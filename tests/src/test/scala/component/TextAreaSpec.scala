package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, ScreenBuffer}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, Keymap, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}
import layout.Constraint
import render.{LayoutManager, RevealPass}
import testkit.RenderHarness.*

import zio.*
import zio.test.*

object TextAreaSpec extends ZIOSpecDefault:

  private val baseStyle = CellStyle(fg = Foreground.Named(FgColor.White))

  private val zwj = "\u200D"
  private val family = "👨" + zwj + "👩" + zwj + "👧"

  private def focusCtx(w: Component): RenderContext = RenderContext(FocusSnapshot(Some(w.id)))

  private def press(w: TextArea, event: Event): EventResult = w.handleEvent(event, focusCtx(w))

  private def key(code: SpecialKeyCode, mods: KeyModifier*): Event = SpecialKey(code, mods.toSet)

  private def char(c: Char): Event = CharKey(c, Set.empty)

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_)                                                 => true
      case EventResult.Consumed | EventResult.Ignored | EventResult.RequestRedraw => false

  /**
   * Render focused at `width` × (`rows` + 2): a bordered field whose wrap
   * width is `width - 3` (two border columns, one reserved caret column).
   */
  private def draw(w: TextArea, width: Int = 12): ScreenBuffer =
    renderToBuffer(width, w.rows + 2)(w, ctx = focusCtx(w))

  private def hasAttr(buf: ScreenBuffer, x: Int, y: Int, a: Attribute): Boolean =
    buf.styleAt(x, y).exists(_.attributes.contains(a))

  /** Cells carrying `Reverse` — where the caret is drawn. */
  private def caretCells(buf: ScreenBuffer): Seq[(Int, Int)] =
    for
      y <- 0 until buf.height
      x <- 0 until buf.width
      if hasAttr(buf, x, y, Attribute.Reverse)
    yield (x, y)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextArea")(
    suite("editing")(
      test("printable text inserts at the caret and returns Perform") {
        for w <- TextArea.make("ac")
        yield
          val _ = press(w, key(SpecialKeyCode.Left))
          val res = press(w, char('b'))
          assertTrue(w.value == "abc", w.caret == 2, isPerform(res))
      },
      test("Enter inserts a newline") {
        for w <- TextArea.make("ab")
        yield
          val _ = press(w, key(SpecialKeyCode.Left))
          val res = press(w, key(SpecialKeyCode.Enter))
          assertTrue(w.value == "a\nb", w.caret == 2, isPerform(res))
      },
      test("Backspace at a line start and Delete at a line end join lines") {
        for
          a <- TextArea.make("ab\ncd")
          b <- TextArea.make("ab\ncd")
        yield
          val _ = press(a, key(SpecialKeyCode.Home))
          val _ = press(a, key(SpecialKeyCode.Backspace))
          val _ = draw(b)
          val _ = press(b, key(SpecialKeyCode.Up))
          val _ = press(b, key(SpecialKeyCode.Delete))
          assertTrue(a.value == "abcd", a.caret == 2, b.value == "abcd", b.caret == 2)
      },
      test("Backspace removes a whole emoji ZWJ sequence") {
        for w <- TextArea.make("x" + family)
        yield
          val _ = press(w, key(SpecialKeyCode.Backspace))
          assertTrue(w.value == "x", w.caret == 1)
      },
      test("word motion crosses a newline") {
        for w <- TextArea.make("one\ntwo")
        yield
          val _ = press(w, key(SpecialKeyCode.Left, KeyModifier.Alt))
          val _ = press(w, key(SpecialKeyCode.Left, KeyModifier.Alt))
          val afterLeft = w.caret
          val _ = press(w, CharKey('f', Set(KeyModifier.Alt)))
          assertTrue(afterLeft == 0, w.caret == 3)
      },
      test("Tab, Escape, Ctrl+Home and Ctrl+End are not consumed") {
        for w <- TextArea.make("ab\ncd")
        yield
          val _ = draw(w)
          assertTrue(
            press(w, key(SpecialKeyCode.Tab)) == EventResult.Ignored,
            press(w, key(SpecialKeyCode.Escape)) == EventResult.Ignored,
            press(w, key(SpecialKeyCode.Home, KeyModifier.Ctrl)) == EventResult.Ignored,
            press(w, key(SpecialKeyCode.End, KeyModifier.Ctrl)) == EventResult.Ignored,
            w.caret == 5
          )
      },
      test("an unfocused or disabled field ignores every key") {
        for
          w <- TextArea.make("ab")
          d <- TextArea.make("ab", enabled = false)
        yield assertTrue(
          w.handleEvent(char('x'), RenderContext.empty) == EventResult.Ignored,
          press(d, char('x')) == EventResult.Ignored,
          !d.focusable,
          w.value == "ab",
          d.value == "ab"
        )
      },
      test("reset restores the seed and clears the scroll") {
        for w <- TextArea.make("1\n2\n3\n4", rows = 2)
        yield
          val _ = draw(w)
          val _ = press(w, char('x'))
          w.reset()
          assertTrue(w.value == "1\n2\n3\n4", w.caret == 7, w.scrollRow == 0)
      }
    ),
    suite("visual navigation")(
      test("Up / Down keep the preferred column through a shorter line") {
        for w <- TextArea.make("abcdefgh\nab\nabcdefgh")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Up))
          val onShort = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Up))
          val onFirst = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Down))
          val _ = press(w, key(SpecialKeyCode.Down))
          assertTrue(
            onShort == TextArea.Position(1, 2),
            onFirst == TextArea.Position(0, 8),
            w.caret == 20
          )
      },
      test("Up / Down move across soft-wrapped lines") {
        // Wrap width 9: "hello" | "world foo".
        for w <- TextArea.make("hello world foo")
        yield
          val _ = draw(w)
          val start = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Up))
          val up = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Down))
          assertTrue(
            start == TextArea.Position(1, 9),
            up == TextArea.Position(0, 5),
            w.caretPosition == TextArea.Position(1, 9)
          )
      },
      test("Up on the first line and Down on the last are Ignored") {
        for w <- TextArea.make("hello world foo")
        yield
          val _ = draw(w)
          val down = press(w, key(SpecialKeyCode.Down))
          val _ = press(w, key(SpecialKeyCode.Up))
          val up = press(w, key(SpecialKeyCode.Up))
          assertTrue(down == EventResult.Ignored, up == EventResult.Ignored)
      },
      test("Home / End stay on the visual line") {
        for w <- TextArea.make("hello world foo")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Home))
          val home = w.caret
          val _ = press(w, key(SpecialKeyCode.Up))
          val _ = press(w, key(SpecialKeyCode.End))
          assertTrue(home == 6, w.caret == 5, w.caretPosition == TextArea.Position(0, 5))
      },
      test("Left / Right step over the whitespace dropped at a soft break") {
        for w <- TextArea.make("hello   world")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Home))
          val _ = press(w, key(SpecialKeyCode.Left))
          val left = w.caret
          val _ = press(w, key(SpecialKeyCode.Right))
          assertTrue(left == 5, w.caret == 8)
      },
      test("End on a word broken mid-way stays on the upper line") {
        // Wrap width 9: "abcdefghi" | "jkl", sharing offset 9.
        for w <- TextArea.make("abcdefghijkl")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Up))
          val _ = press(w, key(SpecialKeyCode.End))
          val end = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Right))
          val next = w.caretPosition
          val _ = press(w, key(SpecialKeyCode.Left))
          assertTrue(
            end == TextArea.Position(0, 9),
            next == TextArea.Position(1, 0),
            w.caretPosition == TextArea.Position(0, 9),
            w.caret == 9
          )
      },
      test("vertical moves never land inside a wide grapheme") {
        for w <- TextArea.make("abc\n你好世界")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Up))
          val _ = press(w, key(SpecialKeyCode.End))
          val _ = press(w, key(SpecialKeyCode.Down))
          assertTrue(w.caret == 5, w.caretPosition == TextArea.Position(1, 2))
      },
      test("Left / Right step CJK one ideograph at a time") {
        for w <- TextArea.make("你好")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Left))
          assertTrue(w.caret == 1, w.caretPosition == TextArea.Position(0, 2))
      }
    ),
    suite("internal scroll")(
      test("typing past the last visible row scrolls") {
        for w <- TextArea.make("1\n2", rows = 2)
        yield
          val _ = draw(w)
          val before = w.scrollRow
          val _ = press(w, key(SpecialKeyCode.Enter))
          val _ = press(w, char('3'))
          val buf = draw(w)
          assertTrue(
            before == 0,
            w.scrollRow == 1,
            buf.glyphGrid(1).startsWith("│2"),
            buf.glyphGrid(2).startsWith("│3")
          )
      },
      test("PgUp / PgDn move by the visible rows") {
        for w <- TextArea.make("1\n2\n3\n4\n5", rows = 2)
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.PgUp))
          val _ = draw(w)
          val up = (w.caretPosition.line, w.scrollRow)
          val _ = press(w, key(SpecialKeyCode.PgUp))
          val _ = draw(w)
          val top = (w.caretPosition.line, w.scrollRow)
          val atTop = press(w, key(SpecialKeyCode.PgUp))
          val _ = press(w, key(SpecialKeyCode.PgDn))
          val _ = draw(w)
          assertTrue(
            up == (2, 1),
            top == (0, 0),
            atTop == EventResult.Ignored,
            w.caretPosition.line == 2
          )
      },
      test("a narrower width re-wraps and keeps the caret visible") {
        for w <- TextArea.make("aaaa bbbb cccc dddd", rows = 2)
        yield
          val _ = draw(w, width = 24)
          val wide = w.scrollRow
          val buf = draw(w, width = 8)
          assertTrue(
            wide == 0,
            w.caretPosition == TextArea.Position(3, 4),
            w.scrollRow == 2,
            caretCells(buf) == Seq((5, 2))
          )
      }
    ),
    suite("rendering")(
      test("placeholder wraps in Dim when empty and unfocused") {
        for w <- TextArea.make(placeholder = "Write something here", style = baseStyle)
        yield
          val buf = renderToBuffer(12, 5)(w)
          assertTrue(
            buf.glyphGrid(1).startsWith("│Write"),
            buf.glyphGrid(2).startsWith("│something"),
            buf.glyphGrid(3).startsWith("│here"),
            hasAttr(buf, 1, 1, Attribute.Dim),
            caretCells(buf).isEmpty
          )
      },
      test("placeholder stays when focused, with the caret on its first cell") {
        for w <- TextArea.make(placeholder = "Write something", style = baseStyle)
        yield
          val buf = draw(w)
          assertTrue(
            buf.glyphGrid(1).startsWith("│Write"),
            caretCells(buf) == Seq((1, 1)),
            buf.charAt(1, 1).contains('W')
          )
      },
      test("placeholder is not drawn when the field has a value") {
        for w <- TextArea.make("hi", placeholder = "Write")
        yield assertTrue(renderToBuffer(12, 5)(w).glyphGrid(1).startsWith("│hi."))
      },
      test("the caret is a Reverse cell on the grapheme under it") {
        for w <- TextArea.make("abc\nde")
        yield
          val _ = draw(w)
          val _ = press(w, key(SpecialKeyCode.Up))
          val buf = draw(w)
          assertTrue(caretCells(buf) == Seq((3, 1)), buf.charAt(3, 1).contains('c'))
      },
      test("a caret after a full line sits in the reserved column") {
        for w <- TextArea.make("abcdefghi")
        yield
          val buf = draw(w)
          assertTrue(
            buf.glyphGrid(1) == "│abcdefghi.│",
            caretCells(buf) == Seq((10, 1)),
            buf.glyphGrid(2) == "│..........│"
          )
      },
      test("focused adds Bold, disabled adds Dim, unfocused shows no caret") {
        for
          w <- TextArea.make("abc", style = baseStyle)
          d <- TextArea.make("abc", style = baseStyle, enabled = false)
        yield
          val focused = draw(w)
          val unfocused = renderToBuffer(12, 5)(w)
          val disabled = draw(d)
          assertTrue(
            hasAttr(focused, 1, 1, Attribute.Bold),
            !hasAttr(unfocused, 1, 1, Attribute.Bold),
            caretCells(unfocused).isEmpty,
            hasAttr(disabled, 1, 1, Attribute.Dim),
            caretCells(disabled).isEmpty
          )
      },
      test("the grip replaces the bottom-right corner, only with both sides drawn") {
        for
          w <- TextArea.make("x", grip = Some(TextArea.GripGlyph))
          n <- TextArea.make("x", grip = Some(TextArea.GripGlyph), sides = Sides.all.copy(bottom = false))
          p <- TextArea.make("x")
        yield assertTrue(
          renderToBuffer(12, 5)(w).charAt(11, 4).contains(TextArea.GripGlyph),
          !renderToBuffer(12, 5)(n).glyphGrid.mkString.contains(TextArea.GripGlyph.toString),
          renderToBuffer(12, 5)(p).charAt(11, 4).contains('┘')
        )
      },
      test("Cyrillic and CJK draw by cell") {
        for w <- TextArea.make("привет\n你好")
        yield
          val buf = renderToBuffer(12, 5)(w)
          assertTrue(
            buf.glyphGrid(1).startsWith("│привет"),
            buf.charAt(1, 2).contains('你'),
            buf.charAt(3, 2).contains('好')
          )
      },
      test("preferred height is rows plus border and padding") {
        for
          w <- TextArea.make(rows = 3)
          p <- TextArea.make(rows = 4, padding = Insets(1, 0, 1, 0))
          n <- TextArea.make(rows = 2, sides = Sides.none)
        yield assertTrue(
          w.preferredHeight(20).contains(5),
          p.preferredHeight(20).contains(8),
          n.preferredHeight(20).contains(2)
        )
      }
    ),
    suite("inside a ScrollView")(
      test("a field that fits the viewport shows its caret row after Tab-reveal") {
        for
          w <- TextArea.make("1\n2\n3", rows = 3)
          view <- ScrollView.make(
                    VBox(
                      Constraint.Fixed(10) -> Spacer,
                      Constraint.Fixed(5)  -> w,
                      Constraint.Fixed(10) -> Spacer
                    )
                  )
        yield
          val area = Rect(0, 0, 12, 6)
          val first = LayoutManager.default.resolve(view, area)
          val _ = RevealPass(first, w.id)
          val _ = LayoutManager.default.resolve(view, area)
          val buf = renderToBuffer(12, 6)(view, ctx = focusCtx(w))
          val caret = caretCells(buf)
          assertTrue(
            caret.size == 1,
            caret.forall((_, y) => y >= 0 && y < 6),
            w.caretPosition.line == 2
          )
      }
    ),
    suite("maxLength")(
      test("input at the limit is refused and consumed") {
        for
          w <- TextArea.make("abc", maxLength = Some(3))
        yield
          val res = press(w, char('d'))
          assertTrue(res == EventResult.Consumed, w.value == "abc")
      },
      test("Enter counts as one and is refused at the limit") {
        for
          w <- TextArea.make("ab", maxLength = Some(3))
        yield
          val _ = press(w, key(SpecialKeyCode.Enter))
          val refused = press(w, key(SpecialKeyCode.Enter))
          assertTrue(w.value == "ab\n", refused == EventResult.Consumed)
      },
      test("delete then insert at the limit") {
        for
          w <- TextArea.make("abc", maxLength = Some(3))
        yield
          val _ = press(w, key(SpecialKeyCode.Backspace))
          val _ = press(w, char('x'))
          val refused = press(w, char('y'))
          assertTrue(w.value == "abx", refused == EventResult.Consumed)
      },
      test("a paste arriving key by key stops at the limit") {
        for
          w <- TextArea.make("", maxLength = Some(4))
        yield
          "one two".foreach(c => press(w, char(c)))
          assertTrue(w.value == "one ")
      },
      test("a ZWJ sequence counts as one") {
        for
          w <- TextArea.make(family + "a", maxLength = Some(3))
        yield
          val _ = press(w, char('b'))
          val refused = press(w, char('c'))
          assertTrue(w.value == family + "ab", refused == EventResult.Consumed)
      },
      test("an over-long seed is shown whole, refuses growth, and can shrink") {
        for
          w <- TextArea.make("abcdef", maxLength = Some(3))
        yield
          val buf = draw(w)
          val shown = (1 to 6).flatMap(x => buf.get(x, 1).map(_.char)).mkString
          val grow = press(w, char('g'))
          val _ = press(w, key(SpecialKeyCode.Backspace))
          assertTrue(shown == "abcdef", grow == EventResult.Consumed, w.value == "abcde")
      }
    ),
    suite("keymap")(
      test("Ctrl+Left and a modified Backspace bubble: chords are exact") {
        for
          w <- TextArea.make(value = "abc")
        yield
          val ctrlLeft = press(w, key(SpecialKeyCode.Left, KeyModifier.Ctrl))
          val altBackspace = press(w, key(SpecialKeyCode.Backspace, KeyModifier.Alt))
          assertTrue(
            ctrlLeft == EventResult.Ignored,
            altBackspace == EventResult.Ignored,
            w.value == "abc",
            w.caret == 3
          )
      },
      test("a keymap without Enter lets it bubble and binds a line break elsewhere") {
        val keymap =
          Keymap(TextArea.DefaultKeymap.bindings - Keymap.plain(SpecialKeyCode.Enter)) ++
            Keymap(Keymap.alt('n') -> TextArea.Action.NewLine)
        for
          w <- TextArea.make(value = "ab", keymap = keymap)
        yield
          val enter = press(w, key(SpecialKeyCode.Enter))
          val afterEnter = w.value
          val res = press(w, CharKey('n', Set(KeyModifier.Alt)))
          assertTrue(enter == EventResult.Ignored, afterEnter == "ab", isPerform(res), w.value == "ab\n")
      }
    )
  )
