package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.io.IOException

object TextInputSpec extends ZIOSpecDefault:

  private val noop: String => ZIO[Frame, IOException, Unit] = _ => ZIO.unit

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private def focusCtx(w: TextInput): RenderContext =
    RenderContext(FocusSnapshot(Some(w.id)))

  private val unfocusedCtx: RenderContext = RenderContext.empty

  private def keyPress(w: TextInput, event: Event): EventResult =
    w.handleEvent(event, focusCtx(w))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextInput")(
    // ===== Rendering — content =====

    suite("rendering")(
      test("value text sits in the inner region") {
        for
          w <- TextInput.make("hello", style = baseStyle)
        yield
          val buf = renderToBuffer(12, 3)(w, ctx = unfocusedCtx)
          // Rect(0,0,12,3).inner(1) = Rect(1,1,10,1); text starts at (1,1)
          assertTrue(
            buf.get(1, 1).map(_.char).contains('h'),
            buf.get(2, 1).map(_.char).contains('e'),
            buf.get(3, 1).map(_.char).contains('l'),
            buf.get(4, 1).map(_.char).contains('l'),
            buf.get(5, 1).map(_.char).contains('o')
          )
      },
      test("placeholder renders in muted (Dim) style when empty and unfocused") {
        for
          w <- TextInput.make(value = "", placeholder = "type here", style = baseStyle)
        yield
          val buf = renderToBuffer(14, 3)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(1, 1).map(_.char).contains('t'),
            buf.get(1, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
          )
      },
      test("placeholder is not drawn when the field has a value") {
        for
          w <- TextInput.make(value = "x", placeholder = "type here", style = baseStyle)
        yield
          val buf = renderToBuffer(14, 3)(w, ctx = unfocusedCtx)
          // First inner cell shows the value 'x', not the placeholder's 't'.
          assertTrue(buf.get(1, 1).map(_.char).contains('x'))
      },
      test("placeholder is not drawn when the field is focused (caret takes over)") {
        for
          w <- TextInput.make(value = "", placeholder = "type here", style = baseStyle)
        yield
          val buf = renderToBuffer(14, 3)(w, ctx = focusCtx(w))
          // No 't' at (1,1) — the focused empty field shows only the caret.
          assertTrue(!buf.get(1, 1).map(_.char).contains('t'))
      },
      test("Sides.none field writes no border glyphs") {
        for
          w <- TextInput.make("hi", style = baseStyle, sides = Sides.none)
        yield
          val buf = renderToBuffer(6, 3)(w, ctx = unfocusedCtx)
          // Value starts at inner (0,0) with no border.
          assertTrue(
            buf.get(0, 0).map(_.char).contains('h'),
            buf.get(1, 0).map(_.char).contains('i')
          )
      },
      test("padding shifts the value inward from the border") {
        for
          w <- TextInput.make(
            "X",
            style = baseStyle,
            padding = Insets(top = 1, right = 2, bottom = 1, left = 2)
          )
        yield
          val buf = renderToBuffer(12, 6)(w, ctx = unfocusedCtx)
          // Rect(0,0,12,6).inner(1).inner(Insets(1,2,1,2)) = Rect(3,2,6,2)
          assertTrue(buf.get(3, 2).map(_.char).contains('X'))
      }
    ),

    // ===== Rendering — states =====

    suite("state modulation")(
      test("focused adds Bold to the effective style") {
        for
          w <- TextInput.make("abc", style = baseStyle)
        yield
          val focused = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          val unfocused = renderToBuffer(8, 3)(w, ctx = unfocusedCtx)
          assertTrue(
            focused.get(1, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            unfocused.get(1, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(false)
          )
      },
      test("disabled adds Dim to the effective style") {
        for
          w <- TextInput.make("abc", style = baseStyle, enabled = false)
        yield
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          assertTrue(buf.get(1, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true))
      },
      test("focused draws a Reverse caret at the caret position") {
        for
          w <- TextInput.make("abc", style = baseStyle)
        yield
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          // Caret starts at length 3 (end of "abc") — the reversed cell is
          // one past the last char, at inner col 3 → buffer col 4.
          assertTrue(
            buf.get(4, 1).map(_.style.attributes.contains(Attribute.Reverse)).contains(true),
            buf.get(4, 1).map(_.char).contains(' ')
          )
      },
      test("unfocused field draws no caret") {
        for
          w <- TextInput.make("abc", style = baseStyle)
        yield
          val buf = renderToBuffer(8, 3)(w, ctx = unfocusedCtx)
          // No cell inside the inner region carries Reverse.
          val innerHasReverse =
            (1 to 6).exists(x =>
              buf.get(x, 1).map(_.style.attributes.contains(Attribute.Reverse)).contains(true)
            )
          assertTrue(!innerHasReverse)
      }
    ),

    // ===== Focus opt-in =====

    suite("focus")(
      test("enabled fields are focusable") {
        for
          w <- TextInput.make("")
        yield assertTrue(w.focusable)
      },
      test("disabled fields are excluded from the focus cycle") {
        for
          w <- TextInput.make("", enabled = false)
        yield assertTrue(!w.focusable)
      }
    ),

    // ===== Event handling — editing =====

    suite("insertion")(
      test("printable char inserts at the caret and returns Perform") {
        for
          w <- TextInput.make("ab")
        yield
          val before = w.value
          val res = keyPress(w, CharKey('X', Set.empty))
          assertTrue(
            before == "ab",
            w.value == "abX",
            w.caret == 3,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("printable char inserts in the middle when caret is mid-buffer") {
        for
          w <- TextInput.make("ac")
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          val res = keyPress(w, CharKey('b', Set.empty))
          assertTrue(
            w.value == "abc",
            w.caret == 2,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Ctrl+char is not treated as printable text") {
        for
          w <- TextInput.make("ab")
        yield
          val res = keyPress(w, CharKey('c', Set(KeyModifier.Ctrl)))
          assertTrue(
            w.value == "ab",
            res == EventResult.Ignored
          )
      },
      test("Alt+char is not treated as printable text") {
        for
          w <- TextInput.make("ab")
        yield
          val res = keyPress(w, CharKey('c', Set(KeyModifier.Alt)))
          assertTrue(
            w.value == "ab",
            res == EventResult.Ignored
          )
      }
    ),
    suite("deletion")(
      test("Backspace removes the char before the caret") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set.empty))
          assertTrue(
            w.value == "ab",
            w.caret == 2,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Backspace at position 0 returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set.empty))
          assertTrue(w.value == "abc", res == EventResult.Ignored)
      },
      test("Delete removes the char at the caret") {
        for
          w <- TextInput.make("abc")
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Delete, Set.empty))
          assertTrue(
            w.value == "bc",
            w.caret == 0,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Delete at end-of-buffer returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Delete, Set.empty))
          assertTrue(w.value == "abc", res == EventResult.Ignored)
      }
    ),
    suite("caret motion")(
      test("Left decrements the caret and requests a redraw") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          assertTrue(w.caret == 2, res == EventResult.RequestRedraw)
      },
      test("Left at position 0 returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          assertTrue(w.caret == 0, res == EventResult.Ignored)
      },
      test("Right at end-of-buffer returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Right, Set.empty))
          assertTrue(w.caret == 3, res == EventResult.Ignored)
      },
      test("Home moves the caret to 0") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          assertTrue(w.caret == 0, res == EventResult.RequestRedraw)
      },
      test("End moves the caret to the buffer length") {
        for
          w <- TextInput.make("abc")
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.End, Set.empty))
          assertTrue(w.caret == 3, res == EventResult.RequestRedraw)
      }
    ),

    // ===== Event handling — bubbling =====

    suite("bubbling")(
      test("Enter returns Ignored so a parent can react") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Enter, Set.empty))
          assertTrue(res == EventResult.Ignored)
      },
      test("Tab returns Ignored so the framework can move focus") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Tab, Set.empty))
          assertTrue(res == EventResult.Ignored)
      },
      test("Escape returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Escape, Set.empty))
          assertTrue(res == EventResult.Ignored)
      },
      test("all keys on an unfocused field return Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          val res = w.handleEvent(CharKey('x', Set.empty), unfocusedCtx)
          assertTrue(res == EventResult.Ignored, w.value == "abc")
      },
      test("all keys on a disabled focused field return Ignored") {
        for
          w <- TextInput.make("abc", enabled = false)
        yield
          val res = keyPress(w, CharKey('x', Set.empty))
          assertTrue(res == EventResult.Ignored, w.value == "abc")
      }
    ),

    // ===== Horizontal scroll =====

    suite("horizontal scroll")(
      test("caret at end of a long value slides the visible slice right") {
        for
          w <- TextInput.make("abcdefghij", style = baseStyle)
        yield
          // Inner width is 8 - 2 = 6; the field has 10 chars and the caret
          // sits at the end. The visible slice must end at 'j' with the
          // caret marker one past that — inner col 5, buffer col 6.
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          assertTrue(
            // Last visible value char is 'j' at inner col 4 (buffer col 5)
            buf.get(5, 1).map(_.char).contains('j'),
            // Caret reversed cell just past 'j' at inner col 5 (buffer col 6)
            buf.get(6, 1).map(_.style.attributes.contains(Attribute.Reverse)).contains(true)
          )
      },
      test("Home scrolls back so the first char is visible again") {
        for
          w <- TextInput.make("abcdefghij", style = baseStyle)
        yield
          // Render once at end to advance scroll, then Home + render again.
          renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          assertTrue(buf.get(1, 1).map(_.char).contains('a'))
      }
    )
  )
