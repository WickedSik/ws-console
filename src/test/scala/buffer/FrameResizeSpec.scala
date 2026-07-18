package io.github.wickedsik.wsconsole
package buffer

import ansi.AnsiBuilder
import terminal.{ColorSupport, RawInput, Terminal, TerminalCapabilities, TerminalSize}

import zio.*
import zio.test.*

import java.io.IOException

object FrameResizeSpec extends ZIOSpecDefault:

  /**
   * Recording terminal that captures `writeBuilder` calls so resize tests
   * can assert the clear-screen ANSI is emitted.
   */
  private final class RecordingTerminal(writeLog: Ref[Vector[String]]) extends Terminal:
    override def enterRawMode:                                 IO[IOException, Unit] = ZIO.unit
    override def exitRawMode:                                  IO[IOException, Unit] = ZIO.unit
    override def enterAlternateBuffer:                         IO[IOException, Unit] = ZIO.unit
    override def exitAlternateBuffer:                          IO[IOException, Unit] = ZIO.unit
    override def disableLineWrap:                              IO[IOException, Unit] = ZIO.unit
    override def enableLineWrap:                               IO[IOException, Unit] = ZIO.unit
    override def moveCursor(row: Int, col: Int):               IO[IOException, Unit] = ZIO.unit
    override def hideCursor:                                   IO[IOException, Unit] = ZIO.unit
    override def showCursor:                                   IO[IOException, Unit] = ZIO.unit
    override def saveCursor:                                   IO[IOException, Unit] = ZIO.unit
    override def restoreCursor:                                IO[IOException, Unit] = ZIO.unit
    override def clearScreen:                                  IO[IOException, Unit] = ZIO.unit
    override def clearLine:                                    IO[IOException, Unit] = ZIO.unit
    override def setScrollRegion(top: Int, bottom: Int):       IO[IOException, Unit] = ZIO.unit
    override def resetScrollRegion:                            IO[IOException, Unit] = ZIO.unit
    override def write(text: String):                          IO[IOException, Unit] = ZIO.unit
    override def writeBuilder(builder: AnsiBuilder):           IO[IOException, Unit] =
      writeLog.update(_ :+ builder.build)
    override def flush:                                        IO[IOException, Unit] = ZIO.unit
    override def readRaw(timeout: Duration):                   IO[IOException, RawInput] =
      ZIO.succeed(RawInput.Timeout)
    override def size:                                         IO[IOException, TerminalSize] =
      ZIO.succeed(TerminalSize(24, 80))
    override def capabilities:                                 IO[IOException, TerminalCapabilities] =
      ZIO.succeed(TerminalCapabilities(ColorSupport.TrueColor, true, true, true, true, TerminalSize(24, 80)))

  /** Build a Frame from a recording terminal via the live ZLayer. */
  private def withFrame(
    body: (Frame, Ref[Vector[String]]) => ZIO[Any, IOException, TestResult]
  ): ZIO[Any, IOException, TestResult] =
    for
      log <- Ref.make(Vector.empty[String])
      terminal: Terminal = new RecordingTerminal(log)
      result <- ZIO
                  .serviceWithZIO[Frame](frame => body(frame, log))
                  .provide(ZLayer.succeed(terminal), Frame.live)
    yield result

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Frame.resize")(

    test("resize updates width and height") {
      withFrame { (frame, _) =>
        for
          _ <- frame.resize(40, 12)
        yield assertTrue(frame.width == 40, frame.height == 12)
      }
    },

    test("resize emits a clear-screen + cursor-home ANSI sequence") {
      withFrame { (frame, log) =>
        for
          _      <- log.set(Vector.empty) // ignore any setup writes
          _      <- frame.resize(40, 12)
          writes <- log.get
        yield
          val allWrites = writes.mkString
          assertTrue(
            // Clear-screen (CSI 2 J) and cursor-home (CSI 1;1 H) both fire
            allWrites.contains("[2J"),
            allWrites.contains("[1;1H")
          )
      }
    },

    test("resize clamps zero / negative dimensions to 1") {
      withFrame { (frame, _) =>
        for
          _ <- frame.resize(0, -5)
        yield assertTrue(frame.width == 1, frame.height == 1)
      }
    },

    test("canvas after resize draws at the new dimensions") {
      withFrame { (frame, _) =>
        for
          _ <- frame.resize(10, 4)
          c  = frame.canvas
        yield assertTrue(c.width == 10, c.height == 4)
      }
    },

    test("resize then render produces ops sized to the new buffer") {
      withFrame { (frame, log) =>
        for
          _      <- frame.resize(5, 2)
          _       = frame.canvas.putChar(0, 0, 'X')
          _      <- log.set(Vector.empty)
          _      <- frame.render
          writes <- log.get
        yield
          val text = writes.mkString
          // The diff includes our 'X' write
          assertTrue(text.contains("X"))
      }
    }
  )
