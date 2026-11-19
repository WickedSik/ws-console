package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, CellStyle, ScreenBuffer}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import layout.Constraint
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

object TagInputSpec extends ZIOSpecDefault:

  private val options = Seq("Alpha", "Beta", "Gamma")

  private def focusCtx(c: Component): RenderContext = RenderContext(FocusSnapshot(Some(c.id)))

  private def key(code: SpecialKeyCode, mods: Set[KeyModifier] = Set.empty): Event = SpecialKey(code, mods)

  private def typed(text: String): Seq[Event] = text.map(c => CharKey(c, Set.empty))

  private def press(t: TagInput, events: Event*): Seq[EventResult] =
    events.map(t.handleEvent(_, focusCtx(t)))

  /** Run a `Perform` result's effect against a real `Frame`. */
  private def perform(res: EventResult): UIO[Unit] =
    res match
      case EventResult.Perform(effect) => FrameHarness.make(1, 1).flatMap(h => effect.provideLayer(h.frameLayer)).orDie
      case _                           => ZIO.unit

  /** Row `y` of `buf` as one char per cell, blank for an empty cell. */
  private def row(buf: ScreenBuffer, y: Int): String =
    (0 until buf.width).map(x => buf.get(x, y).map(_.char).getOrElse(' ')).mkString

  private def hasAttr(buf: ScreenBuffer, x: Int, y: Int, a: Attribute): Boolean =
    buf.get(x, y).exists(_.style.attributes.contains(a))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TagInput")(
    suite("chip layout")(
      test("every chip fits: all shown, no count") {
        assertTrue(TagInput.layoutChips(Seq(5, 4), 20) == TagInput.ChipLayout(2, None, 0))
      },
      test("the chips that fit beside the count, then the count") {
        // " Alpha " (7) + gap + "+1" (2) = 10.
        assertTrue(TagInput.layoutChips(Seq(5, 4), 10) == TagInput.ChipLayout(1, None, 1))
      },
      test("when no whole chip fits, the first label is cut beside the count") {
        assertTrue(TagInput.layoutChips(Seq(7, 6), 6) == TagInput.ChipLayout(1, Some(1), 1))
      },
      test("a lone chip that does not fit is cut to the row") {
        assertTrue(TagInput.layoutChips(Seq(12), 6) == TagInput.ChipLayout(1, Some(4), 0))
      },
      test("only the count when not even one label cell fits") {
        assertTrue(TagInput.layoutChips(Seq(7, 6), 4) == TagInput.ChipLayout(0, None, 2))
      },
      test("nothing at all when not even the count fits") {
        assertTrue(
          TagInput.layoutChips(Seq.fill(10)(5), 2) == TagInput.ChipLayout(0, None, 0),
          TagInput.layoutChips(Seq.fill(10)(5), 3) == TagInput.ChipLayout(0, None, 10)
        )
      },
      test("no chips, nothing drawn") {
        assertTrue(TagInput.layoutChips(Seq.empty, 10) == TagInput.ChipLayout(0, None, 0))
      }
    ),
    suite("closed rendering")(
      test("shows the placeholder dimmed when nothing is chosen") {
        for t <- TagInput.make(options, placeholder = "Add tags")
        yield
          val buf = renderToBuffer(14, 3)(t)
          assertTrue(row(buf, 1) == "│Add tags   ▾│", hasAttr(buf, 1, 1, Attribute.Dim))
      },
      test("draws chips in the order chosen, reversed by default, one cell apart") {
        for t <- TagInput.make(options, selected = Seq(2, 0))
        yield
          val buf = renderToBuffer(20, 3)(t)
          assertTrue(
            row(buf, 1) == "│ Gamma   Alpha   ▾│",
            hasAttr(buf, 2, 1, Attribute.Reverse),
            !hasAttr(buf, 8, 1, Attribute.Reverse),
            hasAttr(buf, 10, 1, Attribute.Reverse)
          )
      },
      test("chips that do not fit give way to a dimmed +n count") {
        for t <- TagInput.make(options, selected = Seq(0, 1, 2))
        yield
          val buf = renderToBuffer(14, 3)(t)
          assertTrue(row(buf, 1) == "│ Alpha  +2 ▾│", hasAttr(buf, 9, 1, Attribute.Dim))
      },
      test("a first chip too wide for the row is cut by cells") {
        for t <- TagInput.make(Seq("Fantasy", "Horror"), selected = Seq(0, 1))
        yield assertTrue(row(renderToBuffer(10, 3)(t), 1) == "│ F  +1 ▾│")
      },
      test("CJK chips take two cells per glyph") {
        for t <- TagInput.make(Seq("東京", "大阪"), selected = Seq(0, 1))
        yield
          val buf = renderToBuffer(20, 3)(t)
          // " 東京 " spans cells 1-6, the gap 7, " 大阪 " from 8.
          assertTrue(
            buf.get(2, 1).map(_.text).contains("東"),
            buf.get(4, 1).map(_.text).contains("京"),
            buf.get(9, 1).map(_.text).contains("大")
          )
      },
      test("each chip takes its own style") {
        val red = CellStyle(attributes = Set(Attribute.Underline))
        for t <-
            TagInput.make(options, selected = Seq(0, 1), chipStyle = Some(i => if i == 0 then red else CellStyle.Empty))
        yield
          val buf = renderToBuffer(20, 3)(t)
          assertTrue(hasAttr(buf, 2, 1, Attribute.Underline), !hasAttr(buf, 10, 1, Attribute.Underline))
      },
      test("the seed drops out-of-range and repeated indices") {
        for t <- TagInput.make(options, selected = Seq(1, 7, 1, -1, 0))
        yield assertTrue(t.selected == Vector(1, 0))
      }
    ),
    suite("keys")(
      test("Enter adds the highlight, keeps the list open, and reports the chips") {
        for
          ref <- Ref.make(Seq.empty[Int])
          t   <- TagInput.make(options, onChange = s => ref.set(s))
          results = press(t, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Down), key(SpecialKeyCode.Enter))
          _        <- perform(results.last)
          reported <- ref.get
        yield assertTrue(t.selected == Vector(1), t.isOpen, reported == Seq(1), t.highlighted.contains(1))
      },
      test("Enter on a chosen option removes it") {
        for t <- TagInput.make(options, selected = Seq(0, 2))
        yield
          val last = press(t, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Enter)).last
          assertTrue(t.selected == Vector(2), last.isInstanceOf[EventResult.Perform])
      },
      test("adding after a filter clears the filter and keeps the added option highlighted") {
        for t <- TagInput.make(options)
        yield
          val _ = press(t, typed("gam")*)
          val _ = press(t, key(SpecialKeyCode.Enter))
          assertTrue(t.selected == Vector(2), t.filter.isEmpty, t.matches == Vector(0, 1, 2), t.highlighted.contains(2))
      },
      test("maxSelected refuses another chip but still allows removal") {
        for t <- TagInput.make(options, selected = Seq(0), maxSelected = Some(1))
        yield
          val refused = press(t, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Down), key(SpecialKeyCode.Enter)).last
          val chipsAtCap = t.selected
          val remainingAtCap = t.remaining
          val _ = press(t, key(SpecialKeyCode.Up), key(SpecialKeyCode.Enter))
          assertTrue(
            refused == EventResult.Consumed,
            chipsAtCap == Vector(0),
            remainingAtCap.contains(0),
            t.selected.isEmpty,
            t.remaining.contains(1)
          )
      },
      test("remaining is None without a cap") {
        for t <- TagInput.make(options)
        yield assertTrue(t.remaining.isEmpty)
      },
      test("Backspace deletes filter text first, then removes the last chip") {
        for t <- TagInput.make(options, selected = Seq(0, 1))
        yield
          val _ = press(t, typed("g")*)
          val _ = press(t, key(SpecialKeyCode.Backspace))
          val afterFilter = (t.filter, t.selected)
          val _ = press(t, key(SpecialKeyCode.Backspace))
          val afterChip = t.selected
          val _ = press(t, key(SpecialKeyCode.Backspace))
          val last = press(t, key(SpecialKeyCode.Backspace)).last
          assertTrue(
            afterFilter == ("", Vector(0, 1)),
            afterChip == Vector(0),
            t.selected.isEmpty,
            last == EventResult.Consumed
          )
      },
      test("Backspace on the closed field removes the last chip; with none it bubbles") {
        for
          t     <- TagInput.make(options, selected = Seq(2))
          empty <- TagInput.make(options)
        yield
          val removed = press(t, key(SpecialKeyCode.Backspace)).last
          assertTrue(
            removed.isInstanceOf[EventResult.Perform],
            t.selected.isEmpty,
            !t.isOpen,
            press(empty, key(SpecialKeyCode.Backspace)) == Seq(EventResult.Ignored)
          )
      },
      test("typing on the closed field opens the list filtered") {
        for t <- TagInput.make(options)
        yield
          val _ = press(t, typed("ET")*)
          assertTrue(t.isOpen, t.filter == "ET", t.matches == Vector(1))
      },
      test("Escape closes keeping the chips; Tab closes and bubbles") {
        for
          a <- TagInput.make(options, selected = Seq(1))
          b <- TagInput.make(options, selected = Seq(1))
        yield
          val esc = press(a, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Escape)).last
          val tab = press(b, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Tab)).last
          assertTrue(
            esc == EventResult.RequestRedraw,
            tab == EventResult.Ignored,
            !a.isOpen,
            !b.isOpen,
            a.selected == Vector(1),
            b.selected == Vector(1)
          )
      },
      test("while open, plain keys are consumed and Ctrl chords bubble") {
        for t <- TagInput.make(options)
        yield
          val _ = press(t, key(SpecialKeyCode.Enter))
          assertTrue(
            press(t, key(SpecialKeyCode.Left)) == Seq(EventResult.Consumed),
            press(t, CharKey('c', Set(KeyModifier.Ctrl))) == Seq(EventResult.Ignored)
          )
      },
      test("disabled: not focusable, ignores keys") {
        for t <- TagInput.make(options, enabled = false)
        yield assertTrue(!t.focusable, press(t, key(SpecialKeyCode.Enter)) == Seq(EventResult.Ignored))
      }
    ),
    suite("popup")(
      test("marks chosen rows and mutes the rows that can no longer be added") {
        for
          t <- TagInput.make(options, selected = Seq(0), maxSelected = Some(1))
          _ = press(t, key(SpecialKeyCode.Enter))
          h      <- FrameHarness.make(20, 10)
          layout <- h.renderTree(VBox(Constraint.Fit -> t, Constraint.Fill -> Spacer), focusCtx(t))
        yield
          val buf = h.drawnBuffer
          assertTrue(
            layout.popups.map(_._2) == Vector(Rect(0, 3, 20, 5)),
            row(buf, 4).startsWith("│ ☑ Alpha"),
            row(buf, 5).startsWith("│ ☐ Beta"),
            hasAttr(buf, 4, 4, Attribute.Bold),
            hasAttr(buf, 4, 5, Attribute.Dim),
            hasAttr(buf, 4, 4, Attribute.Reverse)
          )
      },
      test("the field shows the filter with a caret while typing") {
        for t <- TagInput.make(options, selected = Seq(0))
        yield
          val _ = press(t, typed("be")*)
          val buf = renderToBuffer(14, 3)(t, ctx = focusCtx(t))
          assertTrue(row(buf, 1) == "│be         ▴│", hasAttr(buf, 3, 1, Attribute.Reverse))
      }
    )
  )
