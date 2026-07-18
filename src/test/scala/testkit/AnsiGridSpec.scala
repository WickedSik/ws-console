package io.github.wickedsik.wsconsole
package testkit

import ansi.FgColor
import buffer.{Attribute, Background, Canvas, Cell, CellStyle, Foreground}
import component.{Component, RenderContext, Text}
import geometry.Rect

import zio.*
import zio.test.*

import GridAssertions.assertGrid

object AnsiGridSpec extends ZIOSpecDefault:

  /** Component that paints a fixed map of area-relative styled cells. */
  private final class Cells(cells: Map[(Int, Int), Cell]) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      cells.foreach { case ((x, y), cell) =>
        canvas.putChar(area.x + x, area.y + y, cell.char, cell.style)
      }

  def spec: Spec[TestEnvironment & Scope, Any] = suite("AnsiGrid.decode")(

    test("round-trips a flushed frame's non-empty cells with structural style") {
      val red = CellStyle(fg = Foreground.Named(FgColor.Red))
      // Two attributes: their wire order is non-deterministic (KI-001), but a
      // structural decode is immune to that.
      val boldUnd = CellStyle(
        fg         = Foreground.Named(FgColor.Green),
        attributes = Set(Attribute.Bold, Attribute.Underline)
      )
      val cells = Map((0, 0) -> Cell('A', red), (2, 0) -> Cell('B', boldUnd))
      for
        result <- FrameHarness.renderFrame(new Cells(cells), 4, 1)
        (ansi, _) = result
      yield
        val decoded = AnsiGrid.decode(ansi)
        assertTrue(
          // Exact structural match — glyph AND style — never an ANSI snapshot.
          decoded == cells,
          decoded((2, 0)).style.attributes == Set(Attribute.Bold, Attribute.Underline)
        )
    },

    test("recovers 256-colour and RGB via the multi-parameter SGR grammar") {
      val indexed = CellStyle(fg = Foreground.Indexed(196))
      val rgb     = CellStyle(bg = Background.Rgb(255, 128, 0))
      val cells   = Map((0, 0) -> Cell('X', indexed), (1, 0) -> Cell('Y', rgb))
      for
        result <- FrameHarness.renderFrame(new Cells(cells), 3, 1)
        (ansi, _) = result
      yield assertTrue(AnsiGrid.decode(ansi) == cells)
    },

    test("rounds a flushed frame back into a grid for assertGrid") {
      for
        result <- FrameHarness.renderFrame(Text("hi"), 6, 1)
        (ansi, _) = result
      yield assertGrid(AnsiGrid.decodeToBuffer(ansi, 6, 1), "hi....")
    }
  )
