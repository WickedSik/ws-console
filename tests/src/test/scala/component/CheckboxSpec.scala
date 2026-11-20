package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, Keymap, SpecialKeyCode}
import geometry.Rect
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

object CheckboxSpec extends ZIOSpecDefault:

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private def focusCtx(w: Checkbox): RenderContext =
    RenderContext(FocusSnapshot(Some(w.id)))

  private val unfocusedCtx: RenderContext = RenderContext.empty

  private def keyPress(w: Checkbox, event: Event): EventResult =
    w.handleEvent(event, focusCtx(w))

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_)                                                 => true
      case EventResult.Consumed | EventResult.Ignored | EventResult.RequestRedraw => false

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
          w <- Checkbox.make("Enable it", marks = BooleanMarks("[x]", "[ ]"), style = baseStyle)
        yield
          val buf = renderToBuffer(20, 1)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('['),
            buf.get(1, 0).map(_.char).contains(' '),
            buf.get(2, 0).map(_.char).contains(']')
          )
      },
      test("marks of unequal width keep the label in place when toggled") {
        val marks = BooleanMarks("✔", "[ ]")
        for
          on  <- Checkbox.make("Go", checked = true, marks = marks, style = baseStyle)
          off <- Checkbox.make("Go", checked = false, marks = marks, style = baseStyle)
        yield
          val onBuf = renderToBuffer(20, 1)(on, ctx = unfocusedCtx)
          val offBuf = renderToBuffer(20, 1)(off, ctx = unfocusedCtx)
          // The wider mark is 3 columns, so the label starts at column 4 in both states.
          assertTrue(
            onBuf.get(0, 0).map(_.char).contains('✔'),
            onBuf.get(4, 0).map(_.char).contains('G'),
            offBuf.get(0, 0).map(_.char).contains('['),
            offBuf.get(4, 0).map(_.char).contains('G')
          )
      },
      test("a wide-grapheme label truncates by columns and stays inside its area") {
        for
          w <- Checkbox.make("日本語", style = baseStyle)
        yield
          // Area 6 wide: mark(1) + space(1) leaves 4 columns, room for two wide graphemes.
          val buf = renderToBuffer(10, 1)(w, Rect(0, 0, 6, 1), unfocusedCtx)
          assertTrue(
            buf.get(2, 0).map(_.text).contains("日"),
            buf.get(4, 0).map(_.text).contains("本"),
            buf.get(6, 0).map(_.text).contains(" ")
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
          val focused = renderToBuffer(10, 1)(w, ctx = focusCtx(w))
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
          val res = keyPress(w, CharKey(' ', Set.empty))
          assertTrue(
            !before,
            w.checked,
            isPerform(res)
          )
      },
      test("Space toggles the state back on the second press") {
        for
          w <- Checkbox.make("A", checked = true)
        yield
          val before = w.checked
          val _ = keyPress(w, CharKey(' ', Set.empty))
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
    ),
    suite("keymap")(
      test("a replaced keymap toggles on its own keys only") {
        for
          w <- Checkbox.make("A", keymap = Keymap(Keymap.plain(SpecialKeyCode.Enter) -> Checkbox.Action.Toggle))
        yield
          val space = keyPress(w, CharKey(' ', Set.empty))
          val enter = keyPress(w, SpecialKey(SpecialKeyCode.Enter, Set.empty))
          assertTrue(space == EventResult.Ignored, isPerform(enter), w.checked)
      }
    )
  )
