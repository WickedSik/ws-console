package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Foreground, ScreenBuffer}

import zio.Scope
import zio.test.*

import testkit.RenderHarness.{charAt, renderToBuffer}
import testkit.GridAssertions.{assertCell, assertChar, assertGrid, assertStyle}

/**
 * Phase F demo backfill (AC7): exercise real demo panels through the harness,
 * keeping the demo surface in sync with the library's test capability.
 *
 *   - [[WelcomePanel]] — a static Layer-4 `component.Panel` driven by the pure
 *     `renderToBuffer` and asserted with value-based `assertCell`/`assertStyle`.
 *   - [[ProgressBarPanel]] — an animated panel's pure `drawBar` seam asserted
 *     as an ASCII-block grid.
 */
object DemoPanelHarnessSpec extends ZIOSpecDefault:

  private val cyan     = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val boldCyan = CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  /** First (x, y) where `needle` begins on a single row of the buffer. */
  private def findRowText(buf: ScreenBuffer, needle: String): Option[(Int, Int)] =
    (0 until buf.height).view.flatMap { y =>
      (0 to buf.width - needle.length)
        .find(x => needle.indices.forall(i => buf.charAt(x + i, y).contains(needle.charAt(i))))
        .map(x => (x, y))
    }.headOption

  def spec: Spec[TestEnvironment & Scope, Any] = suite("demo panels through the harness")(

    // ===== WelcomePanel: a static Component panel via renderToBuffer =====

    test("WelcomePanel draws an opaque double-line border box") {
      val w   = WelcomePanel.bounds.width
      val h   = WelcomePanel.bounds.height
      val buf = renderToBuffer(w, h)(WelcomePanel.tree)
      assertCell(buf, 0,     0,     Cell(BoxStyle.Double.topLeft,     cyan)) &&
      assertCell(buf, w - 1, 0,     Cell(BoxStyle.Double.topRight,    cyan)) &&
      assertCell(buf, 0,     h - 1, Cell(BoxStyle.Double.bottomLeft,  cyan)) &&
      assertCell(buf, w - 1, h - 1, Cell(BoxStyle.Double.bottomRight, cyan)) &&
      // The top edge between the corners is the double-line horizontal glyph.
      assertChar(buf, 1, 0, BoxStyle.Double.horizontal) &&
      // Opacity: an uncovered inner cell holds the panel's own styled space.
      assertCell(buf, 1, 1, Cell(' ', cyan))
    },

    test("WelcomePanel centers the ws-console title in bold cyan") {
      val w     = WelcomePanel.bounds.width
      val buf   = renderToBuffer(w, WelcomePanel.bounds.height)(WelcomePanel.tree)
      val title = "ws-console"
      findRowText(buf, title) match
        case None => assertTrue(false) // title was not rendered
        case Some((x, y)) =>
          val expectedX = (w - title.length) / 2
          assertTrue(x == expectedX) &&               // horizontally centered
          assertStyle(buf, x, y, boldCyan) &&         // value-based style (KI-001-safe)
          assertCell(buf, x, y, Cell('w', boldCyan))
    },

    // ===== ProgressBarPanel: the pure seam as an ASCII-block =====

    test("ProgressBarPanel.drawBar renders a full bar at 100% as a grid block") {
      val buf = ScreenBuffer.of(69, 8)
      ProgressBarPanel.drawBar(Canvas(buf), 100)
      val blank    = "." * 69
      val bar      = ".." + "[" + "█" * 60 + "]" + "." + "100%"
      val expected = (Vector.fill(7)(blank) :+ bar).mkString("\n")
      assertGrid(buf, expected)
    }
  )
