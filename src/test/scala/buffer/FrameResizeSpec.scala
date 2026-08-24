package io.github.wickedsik.wsconsole
package buffer

import terminal.Terminal
import testkit.CaptureTerminal

import zio.*
import zio.test.*

import java.io.IOException

object FrameResizeSpec extends ZIOSpecDefault:

  /** Build a Frame from a capture terminal via the live ZLayer. */
  private def withFrame(
    body: (Frame, CaptureTerminal) => ZIO[Any, IOException, TestResult]
  ): ZIO[Any, IOException, TestResult] =
    for
      terminal <- CaptureTerminal.make()
      result <- ZIO
        .serviceWithZIO[Frame](frame => body(frame, terminal))
        .provide(ZLayer.succeed[Terminal](terminal), Frame.live)
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
      withFrame { (frame, term) =>
        for
          _ <- term.clearCaptured // ignore any setup writes
          _ <- frame.resize(40, 12)
          writes <- term.capturedWrites
        yield
          val allWrites = writes.mkString
          assertTrue(
            // Clear-screen (CSI 2 J) and cursor-home (CSI 1;1 H) both fire
            allWrites.contains("[2J"),
            allWrites.contains("[1;1H")
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
          c = frame.canvas
        yield assertTrue(c.width == 10, c.height == 4)
      }
    },
    test("resize then render produces ops sized to the new buffer") {
      withFrame { (frame, term) =>
        for
          _ <- frame.resize(5, 2)
          _ = frame.canvas.putChar(0, 0, 'X')
          _ <- term.clearCaptured
          _ <- frame.render
          writes <- term.capturedWrites
        yield
          val text = writes.mkString
          // The diff includes our 'X' write
          assertTrue(text.contains("X"))
      }
    }
  )
