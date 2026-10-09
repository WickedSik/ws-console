package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{FocusSnapshot, RenderContext, Slider, TextInput}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{EventResult, SpecialKeyCode}
import geometry.Rect
import render.LayoutManager
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.io.IOException

/**
 * [[SliderDemoPanel]] at the demo's content size: four sliders with their
 * default marked, each beside a numeric field bound both ways.
 */
object SliderDemoPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  private def focused(id: component.ComponentId): RenderContext = RenderContext(FocusSnapshot(Some(id)))

  /** Run a `Perform` effect against a real `Frame`. */
  private def perform(res: EventResult): IO[IOException, Unit] =
    res match
      case EventResult.Perform(effect) => FrameHarness.make(1, 1).flatMap(h => effect.provideLayer(h.frameLayer))
      case _                           => ZIO.unit

  def spec: Spec[TestEnvironment & Scope, Any] = suite("SliderDemoPanel")(
    test("four sliders, each followed by its field in the focus cycle") {
      for panel <- SliderDemoPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        val sliders = layout.order.collect { case s: Slider => s }
        val fields = layout.order.collect { case t: TextInput => t }
        val expected = sliders.zip(fields).flatMap((s, f) => Seq(s.id, f.id))
        assertTrue(
          sliders.size == 4,
          fields.size == 4,
          sliders.forall(_.default.isDefined),
          layout.focusOrder.ids == expected
        )
    },
    test("renders labels, thumbs, default labels and Dutch-formatted fields") {
      for panel <- SliderDemoPanel.make
      yield
        val text = rows(renderToBuffer(W, H)(panel.root))
        val temperature = text.indexWhere(_.contains("Temperature (?)"))
        assertTrue(
          text(temperature).contains("●"),
          text(temperature).contains("│ 0,8  │"),
          text(temperature + 1).contains("Default"),
          text.exists(r => r.contains("Top K (?)") && r.contains("│ 30   │")),
          text.forall(r => !r.contains("━"))
        )
    },
    test("moving a slider writes its formatted value into the field") {
      for
        panel  <- SliderDemoPanel.make
        layout  = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        slider  = layout.order.collect { case s: Slider => s }.head
        field   = layout.order.collect { case t: TextInput => t }.head
        _      <- perform(slider.handleEvent(SpecialKey(SpecialKeyCode.Right, Set.empty), focused(slider.id)))
      yield assertTrue(slider.value == 0.9, field.value == "0,9")
    },
    test("typing a number in range moves the slider; out of range leaves it") {
      for
        panel  <- SliderDemoPanel.make
        layout  = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        slider  = layout.order.collect { case s: Slider => s }.head
        field   = layout.order.collect { case t: TextInput => t }.head
        ctx     = focused(field.id)
        // The field holds "0,8"; Backspace then '5' makes "0,5".
        _      <- perform(field.handleEvent(SpecialKey(SpecialKeyCode.Backspace, Set.empty), ctx))
        _      <- perform(field.handleEvent(CharKey('5', Set.empty), ctx))
        inRange = slider.value
        // Appending '9' makes "0,59", which snaps to 0,6.
        _      <- perform(field.handleEvent(CharKey('9', Set.empty), ctx))
        snapped = slider.value
        // Home then '2' makes "20,59", outside [0, 2]: the slider stays put.
        _      <- perform(field.handleEvent(SpecialKey(SpecialKeyCode.Home, Set.empty), ctx))
        _      <- perform(field.handleEvent(CharKey('2', Set.empty), ctx))
      yield assertTrue(inRange == 0.5, snapped == 0.6, field.value == "20,59", slider.value == 0.6)
    }
  )
