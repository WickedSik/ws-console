package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{ScrollView, TextArea}
import geometry.Rect
import render.LayoutManager
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

/**
 * [[TextAreaDemoPanel]] at the demo's content size: three fields in a
 * scroll view, the disabled one out of the Tab cycle, and a live caret
 * line under each.
 */
object TextAreaDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextAreaDemoPanel")(
    test("three fields, two of them in the focus cycle") {
      for panel <- TextAreaDemoPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        val fields = layout.order.collect { case t: TextArea => t }
        assertTrue(
          fields.size == 3,
          fields.count(_.enabled) == 2,
          layout.focusOrder.ids.toSet == fields.filter(_.enabled).map(_.id).toSet,
          layout.order.exists(_.isInstanceOf[ScrollView])
        )
    },
    test("renders the placeholder, the grip, and a caret line under each visible field") {
      for panel <- TextAreaDemoPanel.make
      yield
        val text = rows(renderToBuffer(W, H)(panel.root)).mkString("\n")
        assertTrue(
          text.contains("Nothing here yet."),
          text.contains("rows 2 · line 1 · col 1"),
          text.contains("rows 4 · line "),
          text.contains(TextArea.GripGlyph.toString)
        )
    }
  )
