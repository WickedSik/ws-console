package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, Keymap, SpecialKeyCode}
import geometry.{Insets, Sides}
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

object TextInputSpec extends ZIOSpecDefault:

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private def focusCtx(w: TextInput): RenderContext =
    RenderContext(FocusSnapshot(Some(w.id)))

  private val unfocusedCtx: RenderContext = RenderContext.empty

  private def keyPress(w: TextInput, event: Event): EventResult =
    w.handleEvent(event, focusCtx(w))

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_)                                                 => true
      case EventResult.Consumed | EventResult.Ignored | EventResult.RequestRedraw => false

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
      test("placeholder stays visible in Dim when focused and empty (HTML-aligned)") {
        for
          w <- TextInput.make(value = "", placeholder = "type here", style = baseStyle)
        yield
          val buf = renderToBuffer(14, 3)(w, ctx = focusCtx(w))
          // Placeholder characters past the caret keep Dim; the caret cell
          // at inner (0,0) — buffer (1,1) — carries the placeholder char
          // 't' with both Dim and Reverse overlaid.
          assertTrue(
            buf.get(1, 1).map(_.char).contains('t'),
            buf.get(1, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(1, 1).map(_.style.attributes.contains(Attribute.Reverse)).contains(true),
            buf.get(2, 1).map(_.char).contains('y'),
            buf.get(2, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(2, 1).map(_.style.attributes.contains(Attribute.Reverse)).contains(false)
          )
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
            isPerform(res)
          )
      },
      test("printable char inserts in the middle when caret is mid-buffer") {
        for
          w <- TextInput.make("ac")
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          val res = keyPress(w, CharKey('b', Set.empty))
          assertTrue(
            w.value == "abc",
            w.caret == 2,
            isPerform(res)
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
            isPerform(res)
          )
      },
      test("Backspace at position 0 returns Ignored") {
        for
          w <- TextInput.make("abc")
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set.empty))
          assertTrue(w.value == "abc", res == EventResult.Ignored)
      },
      test("Delete removes the char at the caret") {
        for
          w <- TextInput.make("abc")
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Delete, Set.empty))
          assertTrue(
            w.value == "bc",
            w.caret == 0,
            isPerform(res)
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
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
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
      test("Ctrl+Home / Ctrl+End are left to bubble to an enclosing view") {
        for
          w <- TextInput.make("abc")
        yield
          val home = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set(KeyModifier.Ctrl)))
          val end = keyPress(w, SpecialKey(SpecialKeyCode.End, Set(KeyModifier.Ctrl)))
          assertTrue(home == EventResult.Ignored, end == EventResult.Ignored, w.caret == 3)
      },
      test("End moves the caret to the buffer length") {
        for
          w <- TextInput.make("abc")
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
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

    // ===== maxLength =====

    suite("maxLength")(
      test("input at the limit is refused and consumed, so no onChange is performed") {
        for
          w <- TextInput.make("abc", maxLength = Some(3))
        yield
          val res = keyPress(w, CharKey('d', Set.empty))
          assertTrue(res == EventResult.Consumed, w.value == "abc")
      },
      test("below the limit input is accepted") {
        for
          w <- TextInput.make("ab", maxLength = Some(3))
        yield
          val res = keyPress(w, CharKey('c', Set.empty))
          assertTrue(isPerform(res), w.value == "abc")
      },
      test("delete then insert at the limit") {
        for
          w <- TextInput.make("abc", maxLength = Some(3))
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set.empty))
          val _ = keyPress(w, CharKey('x', Set.empty))
          val refused = keyPress(w, CharKey('y', Set.empty))
          assertTrue(w.value == "abx", refused == EventResult.Consumed)
      },
      test("a paste arriving key by key stops at the limit") {
        for
          w <- TextInput.make("", maxLength = Some(5))
        yield
          "hello world".foreach(c => keyPress(w, CharKey(c, Set.empty)))
          assertTrue(w.value == "hello")
      },
      test("an emoji and a ZWJ sequence each count as one") {
        val zwj = "\u200D"
        val family = "👨" + zwj + "👩" + zwj + "👧"
        for
          full <- TextInput.make(family + "👍🏽a", maxLength = Some(3))
          room <- TextInput.make(family + "👍🏽", maxLength = Some(3))
        yield
          val refused = keyPress(full, CharKey('b', Set.empty))
          val _ = keyPress(room, CharKey('b', Set.empty))
          assertTrue(
            refused == EventResult.Consumed,
            room.value == family + "👍🏽b"
          )
      },
      test("an over-long seed is shown whole, refuses growth, and can shrink") {
        for
          w <- TextInput.make("abcdef", maxLength = Some(3), style = baseStyle)
        yield
          val buf = renderToBuffer(10, 3)(w, ctx = unfocusedCtx)
          val shown = (1 to 6).flatMap(x => buf.get(x, 1).map(_.char)).mkString
          val grow = keyPress(w, CharKey('g', Set.empty))
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set.empty))
          assertTrue(shown == "abcdef", grow == EventResult.Consumed, w.value == "abcde")
      },
      test("a combining mark at the limit joins the last cluster") {
        for
          w <- TextInput.make("abe", maxLength = Some(3))
        yield
          val _ = keyPress(w, CharKey('\u0301', Set.empty))
          assertTrue(w.value == "abe\u0301")
      },
      test("caret motion at the limit is unaffected") {
        for
          w <- TextInput.make("abc", maxLength = Some(3))
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          assertTrue(res == EventResult.RequestRedraw, w.caret == 2)
      },
      test("a negative maxLength is rejected") {
        for exit <- TextInput.make(maxLength = Some(-1)).exit
        yield assertTrue(exit.isFailure)
      }
    ),

    // ===== Reset =====

    suite("reset")(
      test("reset restores the buffer to the initial value and caret to its end") {
        for
          w <- TextInput.make("seed")
        yield
          val _ = keyPress(w, CharKey('X', Set.empty))
          val _ = keyPress(w, CharKey('Y', Set.empty))
          val beforeValue = w.value
          val beforeCaret = w.caret
          w.reset()
          assertTrue(
            beforeValue == "seedXY",
            beforeCaret == 6,
            w.value == "seed",
            w.caret == 4
          )
      },
      test("reset on a widget seeded with empty string clears the buffer entirely") {
        for
          w <- TextInput.make("")
        yield
          val _ = keyPress(w, CharKey('h', Set.empty))
          val _ = keyPress(w, CharKey('i', Set.empty))
          w.reset()
          assertTrue(w.value == "", w.caret == 0)
      },
      test("reset returns the horizontal scroll to the beginning") {
        for
          w <- TextInput.make("", style = baseStyle)
        yield
          // Type past the visible width to force a horizontal scroll.
          "abcdefghij".foreach(c => keyPress(w, CharKey(c, Set.empty)))
          val _ = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          w.reset()
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          // Empty buffer after reset: the caret cell at inner (0,0) —
          // buffer (1,1) — carries a blank, not a scrolled-in char.
          assertTrue(w.value == "", buf.get(1, 1).map(_.char).contains(' '))
      }
    ),

    // ===== Set =====

    suite("set")(
      test("set replaces the buffer and puts the caret at its end") {
        for
          w <- TextInput.make("seed")
        yield
          w.set("0,8")
          assertTrue(w.value == "0,8", w.caret == 3)
      },
      test("set is not bound by maxLength") {
        for
          w <- TextInput.make("", maxLength = Some(3))
        yield
          w.set("abcdef")
          val refused = keyPress(w, CharKey('g', Set.empty))
          assertTrue(w.value == "abcdef", refused == EventResult.Consumed)
      },
      test("set returns the horizontal scroll to the beginning") {
        for
          w <- TextInput.make("", style = baseStyle)
        yield
          "abcdefghij".foreach(c => keyPress(w, CharKey(c, Set.empty)))
          val _ = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          w.set("xy")
          val buf = renderToBuffer(8, 3)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(1, 1).map(_.char).contains('x'), buf.get(2, 1).map(_.char).contains('y'))
      },
      test("reset after set restores the seeded value") {
        for
          w <- TextInput.make("seed")
        yield
          w.set("other")
          w.reset()
          assertTrue(w.value == "seed")
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
          val _ = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val buf = renderToBuffer(8, 3)(w, ctx = focusCtx(w))
          assertTrue(buf.get(1, 1).map(_.char).contains('a'))
      }
    ),
    suite("cell widths")(
      test("wide text typed past the width scrolls by cells; the border stays whole") {
        for w <- TextInput.make(value = "日本語日本語")
        yield
          // Inner width 10: the caret cell plus at most 9 cells of text, four wide glyphs.
          val buf = renderToBuffer(12, 3)(w, ctx = focusCtx(w))
          assertTrue(
            buf.get(1, 1).map(_.text).contains("語"),
            buf.get(7, 1).map(_.text).contains("語"),
            buf.get(9, 1).exists(_.style.attributes.contains(Attribute.Reverse)),
            buf.get(11, 1).map(_.char).contains('│')
          )
      },
      test("the caret covers the whole wide grapheme under it") {
        for w <- TextInput.make(value = "日本語")
        yield
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          val _ = keyPress(w, SpecialKey(SpecialKeyCode.Right, Set.empty))
          val buf = renderToBuffer(12, 3)(w, ctx = focusCtx(w))
          assertTrue(
            buf.get(3, 1).map(_.text).contains("本"),
            buf.get(3, 1).exists(_.style.attributes.contains(Attribute.Reverse)),
            !buf.get(1, 1).exists(_.style.attributes.contains(Attribute.Reverse))
          )
      },
      test("seeded emoji never split: every drawn cell is a whole emoji or blank") {
        for w <- TextInput.make(value = "😀" * 10)
        yield
          val buf = renderToBuffer(12, 3)(w, ctx = focusCtx(w))
          val texts = (1 to 10).flatMap(x => buf.get(x, 1).map(_.text))
          assertTrue(
            texts.forall(t => t == "😀" || t.isBlank || t.isEmpty),
            texts.count(_ == "😀") == 4,
            buf.get(11, 1).map(_.char).contains('│')
          )
      },
      test("unfocused, a wide value keeps its scroll, draws no caret and stays inside the border") {
        for w <- TextInput.make(value = "日本語日本語")
        yield
          // The seeded caret sits at the end and stays in view whether or not the field is focused.
          val buf = renderToBuffer(12, 3)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(1, 1).map(_.text).contains("語"),
            buf.get(7, 1).map(_.text).contains("語"),
            (1 to 10).forall(x => !buf.get(x, 1).exists(_.style.attributes.contains(Attribute.Reverse))),
            buf.get(11, 1).map(_.char).contains('│')
          )
      },
      test("Cyrillic past the width shows its tail and the caret") {
        for w <- TextInput.make(value = "Привет, мир!")
        yield
          val buf = renderToBuffer(12, 3)(w, ctx = focusCtx(w))
          val row = (0 until 12).flatMap(x => buf.get(x, 1).map(_.char)).mkString
          assertTrue(row == "│вет, мир! │", buf.get(10, 1).exists(_.style.attributes.contains(Attribute.Reverse)))
      },
      test("a wide placeholder truncates by cells") {
        for w <- TextInput.make(placeholder = "名前を入力")
        yield
          // Inner width 9: four two-cell glyphs, never half of the fifth.
          val buf = renderToBuffer(11, 3)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(7, 1).map(_.text).contains("入"),
            buf.get(9, 1).forall(c => c.text.isBlank || c.text.isEmpty),
            buf.get(10, 1).map(_.char).contains('│')
          )
      }
    ),
    suite("keymap")(
      test("Ctrl+Left / Ctrl+Right and Shift+Left bubble: chords are exact") {
        for
          w <- TextInput.make(value = "abc")
        yield
          val ctrlLeft = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set(KeyModifier.Ctrl)))
          val ctrlRight = keyPress(w, SpecialKey(SpecialKeyCode.Right, Set(KeyModifier.Ctrl)))
          val shiftLeft = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set(KeyModifier.Shift)))
          assertTrue(
            ctrlLeft == EventResult.Ignored,
            ctrlRight == EventResult.Ignored,
            shiftLeft == EventResult.Ignored,
            w.caret == 3
          )
      },
      test("a modified Backspace or Delete bubbles and leaves the value") {
        for
          w <- TextInput.make(value = "abc")
        yield
          val altBackspace = keyPress(w, SpecialKey(SpecialKeyCode.Backspace, Set(KeyModifier.Alt)))
          val ctrlDelete = keyPress(w, SpecialKey(SpecialKeyCode.Delete, Set(KeyModifier.Ctrl)))
          assertTrue(altBackspace == EventResult.Ignored, ctrlDelete == EventResult.Ignored, w.value == "abc")
      },
      test("a replaced keymap binds its own chords") {
        val keymap = TextInput.DefaultKeymap ++ Keymap(Keymap.ctrl(SpecialKeyCode.Left) -> TextInput.Action.WordLeft)
        for
          w <- TextInput.make(value = "hello world", keymap = keymap)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set(KeyModifier.Ctrl)))
          assertTrue(res == EventResult.RequestRedraw, w.caret == 6)
      }
    )
  )
