package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{BoxStyle, Cell, CellStyle, Canvas, Foreground, ScreenBuffer}
import geometry.Rect
import zio.Scope
import zio.test.*

object PanelSpec extends ZIOSpecDefault:

  private val borderCellStyle = CellStyle(fg = Foreground.Named(FgColor.Cyan))
  private val redStyle        = CellStyle(fg = Foreground.Named(FgColor.Red))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Panel")(

    test("draws border on the outer rect") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Spacer, border = BoxStyle.Single, style = borderCellStyle)
        .render(Rect(0, 0, 4, 3), canvas)
      assertTrue(
        buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
        buf.get(3, 0).map(_.char).contains(BoxStyle.Single.topRight),
        buf.get(0, 2).map(_.char).contains(BoxStyle.Single.bottomLeft),
        buf.get(3, 2).map(_.char).contains(BoxStyle.Single.bottomRight)
      )
    },

    test("places title on the top edge starting at x+2") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Spacer, title = Some("hi"), style = borderCellStyle)
        .render(Rect(0, 0, 12, 3), canvas)
      assertTrue(
        buf.get(2, 0).map(_.char).contains('h'),
        buf.get(3, 0).map(_.char).contains('i')
      )
    },

    test("renders child into area.inner(1)") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Text("X", redStyle))
        .render(Rect(2, 1, 10, 3), canvas)
      // child area is Rect(3, 2, 8, 1); Text("X") at left-align lands at (3, 2)
      assertTrue(buf.get(3, 2).contains(Cell('X', redStyle)))
    },

    test("undersized panel is a no-op") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Text("X", redStyle))
        .render(Rect(0, 0, 1, 1), canvas)
      // Border requires at least 2x2 — nothing drawn
      assertTrue(buf.get(0, 0).contains(Cell.Empty))
    }
  )
