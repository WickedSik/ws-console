package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{Component, ScrollView}
import event.EventResult
import geometry.Rect
import render.{FocusPolicy, LayoutManager, LayoutResult, RenderLoop}
import terminal.Terminal
import testkit.FrameHarness

import zio.*
import zio.test.*

/**
 * [[ScrollViewDemoPanel]] driven through the real [[render.RenderLoop]]
 * at the demo's content size: Tab walks every control in the form and
 * the view follows. That the reveal lands on the very first frame after
 * a focus change is pinned down in `RenderLoopSpec`.
 */
object ScrollViewDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21
  private val area = Rect(0, 0, W, H)
  private val SettleTimeout: Duration = 10.seconds

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
        // Taken before the loop starts, at offset 0. While the loop runs the
        // test only reads `view.offset`: calling `childLayouts` from this fiber
        // would race the loop's own layout, which writes the clamped offset.
        initial  = layout(root)
        view     = initial.order.collectFirst { case v: ScrollView => v }.get
        viewport = initial.rects(view.id)
        controls = initial.focusOrder.ids
        harness <- FrameHarness.make(W, H)
        loop    <- RenderLoop.make(focusPolicy = FocusPolicy.DropOnRemoval)
        fiber <- loop
                   .start(root, (_, _: EventResult) => ZIO.succeed(true))
                   .provide(ZLayer.succeed[Terminal](harness.terminal), harness.frameLayer)
                   .fork
        // Wait for the first frame to install the focus order.
        _ <- (ZIO.sleep(5.millis) *> loop.focusManager.focus(controls.head)).repeatUntil(identity)
        visits <- ZIO.foreach(controls.indices.toVector) { i =>
                    val atZero = initial.rects(controls(i))
                    ZIO.when(i > 0)(loop.focusManager.focusNext()) *>
                      (ZIO.sleep(5.millis) *> ZIO.succeed(view.offset))
                        .repeatUntil(off => inside(atZero.copy(y = atZero.y - off), viewport))
                        .timeout(SettleTimeout)
                        .map(_.isDefined)
                  }
        bottom <- (ZIO.sleep(5.millis) *> ZIO.succeed(rows(harness.drawnBuffer).takeRight(5).mkString("\n")))
                    .repeatUntil(_.contains("Reset"))
                    .timeout(SettleTimeout)
        _ <- loop.stop
        _ <- fiber.join
      yield assertTrue(
        controls.size == 17,
        visits.forall(identity),
        // The last control, Reset, is drawn in the bottom rows once focused.
        view.offset > 0,
        bottom.isDefined
      )
    } @@ TestAspect.withLiveClock
  ) @@ TestAspect.timeout(60.seconds)
