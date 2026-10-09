package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.RadioGroup
import geometry.Rect
import render.LayoutManager
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

/**
 * [[RadioGroupDemoPanel]] at the demo's content size: two vertical and two
 * horizontal groups, each one focus stop, and the mirror summary.
 */
object RadioGroupDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RadioGroupDemoPanel")(
    test("four groups, two of them horizontal, each one focus stop") {
      for panel <- RadioGroupDemoPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        val groups = layout.order.collect { case g: RadioGroup => g }
        assertTrue(
          groups.size == 4,
          groups.count(_.layout.isInstanceOf[RadioGroup.Layout.Horizontal]) == 2,
          layout.focusOrder.ids.toSet == groups.map(_.id).toSet
        )
    },
    test("horizontal groups render on one row each, and the mirror summarises every group") {
      for panel <- RadioGroupDemoPanel.make
      yield
        val text = rows(renderToBuffer(W, H)(panel.root))
        assertTrue(
          text.exists(r => r.contains("Rating") && r.contains("● Safe  ○ Mature")),
          text.exists(r => r.contains("Language") && r.contains("● English   ○ 日本語   ○ 中文")),
          text.exists(_.contains("Rating: Safe")),
          text.exists(_.contains("←/→ a horizontal one."))
        )
    }
  )
