package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{Component, FocusSnapshot, RenderContext, ScrollView}
import geometry.Rect
import render.{FocusManager, FocusPolicy, LayoutManager, LayoutResult}
import testkit.FrameHarness

import zio.*
import zio.test.*

/**
 * [[ScrollViewDemoPanel]] at the demo's content size: Tab walks every
 * control in the form and the view follows. Each step moves focus, then
 * draws one frame with `renderTree` revealing the focused control, as the
 * render loop does. That the reveal lands on the very first frame after a
 * focus change in the live loop is pinned down in `RenderLoopSpec`.
 */
object ScrollViewDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21
  private val area = Rect(0, 0, W, H)

  private def layout(root: Component): LayoutResult = LayoutManager.default.resolve(root, area)

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  private def inside(r: Rect, viewport: Rect): Boolean =
    r.y >= viewport.y && r.y + r.height <= viewport.y + viewport.height

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ScrollViewDemoPanel")(
    test("Tab walks every control and the view follows") {
      for
        panel <- ScrollViewDemoPanel.make
        root = panel.root
        initial = layout(root)
        view <- ZIO
                  .fromOption(initial.order.collectFirst { case v: ScrollView => v })
                  .orElseFail("the panel has no ScrollView")
        viewport = initial.rects(view.id)
        controls = initial.focusOrder.ids.toVector
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        h0 <- FrameHarness.make(W, H)
        l0 <- h0.renderTree(root, RenderContext.empty)
        _  <- fm.setOrder(l0.focusOrder)
        frames <- ZIO.foreach(controls) { _ =>
                    for
                      _       <- fm.focusNext()
                      focused <- fm.focused
                      h       <- FrameHarness.make(W, H)
                      l       <- h.renderTree(root, RenderContext(FocusSnapshot(focused)), reveal = focused)
                      _       <- fm.setOrder(l.focusOrder)
                    yield (focused, focused.flatMap(l.rects.get), rows(h.drawnBuffer))
                  }
      yield assertTrue(
        controls.size == 17,
        frames.map(_._1) == controls.map(Some(_)),
        // Every focused control is drawn inside the viewport on its own frame.
        frames.forall((_, rect, _) => rect.exists(inside(_, viewport))),
        view.offset > 0,
        // The last control, Reset, is drawn in the bottom rows once focused.
        frames.lastOption.exists(_._3.takeRight(5).mkString("\n").contains("Reset"))
      )
    }
  )
