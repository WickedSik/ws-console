package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Cell, CellStyle, Foreground, ScreenBuffer}
import testkit.GridAssertions.{assertCell, assertChar, assertStyle}
import testkit.RenderHarness.{charAt, renderToBuffer}

import zio.Scope
import zio.test.*

/** [[WelcomePanel]], a static Layer-4 `component.Panel`, through the pure `renderToBuffer`. */
object WelcomePanelSpec extends ZIOSpecDefault:

  private val cyan = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val boldCyan = CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  /** First (x, y) where `needle` begins on a single row of the buffer. */
  private def findRowText(buf: ScreenBuffer, needle: String): Option[(Int, Int)] =
    (0 until buf.height).view.flatMap { y =>
      (0 to buf.width - needle.length)
        .find(x => needle.indices.forall(i => buf.charAt(x + i, y).contains(needle.charAt(i))))
        .map(x => (x, y))
    }.headOption

  def spec: Spec[TestEnvironment & Scope, Any] = suite("WelcomePanel")(
    test("draws an opaque double-line border box") {
      val w = 80
      val h = 21
      val buf = renderToBuffer(w, h)(WelcomePanel.tree)
      assertCell(buf, 0, 0, Cell(BoxStyle.Double.topLeft, cyan)) &&
      assertCell(buf, w - 1, 0, Cell(BoxStyle.Double.topRight, cyan)) &&
      assertCell(buf, 0, h - 1, Cell(BoxStyle.Double.bottomLeft, cyan)) &&
      assertCell(buf, w - 1, h - 1, Cell(BoxStyle.Double.bottomRight, cyan)) &&
      // The top edge between the corners is the double-line top-center glyph.
      assertChar(buf, 1, 0, BoxStyle.Double.topCenter) &&
      // Opacity: an uncovered inner cell holds the panel's own styled space.
      assertCell(buf, 1, 1, Cell(' ', cyan))
    },
    test("centers the ws-console title in bold cyan") {
      val w = 80
      val buf = renderToBuffer(w, 21)(WelcomePanel.tree)
      val title = "ws-console"
      findRowText(buf, title) match
        case None => assertTrue(false) // title was not rendered
        case Some((x, y)) =>
          val expectedX = (w - title.length) / 2
          assertTrue(x == expectedX) && // horizontally centered
          assertStyle(buf, x, y, boldCyan) && // value-based style (KI-001-safe)
          assertCell(buf, x, y, Cell('w', boldCyan))
    }
  )
