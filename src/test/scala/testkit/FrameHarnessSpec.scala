package io.github.wickedsik.wsconsole
package testkit

import buffer.Canvas
import component.{Component, RenderContext, Text}
import geometry.Rect

import zio.*
import zio.test.*

import RenderHarness.glyphGrid

object FrameHarnessSpec extends ZIOSpecDefault:

  /** Draws each char of `s` along the first row of its area. */
  final private class Row(s: String) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      s.zipWithIndex.foreach { case (ch, i) =>
        if i < area.width then canvas.putChar(area.x + i, area.y, ch)
      }

  def spec: Spec[TestEnvironment & Scope, Any] = suite("FrameHarness")(
    test("renderFrame returns the emitted ANSI and the drawn buffer") {
      for
        result <- FrameHarness.renderFrame(Text("hi"), 6, 1)
        (ansi, buffer) = result
      yield assertTrue(
        // The drawn frame lives in `previous` after the swap; glyphs read back.
        buffer.glyphGrid == Vector("hi...."),
        // The wire stream addressed and painted the two glyphs.
        ansi.contains("[1;1H"),
        ansi.contains("h"),
        ansi.contains("i")
      )
    },
    test("a push then a change emits only the delta at the wire level") {
      for
        h      <- FrameHarness.make(4, 1)
        _      <- h.run(Row("AAAA"))
        frame1 <- h.captured
        grid1 = h.drawnBuffer.glyphGrid
        _      <- h.clearCaptured
        _      <- h.run(Row("AAAB"))
        frame2 <- h.captured
        grid2 = h.drawnBuffer.glyphGrid
      yield assertTrue(
        // Frame 1: fresh paint against an empty baseline — all four columns.
        grid1 == Vector("AAAA"),
        frame1.contains("[1;1H"),
        frame1.contains("[1;4H"),
        // Frame 2: steady-state diff — only column 3 (A → B) reaches the wire.
        grid2 == Vector("AAAB"),
        frame2.contains("[1;4H"),
        frame2.contains("B"),
        !frame2.contains("A"),
        !frame2.contains("[1;1H")
      )
    }
  )
