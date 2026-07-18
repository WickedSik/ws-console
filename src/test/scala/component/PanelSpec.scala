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
  private val ctx             = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Panel")(

    test("draws border on the outer rect") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Spacer, border = BoxStyle.Single, style = borderCellStyle)
        .render(Rect(0, 0, 4, 3), canvas, ctx)
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
        .render(Rect(0, 0, 12, 3), canvas, ctx)
      assertTrue(
        buf.get(2, 0).map(_.char).contains('h'),
        buf.get(3, 0).map(_.char).contains('i')
      )
    },

    test("renders child into area.inner(1)") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Text("X", redStyle))
        .render(Rect(2, 1, 10, 3), canvas, ctx)
      // child area is Rect(3, 2, 8, 1); Text("X") at left-align lands at (3, 2)
      assertTrue(buf.get(3, 2).contains(Cell('X', redStyle)))
    },

    test("undersized panel is a no-op") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Text("X", redStyle))
        .render(Rect(0, 0, 1, 1), canvas, ctx)
      // Border requires at least 2x2 — nothing drawn
      assertTrue(buf.get(0, 0).contains(Cell.Empty))
    },

    test("paints opaque interior, hiding cells written earlier in the frame") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      // Simulate a lower-z panel having written into the cells the
      // upper panel is about to claim.
      canvas.putChar(2, 1, 'X', redStyle)
      canvas.putChar(3, 1, 'Y', redStyle)
      Panel(child = Spacer, style = borderCellStyle)
        .render(Rect(1, 0, 4, 3), canvas, ctx)
      // Interior cells (2,1) and (3,1) must now carry the panel's
      // background fill, not the underlying X / Y.
      assertTrue(
        buf.get(2, 1).contains(Cell(' ', borderCellStyle)),
        buf.get(3, 1).contains(Cell(' ', borderCellStyle))
      )
    },

    test("opaque fill does not erase the child's writes") {
      val buf    = ScreenBuffer.of(20, 5)
      val canvas = Canvas(buf)
      Panel(child = Text("X", redStyle), style = borderCellStyle)
        .render(Rect(2, 1, 10, 3), canvas, ctx)
      // Child writes at (3, 2) — after fillRect + drawBox + child.render,
      // the cell must reflect the child, not the background.
      assertTrue(buf.get(3, 2).contains(Cell('X', redStyle)))
    }
  )
