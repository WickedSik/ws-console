package io.github.wickedsik.wsconsole
package buffer

import ansi.{AnsiBuilder, FgColor}
import terminal.{ColorSupport, RawInput, Terminal, TerminalCapabilities, TerminalSize}

import zio.*
import zio.test.*

import java.io.IOException

/**
 * Integration tests for `Frame.invalidate` — the panel-swap refresh
 * primitive. Drives a `Frame` against a recording terminal across
 * multiple frames, asserting on the byte stream emitted to the wire.
 *
 * Contract under test: after `frame.invalidate`, the next `render`
 * emits writes covering *every* position of the frame, including
 * cells the new frame leaves empty. This breaks the diff's normal
 * "skip unchanged cells" optimization at panel-swap boundaries, where
 * the terminal display can drift from the buffer model (toolbar bug
 * 2026-05-16; Welcome bleed-through 2026-05-16).
 *
 * These tests would have caught both bugs without manual inspection.
 */
object FrameRefreshSpec extends ZIOSpecDefault:

  private val redA   = Cell('A', CellStyle(fg = Foreground.Named(FgColor.Red)))
  private val greenB = Cell('B', CellStyle(fg = Foreground.Named(FgColor.Green)))

  /** Recording terminal — captures every byte written via writeBuilder. */
  private final class RecordingTerminal(writeLog: Ref[Vector[String]]) extends Terminal:
    override def enterRawMode:                           IO[IOException, Unit] = ZIO.unit
    override def exitRawMode:                            IO[IOException, Unit] = ZIO.unit
    override def enterAlternateBuffer:                   IO[IOException, Unit] = ZIO.unit
    override def exitAlternateBuffer:                    IO[IOException, Unit] = ZIO.unit
    override def disableLineWrap:                        IO[IOException, Unit] = ZIO.unit
    override def enableLineWrap:                         IO[IOException, Unit] = ZIO.unit
    override def moveCursor(row: Int, col: Int):         IO[IOException, Unit] = ZIO.unit
    override def hideCursor:                             IO[IOException, Unit] = ZIO.unit
    override def showCursor:                             IO[IOException, Unit] = ZIO.unit
    override def saveCursor:                             IO[IOException, Unit] = ZIO.unit
    override def restoreCursor:                          IO[IOException, Unit] = ZIO.unit
    override def clearScreen:                            IO[IOException, Unit] = ZIO.unit
    override def clearLine:                              IO[IOException, Unit] = ZIO.unit
    override def setScrollRegion(top: Int, bottom: Int): IO[IOException, Unit] = ZIO.unit
    override def resetScrollRegion:                      IO[IOException, Unit] = ZIO.unit
    override def write(text: String):                    IO[IOException, Unit] = ZIO.unit
    override def writeBuilder(builder: AnsiBuilder):     IO[IOException, Unit] =
      writeLog.update(_ :+ builder.build)
    override def flush:                                  IO[IOException, Unit] = ZIO.unit
    override def readRaw(timeout: Duration):             IO[IOException, RawInput] =
      ZIO.succeed(RawInput.Timeout)
    override def size:                                   IO[IOException, TerminalSize] =
      ZIO.succeed(TerminalSize(5, 10))
    override def capabilities:                           IO[IOException, TerminalCapabilities] =
      ZIO.succeed(TerminalCapabilities(ColorSupport.TrueColor, true, true, true, true, TerminalSize(5, 10)))

  private def withFrame(
    width:  Int,
    height: Int
  )(body: (Frame, Ref[Vector[String]]) => ZIO[Any, IOException, TestResult]): ZIO[Any, IOException, TestResult] =
    for
      log <- Ref.make(Vector.empty[String])
      terminal: Terminal = new RecordingTerminal(log)
      // Resize the live-layer frame to the test dimensions.
      result <- ZIO
                  .serviceWithZIO[Frame] { frame =>
                    frame.resize(width, height) *> body(frame, log)
                  }
                  .provide(ZLayer.succeed(terminal), Frame.live)
    yield result

  /**
   * Extract every cursor-positioning sequence `\e[Y;XH` from the captured
   * byte stream. Returns the set of (x, y) positions (1-indexed in ANSI,
   * normalised to 0-indexed here) that the byte stream addresses.
   */
  private def positionsAddressed(bytes: String): Set[(Int, Int)] =
    val pattern = """\[(\d+);(\d+)H""".r
    pattern.findAllMatchIn(bytes).map { m =>
      val row = m.group(1).toInt - 1
      val col = m.group(2).toInt - 1
      (col, row)
    }.toSet

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Frame.invalidate (panel-swap refresh)")(

    // ===== L2 — contract test for the refresh primitive =====

    test("after invalidate, next render addresses every (x, y) in the frame") {
      withFrame(3, 2) { (frame, log) =>
        for
          // Frame 1: paint one styled cell at (0, 0). Render.
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          // Drop setup writes from the log so we only inspect the refresh frame.
          _ <- log.set(Vector.empty)

          // Frame 2: invalidate, then paint a different single cell at (2, 1).
          _ <- frame.invalidate
          _ <- ZIO.succeed(frame.canvas.putChar(2, 1, greenB.char, greenB.style))
          _ <- frame.render

          writes <- log.get
        yield
          val bytes     = writes.mkString
          val addressed = positionsAddressed(bytes)
          val expected: Set[(Int, Int)] =
            (for { x <- 0 until 3; y <- 0 until 2 } yield (x, y)).toSet
          assertTrue(
            // Every (x, y) must appear in the byte stream.
            expected.forall(addressed.contains),
            addressed.size == expected.size
          )
      }
    },

    test("after invalidate, byte stream contains erasures for prior-frame content") {
      withFrame(3, 2) { (frame, log) =>
        for
          // Frame 1: paint a "Welcome-like" row across (0..2, 0).
          _ <- ZIO.succeed {
                 frame.canvas.putChar(0, 0, redA.char, redA.style)
                 frame.canvas.putChar(1, 0, redA.char, redA.style)
                 frame.canvas.putChar(2, 0, redA.char, redA.style)
               }
          _ <- frame.render

          _ <- log.set(Vector.empty)

          // Frame 2: invalidate, paint nothing on row 0 (it must be erased).
          _ <- frame.invalidate
          _ <- frame.render

          writes <- log.get
        yield
          val bytes     = writes.mkString
          val addressed = positionsAddressed(bytes)
          // Every position must be addressed — including (0,0), (1,0), (2,0)
          // where Frame 1 painted content that must now be erased.
          assertTrue(
            addressed.contains((0, 0)),
            addressed.contains((1, 0)),
            addressed.contains((2, 0)),
            // No flicker: no `\e[2J` clear-screen emitted by the refresh.
            !bytes.contains("[2J")
          )
      }
    },

    test("without invalidate, byte stream skips unchanged cells (steady-state diff intact)") {
      withFrame(3, 2) { (frame, log) =>
        for
          // Frame 1: paint (0, 0).
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          _ <- log.set(Vector.empty)

          // Frame 2: paint the same cell again — no other writes. Diff should
          // emit zero cells (and zero writeBuilders) because `current` after
          // swap-and-redraw matches `previous` exactly.
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          writes <- log.get
        yield
          val bytes     = writes.mkString
          val addressed = positionsAddressed(bytes)
          assertTrue(
            // Steady-state diff: no positions addressed, no writeBuilder
            // call (or an empty builder; either way no cell ops).
            addressed.isEmpty || writes.forall(_.isEmpty)
          )
      }
    },

    test("invalidate alone does not emit any bytes to the terminal") {
      withFrame(3, 2) { (frame, log) =>
        for
          _ <- log.set(Vector.empty)
          _ <- frame.invalidate
          writes <- log.get
        yield assertTrue(writes.isEmpty)
      }
    }
  )
