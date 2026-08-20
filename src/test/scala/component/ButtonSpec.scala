package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Cell, CellStyle, Foreground, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Insets, Rect}
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.io.IOException

object ButtonSpec extends ZIOSpecDefault:

  private val noop: ZIO[Frame, IOException, Unit] = ZIO.unit

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private def focusCtx(btn: Button): RenderContext =
    RenderContext(FocusSnapshot(Some(btn.id)))

  private val unfocusedCtx: RenderContext =
    RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Button")(

    // ===== Rendering — layout & framing =====

    suite("rendering")(

      test("draws Single-border corner glyphs and centres a short label") {
        for
          btn <- Button.make("A", noop, style = baseStyle)
        yield
          val buf = renderToBuffer(6, 3)(btn, ctx = unfocusedCtx)
          // Border at Rect(0,0,6,3): TL=(0,0), TR=(5,0), BL=(0,2), BR=(5,2)
          // Inner Rect(1,1,4,1); Text "A" horizontally centred → x = 1 + (4-1)/2 = 2
          assertTrue(
            buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
            buf.get(5, 0).map(_.char).contains(BoxStyle.Single.topRight),
            buf.get(0, 2).map(_.char).contains(BoxStyle.Single.bottomLeft),
            buf.get(5, 2).map(_.char).contains(BoxStyle.Single.bottomRight),
            buf.get(2, 1).map(_.char).contains('A')
          )
      },

      test("Borderless button writes no border glyphs at the corners") {
        for
          btn <- Button.make("X", noop, style = baseStyle, border = BoxStyle.Borderless)
        yield
          val buf = renderToBuffer(6, 3)(btn, ctx = unfocusedCtx)
          // No border — corners hold the fill (styled space).
          assertTrue(
            buf.get(0, 0).contains(Cell(' ', baseStyle)),
            buf.get(5, 0).contains(Cell(' ', baseStyle)),
            buf.get(0, 2).contains(Cell(' ', baseStyle)),
            buf.get(5, 2).contains(Cell(' ', baseStyle))
          )
      },

      test("Borderless button is valid at 1x1 and places the label there") {
        for
          btn <- Button.make("X", noop, style = baseStyle, border = BoxStyle.Borderless)
        yield
          val buf = renderToBuffer(3, 3)(btn, area = Rect(1, 1, 1, 1), ctx = unfocusedCtx)
          assertTrue(buf.get(1, 1).map(_.char).contains('X'))
      },

      test("padding shifts the label inward from the border") {
        for
          btn <- Button.make(
            "X", noop,
            style   = baseStyle,
            padding = Insets(top = 1, right = 2, bottom = 1, left = 2)
          )
        yield
          val buf = renderToBuffer(12, 6)(btn, ctx = unfocusedCtx)
          // Rect(0,0,12,6).inner(1) = Rect(1,1,10,4)
          // .inner(Insets(1,2,1,2)) = Rect(3,2,6,2)
          // Label "X": xOffset = (6-1)/2 = 2 → col 3+2=5; yOffset = (2-1)/2 = 0 → row 2
          assertTrue(buf.get(5, 2).map(_.char).contains('X'))
      },

      test("a label wider than the inner width is truncated to fit") {
        for
          btn <- Button.make("HelloWorld", noop, style = baseStyle)
        yield
          val buf = renderToBuffer(6, 3)(btn, ctx = unfocusedCtx)
          // Inner width = 4. Label truncates to "Hell"; xOffset = (4-4)/2 = 0 → col 1
          assertTrue(
            buf.get(1, 1).map(_.char).contains('H'),
            buf.get(2, 1).map(_.char).contains('e'),
            buf.get(3, 1).map(_.char).contains('l'),
            buf.get(4, 1).map(_.char).contains('l')
          )
      },

      test("Single-border button smaller than 2x2 is a no-op") {
        for
          btn <- Button.make("A", noop, style = baseStyle)
        yield
          val buf = renderToBuffer(4, 4)(btn, area = Rect(1, 1, 1, 1), ctx = unfocusedCtx)
          assertTrue(buf.get(1, 1).contains(Cell.Empty))
      }
    ),

    // ===== Rendering — interaction states =====

    suite("state modulation")(

      test("focused adds Bold to every rendered cell's attributes") {
        for
          btn <- Button.make("A", noop, style = baseStyle)
        yield
          val focused   = renderToBuffer(6, 3)(btn, ctx = focusCtx(btn))
          val unfocused = renderToBuffer(6, 3)(btn, ctx = unfocusedCtx)
          assertTrue(
            focused.get(0, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            focused.get(2, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            unfocused.get(0, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(false)
          )
      },

      test("focused preserves the role's fg (hue is role-owned)") {
        for
          btn <- Button.make("A", noop, style = baseStyle)
        yield
          val focused = renderToBuffer(6, 3)(btn, ctx = focusCtx(btn))
          assertTrue(focused.get(0, 0).map(_.style.fg).contains(baseStyle.fg))
      },

      test("disabled adds Dim to every rendered cell's attributes") {
        for
          btn <- Button.make("A", noop, style = baseStyle, enabled = false)
        yield
          val buf = renderToBuffer(6, 3)(btn, ctx = focusCtx(btn))
          assertTrue(
            buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(2, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
          )
      },

      test("disabled beats focused when both would apply") {
        for
          btn <- Button.make("A", noop, style = baseStyle, enabled = false)
        yield
          val buf = renderToBuffer(6, 3)(btn, ctx = focusCtx(btn))
          // A disabled button that the focus system somehow points at
          // still renders Dim, not Bold.
          assertTrue(
            buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(0, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(false)
          )
      }
    ),

    // ===== Focus opt-in =====

    suite("focus")(

      test("enabled buttons are focusable") {
        for
          btn <- Button.make("A", noop)
        yield assertTrue(btn.focusable)
      },

      test("disabled buttons are excluded from the focus cycle") {
        for
          btn <- Button.make("A", noop, enabled = false)
        yield assertTrue(!btn.focusable)
      }
    ),

    // ===== Event handling =====

    suite("handleEvent")(

      test("Enter on a focused enabled button returns Perform bound to onActivate") {
        for
          btn <- Button.make("A", noop)
        yield
          val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), focusCtx(btn))
          assertTrue(res match
            case EventResult.Perform(effect) => effect eq noop
            case _                           => false
          )
      },

      test("Space on a focused enabled button returns Perform bound to onActivate") {
        for
          btn <- Button.make("A", noop)
        yield
          val res = btn.handleEvent(CharKey(' ', Set.empty), focusCtx(btn))
          assertTrue(res match
            case EventResult.Perform(effect) => effect eq noop
            case _                           => false
          )
      },

      test("Enter on an unfocused button returns Ignored (§3.2 focus guard)") {
        for
          btn <- Button.make("A", noop)
        yield
          val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), unfocusedCtx)
          assertTrue(res == EventResult.Ignored)
      },

      test("Enter on a disabled focused button returns Ignored") {
        for
          btn <- Button.make("A", noop, enabled = false)
        yield
          val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), focusCtx(btn))
          assertTrue(res == EventResult.Ignored)
      },

      test("Space on a disabled focused button returns Ignored") {
        for
          btn <- Button.make("A", noop, enabled = false)
        yield
          val res = btn.handleEvent(CharKey(' ', Set.empty), focusCtx(btn))
          assertTrue(res == EventResult.Ignored)
      },

      test("other keys on a focused button return Ignored") {
        for
          btn <- Button.make("A", noop)
        yield
          val res = btn.handleEvent(CharKey('x', Set.empty), focusCtx(btn))
          assertTrue(res == EventResult.Ignored)
      },

      test("modifiers on Enter do not prevent activation") {
        for
          btn <- Button.make("A", noop)
        yield
          val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set(KeyModifier.Shift)), focusCtx(btn))
          assertTrue(res match
            case EventResult.Perform(_) => true
            case _                      => false
          )
      }
    )
  )
