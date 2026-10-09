package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{Component, FocusSnapshot, RenderContext, Select}
import event.KeyEvent.SpecialKey
import event.SpecialKeyCode
import geometry.Rect
import render.{LayoutManager, Renderer}
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

/**
 * [[SelectDemoPanel]] at the demo's content size: three selects, the
 * country list scrolling inside its popup, the theme list opening upward.
 */
object SelectDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  private def focused(c: Component): RenderContext = RenderContext(FocusSnapshot(Some(c.id)))

  private def selects(root: Component): Vector[Select] =
    LayoutManager.default.resolve(root, Rect(0, 0, W, H)).order.collect { case s: Select => s }

  def spec: Spec[TestEnvironment & Scope, Any] = suite("SelectDemoPanel")(
    test("three selects, all in the focus cycle, in reading order") {
      for panel <- SelectDemoPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        val found = selects(panel.root)
        assertTrue(found.size == 3, layout.focusOrder.ids == found.map(_.id))
    },
    test("renders labels, the chosen model, the country placeholder and the chosen theme") {
      for panel <- SelectDemoPanel.make
      yield
        val text = rows(renderToBuffer(W, H)(panel.root))
        assertTrue(
          text.exists(r => r.contains("Model") && r.contains("Sonnet")),
          text.exists(r => r.contains("Country") && r.contains("Choose a country")),
          text.exists(r => r.contains("Theme") && r.contains("System"))
        )
    },
    test("the theme list opens upward from the bottom edge") {
      for
        panel <- SelectDemoPanel.make
        theme = selects(panel.root).last
        _ = theme.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), focused(theme))
        h      <- FrameHarness.make(W, H)
        layout <- Renderer.default.renderFull(panel.root, focused(theme)).provideLayer(h.frameLayer)
      yield
        val text = rows(h.drawnBuffer)
        assertTrue(
          layout.popups.map(_._2) == Vector(Rect(14, 12, 32, 6)),
          text(13).contains("System"),
          text(16).contains("High contrast")
        )
    },
    test("the country list scrolls to its last option") {
      for
        panel <- SelectDemoPanel.make
        country = selects(panel.root)(1)
        _ = Seq(SpecialKeyCode.Enter, SpecialKeyCode.End)
              .foreach(code => country.handleEvent(SpecialKey(code, Set.empty), focused(country)))
        h      <- FrameHarness.make(W, H)
        layout <- Renderer.default.renderFull(panel.root, focused(country)).provideLayer(h.frameLayer)
      yield
        val text = rows(h.drawnBuffer)
        assertTrue(
          layout.popups.map(_._2) == Vector(Rect(14, 11, 32, 8)),
          text(17).contains("Uruguay"),
          text(12).contains("Russia (Россия)")
        )
    }
  )
