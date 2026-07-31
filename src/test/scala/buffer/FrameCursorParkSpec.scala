package io.github.wickedsik.wsconsole
package buffer

import ansi.{AnsiBuilder, FgColor}
import terminal.{Terminal, TerminalSize}
import testkit.{AnsiGrid, CaptureTerminal}

import zio.*
import zio.test.*

import java.io.IOException

/**
 * Regression tests for the frame's cursor-park contract.
 *
 * A frame's last cell write leaves the cursor wherever the diff happened
 * to end. When another process shares the TTY and emits a *cursor-relative*
 * erase, everything below that point dies. `sbt` does exactly this — it
 * appends `ED 0` after our writes when the demo runs unforked in sbt's JVM.
 * A Tab that repainted only row 14 cost rows 15–24, taking the demo's
 * toolbar with it (`.claude/tasks/demo-toolbar-disappearance.md`).
 *
 * Contract: every non-empty frame ends with the cursor at the bottom-right
 * corner, which is the position of minimum blast radius. A frame with no
 * ops still emits nothing at all.
 */
object FrameCursorParkSpec extends ZIOSpecDefault:

  private val redA = Cell('A', CellStyle(fg = Foreground.Named(FgColor.Red)))

  private def withFrame(
    width:  Int,
    height: Int
  )(body: (Frame, CaptureTerminal) => ZIO[Any, IOException, TestResult]): ZIO[Any, IOException, TestResult] =
    for
      terminal <- CaptureTerminal.make(size = TerminalSize(5, 10))
      result <- ZIO
                  .serviceWithZIO[Frame] { frame =>
                    frame.resize(width, height) *> body(frame, terminal)
                  }
                  .provide(ZLayer.succeed[Terminal](terminal), Frame.live)
    yield result

  /** The escape a bottom-right park emits for a `width × height` frame. */
  private def parkSuffix(width: Int, height: Int): String =
    AnsiBuilder().moveTo(height, width).build

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Frame cursor park")(

    test("a rendered frame ends with the cursor at the bottom-right corner") {
      withFrame(6, 4) { (frame, term) =>
        for
          _      <- term.clearCaptured
          // Paint a single cell in the top-left — the diff's last op is
          // nowhere near the bottom row, which is precisely the shape that
          // made an injected erase catastrophic.
          _      <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _      <- frame.render
          writes <- term.capturedWrites
        yield
          val bytes = writes.mkString
          assertTrue(
            bytes.nonEmpty,
            bytes.endsWith(parkSuffix(6, 4))
          )
      }
    },

    test("the park does not decode as a cell — the grid is unchanged by it") {
      withFrame(6, 4) { (frame, term) =>
        for
          _      <- term.clearCaptured
          _      <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _      <- frame.render
          writes <- term.capturedWrites
        yield
          val decoded = AnsiGrid.decode(writes.mkString)
          assertTrue(
            // Exactly the one painted cell; the trailing moveTo carries no
            // glyph and must not register at the bottom-right corner.
            decoded.keySet == Set((0, 0)),
            !decoded.contains((5, 3))
          )
      }
    },

    test("a frame with no ops emits no bytes at all, park included") {
      withFrame(6, 4) { (frame, term) =>
        for
          // Frame 1 establishes the baseline.
          _      <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _      <- frame.render
          _      <- term.clearCaptured
          // Frame 2 repaints the identical cell — the diff produces nothing.
          _      <- ZIO.succeed(frame.canvas.putChar(0, 0, redA.char, redA.style))
          _      <- frame.render
          writes <- term.capturedWrites
        yield assertTrue(writes.mkString.isEmpty)
      }
    },

    test("the park tracks the frame's dimensions across a resize") {
      withFrame(6, 4) { (frame, term) =>
        for
          _      <- frame.resize(9, 7)
          _      <- term.clearCaptured
          _      <- ZIO.succeed(frame.canvas.putChar(1, 1, redA.char, redA.style))
          _      <- frame.render
          writes <- term.capturedWrites
        yield assertTrue(writes.mkString.endsWith(parkSuffix(9, 7)))
      }
    }
  )
