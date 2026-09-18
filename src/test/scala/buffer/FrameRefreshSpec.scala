package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import terminal.{Terminal, TerminalSize}
import testkit.{AnsiGrid, CaptureTerminal}

import zio.*
import zio.test.*

import java.io.IOException

/**
 * Integration tests for `Frame.invalidate` — the panel-swap refresh
 * primitive. Drives a `Frame` against a capture terminal across
 * multiple frames, asserting on the byte stream emitted to the wire.
 *
 * Contract: after `frame.invalidate`, the next `render` emits writes
 * covering every position, including cells the new frame leaves empty.
 * This breaks the diff's "skip unchanged cells" optimization at panel-
 * swap boundaries where the terminal display can drift from the buffer
 * model.
 */
object FrameRefreshSpec extends ZIOSpecDefault:

  private val redA = Cell('A', CellStyle(fg = Foreground.Named(FgColor.Red)))
  private val greenB = Cell('B', CellStyle(fg = Foreground.Named(FgColor.Green)))

  private def withFrame(
    width: Int,
    height: Int
  )(body: (Frame, CaptureTerminal) => ZIO[Any, IOException, TestResult]): ZIO[Any, IOException, TestResult] =
    for
      terminal <- CaptureTerminal.make(size = TerminalSize(5, 10))
      // Resize the live-layer frame to the test dimensions.
      result <- ZIO
                  .serviceWithZIO[Frame] { frame =>
                    frame.resize(width, height) *> body(frame, terminal)
                  }
                  .provide(ZLayer.succeed[Terminal](terminal), Frame.live)
    yield result

  /**
   * The set of (x, y) positions the byte stream addressed — now recovered via
   * the shared [[testkit.AnsiGrid]] decoder (which replaced the spec-local
   * `positionsAddressed` regex).
   */
  private def positionsAddressed(bytes: String): Set[(Int, Int)] =
    AnsiGrid.decode(bytes).keySet

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Frame.invalidate (panel-swap refresh)")(
    // ===== L2 — contract test for the refresh primitive =====

    test("after invalidate, next render addresses every (x, y) in the frame") {
      withFrame(3, 2) { (frame, term) =>
        for
          // Frame 1: paint one styled cell at (0, 0). Render.
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          // Drop setup writes from the log so we only inspect the refresh frame.
          _ <- term.clearCaptured

          // Frame 2: invalidate, then paint a different single cell at (2, 1).
          _ <- frame.invalidate
          _ <- ZIO.succeed(frame.canvas.putChar(2, 1, greenB.char, greenB.style))
          _ <- frame.render

          writes <- term.capturedWrites
        yield
          val bytes = writes.mkString
          val addressed = positionsAddressed(bytes)
          val expected: Set[(Int, Int)] =
            (for x <- 0 until 3; y <- 0 until 2 yield (x, y)).toSet
          assertTrue(
            // Every (x, y) must appear in the byte stream.
            expected.forall(addressed.contains),
            addressed.size == expected.size
          )
      }
    },
    test("after invalidate, byte stream contains erasures for prior-frame content") {
      withFrame(3, 2) { (frame, term) =>
        for
          // Frame 1: paint a "Welcome-like" row across (0..2, 0).
          _ <- ZIO.succeed {
                 frame.canvas.putChar(0, 0, redA.char, redA.style)
                 frame.canvas.putChar(1, 0, redA.char, redA.style)
                 frame.canvas.putChar(2, 0, redA.char, redA.style)
               }
          _ <- frame.render

          _ <- term.clearCaptured

          // Frame 2: invalidate, paint nothing on row 0 (it must be erased).
          _ <- frame.invalidate
          _ <- frame.render

          writes <- term.capturedWrites
        yield
          val bytes = writes.mkString
          val addressed = positionsAddressed(bytes)
          // Every position must be addressed — including (0,0), (1,0), (2,0)
          // where Frame 1 painted content that must now be erased.
          assertTrue(
            addressed.contains((0, 0)),
            addressed.contains((1, 0)),
            addressed.contains((2, 0)),
            // No flicker: no `\e[2J` clear-screen emitted by the refresh.
            !bytes.contains("[2J")
          )
      }
    },
    test("without invalidate, byte stream skips unchanged cells (steady-state diff intact)") {
      withFrame(3, 2) { (frame, term) =>
        for
          // Frame 1: paint (0, 0).
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          _ <- term.clearCaptured

          // Frame 2: paint the same cell again — no other writes. Diff should
          // emit zero cells (and zero writeBuilders) because `current` after
          // swap-and-redraw matches `previous` exactly.
          _ <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _ <- frame.render

          writes <- term.capturedWrites
        yield
          val bytes = writes.mkString
          val addressed = positionsAddressed(bytes)
          assertTrue(
            // Steady-state diff: no positions addressed, no writeBuilder
            // call (or an empty builder; either way no cell ops).
            addressed.isEmpty || writes.forall(_.isEmpty)
          )
      }
    },
    test("invalidate alone does not emit any bytes to the terminal") {
      withFrame(3, 2) { (frame, term) =>
        for
          _      <- term.clearCaptured
          _      <- frame.invalidate
          writes <- term.capturedWrites
        yield assertTrue(writes.isEmpty)
      }
    }
  )
