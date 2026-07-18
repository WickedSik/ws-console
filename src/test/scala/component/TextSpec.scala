package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Cell, CellStyle, Canvas, Foreground, ScreenBuffer}
import geometry.Rect
import zio.Scope
import zio.test.*

object TextSpec extends ZIOSpecDefault:

  private val redStyle = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val ctx      = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Text")(

    test("renders a left-aligned line at area origin") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hi", redStyle, Alignment.Left).render(Rect(0, 0, 20, 1), canvas, ctx)
      assertTrue(
        buf.get(0, 0).contains(Cell('h', redStyle)),
        buf.get(1, 0).contains(Cell('i', redStyle))
      )
    },

    test("centers text within the area width") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hi", redStyle, Alignment.Center).render(Rect(0, 0, 10, 1), canvas, ctx)
      // (10 - 2) / 2 = 4 → text starts at x=4
      assertTrue(
        buf.get(4, 0).contains(Cell('h', redStyle)),
        buf.get(5, 0).contains(Cell('i', redStyle)),
        buf.get(3, 0).contains(Cell.Empty)
      )
    },

    test("right-aligns text against the area's right edge") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hi", redStyle, Alignment.Right).render(Rect(0, 0, 10, 1), canvas, ctx)
      // 10 - 2 = 8 → text starts at x=8
      assertTrue(
        buf.get(8, 0).contains(Cell('h', redStyle)),
        buf.get(9, 0).contains(Cell('i', redStyle))
      )
    },

    test("respects a non-zero area origin") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hi", redStyle).render(Rect(5, 2, 10, 1), canvas, ctx)
      assertTrue(
        buf.get(5, 2).contains(Cell('h', redStyle)),
        buf.get(6, 2).contains(Cell('i', redStyle))
      )
    },

    test("truncates content longer than area width") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hello world", redStyle).render(Rect(0, 0, 5, 1), canvas, ctx)
      assertTrue(
        buf.get(0, 0).contains(Cell('h', redStyle)),
        buf.get(4, 0).contains(Cell('o', redStyle)),
        // No write past column 4
        buf.get(5, 0).contains(Cell.Empty)
      )
    },

    test("empty content is a no-op") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("", redStyle).render(Rect(0, 0, 10, 1), canvas, ctx)
      assertTrue(buf.get(0, 0).contains(Cell.Empty))
    },

    test("empty area is a no-op") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Text("hi", redStyle).render(Rect(0, 0, 0, 0), canvas, ctx)
      assertTrue(buf.get(0, 0).contains(Cell.Empty))
    }
  )
