package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{BoxStyle, Cell, CellStyle, Canvas, Foreground, ScreenBuffer}
import geometry.{Insets, Rect}
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
    },

    // ===== Phase 6: padding + Borderless composition =====

    test("padding shifts the child inward from the border") {
      val buf    = ScreenBuffer.of(20, 10)
      val canvas = Canvas(buf)
      // area = Rect(0, 0, 12, 8)
      // area.inner(border.inset=1) = Rect(1, 1, 10, 6)
      // .inner(Insets(top=1, right=2, bottom=1, left=2)) = Rect(3, 2, 6, 4)
      // Text("X") at Alignment.Left lands at (3, 2)
      Panel(
        child   = Text("X", redStyle),
        padding = Insets(top = 1, right = 2, bottom = 1, left = 2)
      ).render(Rect(0, 0, 12, 8), canvas, ctx)
      assertTrue(buf.get(3, 2).contains(Cell('X', redStyle)))
    },

    test("Insets.zero padding is a visual no-op") {
      val bufA = ScreenBuffer.of(20, 5)
      val bufB = ScreenBuffer.of(20, 5)
      Panel(child = Text("X", redStyle))
        .render(Rect(2, 1, 10, 3), Canvas(bufA), ctx)
      Panel(child = Text("X", redStyle), padding = Insets.zero)
        .render(Rect(2, 1, 10, 3), Canvas(bufB), ctx)
      assertTrue(bufA.get(3, 2) == bufB.get(3, 2))
    },

    test("Borderless panel renders the child in the full area") {
      val buf    = ScreenBuffer.of(6, 3)
      val canvas = Canvas(buf)
      // Borderless has inset 0; with zero padding the child area == the
      // panel area. Text("X") at (0, 0) lands at the panel's origin.
      Panel(child = Text("X", redStyle), border = BoxStyle.Borderless)
        .render(Rect(0, 0, 6, 3), canvas, ctx)
      assertTrue(buf.get(0, 0).contains(Cell('X', redStyle)))
    },

    test("Borderless panel is valid at 1x1") {
      val buf    = ScreenBuffer.of(4, 4)
      val canvas = Canvas(buf)
      // A single-cell Borderless panel fills its one cell and draws the
      // child into that same cell — no undersized-for-border guard trips.
      Panel(child = Text("X", redStyle), border = BoxStyle.Borderless)
        .render(Rect(2, 1, 1, 1), canvas, ctx)
      assertTrue(buf.get(2, 1).contains(Cell('X', redStyle)))
    },

    test("Borderless panel writes no border glyphs at the corners") {
      val buf    = ScreenBuffer.of(6, 3)
      val canvas = Canvas(buf)
      // The fill paints spaces styled with borderCellStyle across the
      // whole area — corners should hold spaces, not border glyphs.
      Panel(child = Spacer, border = BoxStyle.Borderless, style = borderCellStyle)
        .render(Rect(0, 0, 6, 3), canvas, ctx)
      assertTrue(
        buf.get(0, 0).contains(Cell(' ', borderCellStyle)),
        buf.get(5, 0).contains(Cell(' ', borderCellStyle)),
        buf.get(0, 2).contains(Cell(' ', borderCellStyle)),
        buf.get(5, 2).contains(Cell(' ', borderCellStyle))
      )
    },

    test("Borderless panel with padding shifts child by padding only") {
      val buf    = ScreenBuffer.of(6, 4)
      val canvas = Canvas(buf)
      // area = Rect(0, 0, 6, 4)
      // .inner(0).inner(Insets.all(1)) = Rect(1, 1, 4, 2)
      // Text("X") at Alignment.Left lands at (1, 1)
      Panel(
        child   = Text("X", redStyle),
        border  = BoxStyle.Borderless,
        padding = Insets.all(1)
      ).render(Rect(0, 0, 6, 4), canvas, ctx)
      assertTrue(buf.get(1, 1).contains(Cell('X', redStyle)))
    }
  )
