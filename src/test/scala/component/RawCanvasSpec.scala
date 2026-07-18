package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Cell, CellStyle, Canvas, Foreground, ScreenBuffer}
import geometry.Rect
import zio.Scope
import zio.test.*

object RawCanvasSpec extends ZIOSpecDefault:

  private val redStyle = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val ctx      = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RawCanvas")(

    test("hands the callback a sub-canvas clipped to the area") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      RawCanvas { c =>
        c.putChar(0, 0, '#', redStyle)        // top-left of area
        c.putChar(2, 1, '*', redStyle)        // somewhere in the middle
      }.render(Rect(5, 2, 10, 3), canvas, ctx)
      assertTrue(
        buf.get(5, 2).contains(Cell('#', redStyle)),
        buf.get(7, 3).contains(Cell('*', redStyle))
      )
    },

    test("writes outside the assigned area are clipped (sub-canvas)") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      RawCanvas { c =>
        c.putChar(15, 0, 'X', redStyle)       // 15 > sub-canvas width of 5 → clipped
      }.render(Rect(2, 1, 5, 2), canvas, ctx)
      // Nothing written outside the area
      val anyDrawn = (0 until 20).exists { x =>
        (0 until 5).exists(y => buf.get(x, y).exists(_ != Cell.Empty))
      }
      assertTrue(!anyDrawn)
    },

    test("empty area is a no-op") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      var called = false
      RawCanvas(_ => called = true).render(Rect(0, 0, 0, 0), canvas, ctx)
      assertTrue(!called)
    }
  )
