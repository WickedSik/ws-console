package io.github.wickedsik.wsconsole
package demo.panels

import component.{ScrollView, WrappedText}
import geometry.Rect
import render.LayoutManager

import zio.Scope
import zio.test.*

/**
 * [[TextFlowPanel]] laid out at the demo's content size: every
 * `WrappedText` gets at least the rows its text wraps to, and the column
 * is taller than the viewport, so the ScrollView has something to scroll.
 */
object TextFlowPanelSpec extends ZIOSpecDefault:

  private val area = Rect(0, 0, 80, 21)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextFlowPanel")(
    test("no WrappedText gets fewer rows than it wraps to") {
      for panel <- TextFlowPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, area)
        val texts = layout.order.collect { case w: WrappedText => w }
        val view = layout.order.collectFirst { case v: ScrollView => v }
        // An HBox gives every sibling the tallest one's height, so a block may
        // get more rows than it wraps to — never fewer.
        val unclipped = texts.forall { w =>
          val r = layout.rects(w.id)
          w.preferredHeight(r.width).exists(_ <= r.height)
        }
        assertTrue(
          texts.size == 8,
          unclipped,
          view.exists(_.focusable),
          view.exists(v => v.child.preferredHeight(area.width - 1).exists(_ > area.height))
        )
    }
  )
