package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.io.IOException

object CheckboxSpec extends ZIOSpecDefault:

  private val noop: Boolean => ZIO[Frame, IOException, Unit] = _ => ZIO.unit

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private def focusCtx(w: Checkbox): RenderContext =
    RenderContext(FocusSnapshot(Some(w.id)))

  private val unfocusedCtx: RenderContext = RenderContext.empty

  private def keyPress(w: Checkbox, event: Event): EventResult =
    w.handleEvent(event, focusCtx(w))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Checkbox")(

    // ===== Mark rendering =====

    suite("mark rendering")(

      test("unchecked default draws the styleguide's empty-box glyph") {
        for
          w <- Checkbox.make("Enable it", style = baseStyle)
        yield
          val buf = renderToBuffer(20, 1)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).map(_.char).contains('☐'))
      },

      test("checked default draws the styleguide's ticked-box glyph") {
        for
          w <- Checkbox.make("Enable it", checked = true, style = baseStyle)
        yield
          val buf = renderToBuffer(20, 1)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).map(_.char).contains('☑'))
      },

      test("consumer-supplied marks override the defaults") {
        for
          w <- Checkbox.make("Enable it", marks = ("[x]", "[ ]"), style = baseStyle)
        yield
          val buf = renderToBuffer(20, 1)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('['),
            buf.get(1, 0).map(_.char).contains(' '),
            buf.get(2, 0).map(_.char).contains(']')
          )
      },

      test("label follows the mark after one space") {
        for
          w <- Checkbox.make("Yes", style = baseStyle)
        yield
          val buf = renderToBuffer(20, 1)(w, ctx = unfocusedCtx)
          // Mark at 0 (1 char), space at 1, label 'Y' at 2, 'e' at 3, 's' at 4
          assertTrue(
            buf.get(2, 0).map(_.char).contains('Y'),
            buf.get(3, 0).map(_.char).contains('e'),
            buf.get(4, 0).map(_.char).contains('s')
          )
      },

      test("a label wider than the remainder is truncated to fit") {
        for
          w <- Checkbox.make("HelloWorld", style = baseStyle)
        yield
          val buf = renderToBuffer(6, 1)(w, ctx = unfocusedCtx)
          // Mark(1) + space(1) leaves 4 label cells → "Hell"
          assertTrue(
            buf.get(2, 0).map(_.char).contains('H'),
            buf.get(3, 0).map(_.char).contains('e'),
            buf.get(4, 0).map(_.char).contains('l'),
            buf.get(5, 0).map(_.char).contains('l')
          )
      },

      test("area too narrow for the mark is a no-op") {
        for
          w <- Checkbox.make("A", style = baseStyle)
        yield
          val buf = renderToBuffer(4, 1)(w, area = Rect(0, 0, 0, 1), ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).map(_.char).contains(' '))
      }
    ),

    // ===== State modulation =====

    suite("state modulation")(

      test("checked mark carries Bold (accent-like) on top of the base style") {
        for
          w <- Checkbox.make("A", checked = true, style = baseStyle)
        yield
          val buf = renderToBuffer(10, 1)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(true))
      },

      test("unchecked mark carries Dim (muted-like) on top of the base style") {
        for
          w <- Checkbox.make("A", checked = false, style = baseStyle)
        yield
          val buf = renderToBuffer(10, 1)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true))
      },

      test("focused adds Bold to the label style") {
        for
          w <- Checkbox.make("A", style = baseStyle)
        yield
          val focused   = renderToBuffer(10, 1)(w, ctx = focusCtx(w))
          val unfocused = renderToBuffer(10, 1)(w, ctx = unfocusedCtx)
          assertTrue(
            focused.get(2, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            unfocused.get(2, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(false)
          )
      },

      test("focused preserves the role's fg (hue is role-owned)") {
        for
          w <- Checkbox.make("A", style = baseStyle)
        yield
          val focused = renderToBuffer(10, 1)(w, ctx = focusCtx(w))
          assertTrue(focused.get(2, 0).map(_.style.fg).contains(baseStyle.fg))
      },

      test("disabled adds Dim to the label style") {
        for
          w <- Checkbox.make("A", style = baseStyle, enabled = false)
        yield
          val buf = renderToBuffer(10, 1)(w, ctx = focusCtx(w))
          assertTrue(buf.get(2, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true))
      }
    ),

    // ===== Focus opt-in =====

    suite("focus")(

      test("enabled checkboxes are focusable") {
        for
          w <- Checkbox.make("A")
        yield assertTrue(w.focusable)
      },

      test("disabled checkboxes are excluded from the focus cycle") {
        for
          w <- Checkbox.make("A", enabled = false)
        yield assertTrue(!w.focusable)
      }
    ),

    // ===== Event handling — toggle =====

    suite("handleEvent")(

      test("Space on a focused enabled checkbox toggles and returns Perform") {
        for
          w <- Checkbox.make("A")
        yield
          val before = w.checked
          val res    = keyPress(w, CharKey(' ', Set.empty))
          assertTrue(
            !before,
            w.checked,
            res match { case EventResult.Perform(_) => true; case _ => false }
          )
      },

      test("Space toggles the state back on the second press") {
        for
          w <- Checkbox.make("A", checked = true)
        yield
          val before = w.checked
          keyPress(w, CharKey(' ', Set.empty))
          assertTrue(before, !w.checked)
      },

      test("Space on an unfocused checkbox returns Ignored") {
        for
          w <- Checkbox.make("A")
        yield
          val res = w.handleEvent(CharKey(' ', Set.empty), unfocusedCtx)
          assertTrue(res == EventResult.Ignored, !w.checked)
      },

      test("Space on a disabled focused checkbox returns Ignored") {
        for
          w <- Checkbox.make("A", enabled = false)
        yield
          val res = keyPress(w, CharKey(' ', Set.empty))
          assertTrue(res == EventResult.Ignored, !w.checked)
      },

      test("Space with a modifier does not toggle") {
        for
          w <- Checkbox.make("A")
        yield
          val res = keyPress(w, CharKey(' ', Set(KeyModifier.Ctrl)))
          assertTrue(res == EventResult.Ignored, !w.checked)
      },

      test("Enter on a focused checkbox returns Ignored (bubbles for form submit)") {
        for
          w <- Checkbox.make("A")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Enter, Set.empty))
          assertTrue(res == EventResult.Ignored, !w.checked)
      },

      test("other characters on a focused checkbox return Ignored") {
        for
          w <- Checkbox.make("A")
        yield
          val res = keyPress(w, CharKey('x', Set.empty))
          assertTrue(res == EventResult.Ignored)
      }
    )
  )
