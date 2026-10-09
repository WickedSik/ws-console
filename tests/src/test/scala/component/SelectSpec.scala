package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, ScreenBuffer}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import layout.Constraint
import render.{EventDispatcher, FocusManager, LayoutManager, Renderer}
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.util.concurrent.atomic.AtomicInteger

object SelectSpec extends ZIOSpecDefault:

  private val options = Seq("Alpha", "Beta", "Gamma")

  private def focusCtx(c: Component): RenderContext = RenderContext(FocusSnapshot(Some(c.id)))

  private def key(code: SpecialKeyCode, mods: Set[KeyModifier] = Set.empty): Event = SpecialKey(code, mods)

  private def press(s: Select, events: Event*): Seq[EventResult] =
    events.map(s.handleEvent(_, focusCtx(s)))

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_) => true
      case _                      => false

  /** Row `y` of `buf` as one char per cell, blank for an empty cell. */
  private def row(buf: ScreenBuffer, y: Int): String =
    (0 until buf.width).map(x => buf.get(x, y).map(_.char).getOrElse(' ')).mkString

  private def text(buf: ScreenBuffer, x: Int, y: Int): Option[String] = buf.get(x, y).map(_.text)

  private def hasAttr(buf: ScreenBuffer, x: Int, y: Int, a: Attribute): Boolean =
    buf.get(x, y).exists(_.style.attributes.contains(a))

  /** One frame of the real pipeline with `focus` focused: the drawn buffer and its layout. */
  private def frame(root: Component, width: Int, height: Int, focus: Component) =
    for
      h      <- FrameHarness.make(width, height)
      layout <- Renderer.default.renderFull(root, focusCtx(focus)).provideLayer(h.frameLayer)
    yield (h.drawnBuffer, layout)

  /** Passes every event it sees, counting them. */
  final private class Recorder(child: Component) extends Component:
    val seen = new AtomicInteger(0)
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = child.render(area, canvas, ctx)
    override def childLayouts(area: Rect): Seq[(Component, Rect)] = Seq(child -> area)
    override def handleEvent(event: Event, ctx: RenderContext): EventResult =
      seen.incrementAndGet()
      EventResult.Consumed

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Select")(
    suite("closed rendering")(
      test("shows the placeholder dimmed and the chevron at the right edge") {
        for s <- Select.make(options, placeholder = "Pick")
        yield
          val buf = renderToBuffer(12, 3)(s)
          assertTrue(
            row(buf, 1) == "│Pick     ▾│",
            hasAttr(buf, 1, 1, Attribute.Dim),
            text(buf, 0, 0).contains(BoxStyle.Single.topLeft.toString),
            s.preferredHeight(12).contains(3)
          )
      },
      test("shows the selected option's label") {
        for s <- Select.make(options, selected = Some(1), placeholder = "Pick")
        yield assertTrue(row(renderToBuffer(12, 3)(s), 1) == "│Beta     ▾│")
      },
      test("an out-of-range seed selects nothing") {
        for s <- Select.make(options, selected = Some(7))
        yield assertTrue(s.selected.isEmpty)
      },
      test("truncates a long label by cells, never splitting a wide glyph, and keeps the chevron") {
        for s <- Select.make(Seq("中文中文中文"), selected = Some(0))
        yield
          // Inner width 10, label width 8: four two-cell glyphs.
          val buf = renderToBuffer(12, 3)(s)
          assertTrue(
            text(buf, 1, 1).contains("中"),
            text(buf, 7, 1).contains("文"),
            text(buf, 9, 1).forall(_.isBlank),
            text(buf, 10, 1).contains(Select.ClosedChevron)
          )
      },
      test("Cyrillic labels truncate on the label width") {
        for s <- Select.make(Seq("Привет, мир"), selected = Some(0))
        yield assertTrue(row(renderToBuffer(12, 3)(s), 1) == "│Привет,  ▾│")
      },
      test("focused renders bold; disabled renders dim and leaves the focus cycle") {
        for
          s <- Select.make(options)
          d <- Select.make(options, enabled = false)
        yield
          val focused = renderToBuffer(12, 3)(s, ctx = focusCtx(s))
          val disabled = renderToBuffer(12, 3)(d)
          assertTrue(
            hasAttr(focused, 0, 0, Attribute.Bold),
            hasAttr(disabled, 0, 0, Attribute.Dim),
            !d.focusable,
            d.handleEvent(key(SpecialKeyCode.Enter), focusCtx(d)) == EventResult.Ignored
          )
      },
      test("an empty option list is not focusable") {
        for s <- Select.make(Seq.empty)
        yield assertTrue(!s.focusable)
      }
    ),
    suite("keys")(
      test("Enter, Space and Down each open the list; Up does not") {
        for
          a <- Select.make(options)
          b <- Select.make(options)
          c <- Select.make(options)
          d <- Select.make(options)
        yield
          val results = Seq(
            press(a, key(SpecialKeyCode.Enter)),
            press(b, CharKey(' ', Set.empty)),
            press(c, key(SpecialKeyCode.Down))
          ).flatten
          assertTrue(
            results.forall(_ == EventResult.RequestRedraw),
            a.isOpen,
            b.isOpen,
            c.isOpen,
            press(d, key(SpecialKeyCode.Up)) == Seq(EventResult.Ignored),
            !d.isOpen
          )
      },
      test("opening highlights the selected option, or the first when none is") {
        for
          none <- Select.make(options)
          some <- Select.make(options, selected = Some(2))
        yield
          press(none, key(SpecialKeyCode.Enter))
          press(some, key(SpecialKeyCode.Enter))
          assertTrue(none.highlighted == 0, some.highlighted == 2)
      },
      test("the highlight moves and stops at both ends without wrapping, still consuming") {
        for s <- Select.make(options)
        yield
          val atTop = press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Up)).last
          val down = press(s, key(SpecialKeyCode.Down), key(SpecialKeyCode.Down))
          val atBottom = press(s, key(SpecialKeyCode.Down)).last
          val home = press(s, key(SpecialKeyCode.Home)).last
          val highlightAfterHome = s.highlighted
          press(s, key(SpecialKeyCode.End))
          assertTrue(
            atTop == EventResult.Consumed,
            down.forall(_ == EventResult.RequestRedraw),
            atBottom == EventResult.Consumed,
            home == EventResult.RequestRedraw,
            highlightAfterHome == 0,
            s.highlighted == 2
          )
      },
      test("PgDn and PgUp move by the page, clamped") {
        val many = (1 to 30).map(i => s"Option $i")
        for s <- Select.make(many, maxVisible = 5)
        yield
          press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.PgDn))
          val afterOne = s.highlighted
          press(s, Seq.fill(10)(key(SpecialKeyCode.PgDn))*)
          val afterMany = s.highlighted
          press(s, key(SpecialKeyCode.PgUp))
          assertTrue(afterOne == 5, afterMany == 29, s.highlighted == 24)
      },
      test("Enter commits the highlight, closes, and reports it") {
        for
          ref <- Ref.make(Option.empty[Int])
          s   <- Select.make(options, onSelect = i => ref.set(Some(i)))
          results = press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Down), key(SpecialKeyCode.Enter))
          h <- FrameHarness.make(1, 1)
          _ <- results.last match
                 case EventResult.Perform(effect) => effect.provideLayer(h.frameLayer)
                 case _                           => ZIO.unit
          reported <- ref.get
        yield assertTrue(isPerform(results.last), !s.isOpen, s.selected.contains(1), reported.contains(1))
      },
      test("re-selecting the current option fires nothing") {
        for s <- Select.make(options, selected = Some(0))
        yield assertTrue(
          press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Enter)).last == EventResult.RequestRedraw,
          !s.isOpen
        )
      },
      test("Escape closes and leaves the selection untouched") {
        for s <- Select.make(options, selected = Some(0))
        yield
          val last = press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Down), key(SpecialKeyCode.Escape)).last
          assertTrue(last == EventResult.RequestRedraw, !s.isOpen, s.selected.contains(0))
      },
      test("Tab and Shift+Tab close without committing and bubble") {
        for
          a <- Select.make(options, selected = Some(0))
          b <- Select.make(options, selected = Some(0))
        yield
          val tab = press(a, key(SpecialKeyCode.Enter), key(SpecialKeyCode.Down), key(SpecialKeyCode.Tab)).last
          val back = press(
            b,
            key(SpecialKeyCode.Enter),
            key(SpecialKeyCode.Down),
            key(SpecialKeyCode.Tab, Set(KeyModifier.Shift))
          ).last
          assertTrue(
            tab == EventResult.Ignored,
            back == EventResult.Ignored,
            !a.isOpen,
            !b.isOpen,
            a.selected.contains(0),
            b.selected.contains(0)
          )
      },
      test("while open, plain keys are consumed and Ctrl chords bubble") {
        for s <- Select.make(options)
        yield
          press(s, key(SpecialKeyCode.Enter))
          assertTrue(
            press(s, CharKey('q', Set.empty)) == Seq(EventResult.Consumed),
            press(s, key(SpecialKeyCode.Left)) == Seq(EventResult.Consumed),
            press(s, CharKey('c', Set(KeyModifier.Ctrl))) == Seq(EventResult.Ignored),
            press(s, key(SpecialKeyCode.Down, Set(KeyModifier.Ctrl))) == Seq(EventResult.Ignored),
            s.isOpen
          )
      },
      test("an unfocused select ignores every key") {
        for s <- Select.make(options)
        yield assertTrue(s.handleEvent(key(SpecialKeyCode.Enter), RenderContext.empty) == EventResult.Ignored)
      },
      test("losing focus closes the list") {
        for s <- Select.make(options)
        yield
          press(s, key(SpecialKeyCode.Enter))
          val wasOpen = s.isOpen
          renderToBuffer(12, 3)(s, ctx = RenderContext.empty)
          assertTrue(wasOpen, !s.isOpen)
      }
    ),
    suite("dispatch")(
      test("shell keys do not reach an ancestor while open; Ctrl+C does") {
        for
          s <- Select.make(options)
          shell = new Recorder(VBox(Constraint.Fit -> s))
          layout = LayoutManager.default.resolve(shell, Rect(0, 0, 20, 10))
          fm <- FocusManager.make
          _  <- fm.setOrder(layout.focusOrder)
          _  <- fm.focus(s.id)
          d = EventDispatcher.make(fm)
          ctx = focusCtx(s)
          _          <- d.dispatch(key(SpecialKeyCode.Enter), layout, shell, ctx)
          q          <- d.dispatch(CharKey('q', Set.empty), layout, shell, ctx)
          seenAfterQ = shell.seen.get()
          _          <- d.dispatch(CharKey('c', Set(KeyModifier.Ctrl)), layout, shell, ctx)
        yield assertTrue(q == EventResult.Consumed, seenAfterQ == 0, shell.seen.get() == 1, s.isOpen)
      }
    ),
    suite("popup")(
      test("opens below the field, as wide as the field, highlight reversed") {
        for
          s <- Select.make(options)
          _ = press(s, key(SpecialKeyCode.Enter))
          drawn <- frame(VBox(Constraint.Fit -> s, Constraint.Fill -> Spacer), 20, 10, s)
          (buf, layout) = drawn
        yield assertTrue(
          layout.popups.map(_._2) == Vector(Rect(0, 3, 20, 5)),
          row(buf, 4).startsWith("│ Alpha"),
          row(buf, 6).startsWith("│ Gamma"),
          hasAttr(buf, 2, 4, Attribute.Reverse),
          !hasAttr(buf, 2, 5, Attribute.Reverse),
          text(buf, 18, 1).contains(Select.OpenChevron)
        )
      },
      test("opens above the field when there is no room below") {
        for
          s <- Select.make(options)
          _ = press(s, key(SpecialKeyCode.Enter))
          drawn <- frame(VBox(Constraint.Fill -> Spacer, Constraint.Fit -> s), 20, 10, s)
          (buf, layout) = drawn
        yield assertTrue(
          layout.popups.map(_._2) == Vector(Rect(0, 2, 20, 5)),
          row(buf, 3).startsWith("│ Alpha")
        )
      },
      test("grows wider than the field for a long label") {
        for
          s <- Select.make(Seq("Short", "A much longer one"))
          _ = press(s, key(SpecialKeyCode.Enter))
          root = VBox(Constraint.Fit -> HBox(Constraint.Fixed(10) -> s, Constraint.Fill -> Spacer))
          drawn <- frame(root, 30, 10, s)
          (_, layout) = drawn
        yield
          // 17 cells of label, a space each side, two border columns.
          assertTrue(layout.popups.map(_._2) == Vector(Rect(0, 3, 21, 4)))
      },
      test("caps the height and scrolls to keep the highlight in view") {
        val many = (1 to 30).map(i => s"Option $i")
        for
          s <- Select.make(many, maxVisible = 5)
          _ = press(s, key(SpecialKeyCode.Enter), key(SpecialKeyCode.End))
          drawn <- frame(VBox(Constraint.Fit -> s, Constraint.Fill -> Spacer), 20, 12, s)
          (buf, layout) = drawn
        yield assertTrue(
          layout.popups.map(_._2) == Vector(Rect(0, 3, 20, 7)),
          row(buf, 4).startsWith("│ Option 26"),
          row(buf, 8).startsWith("│ Option 30"),
          hasAttr(buf, 2, 8, Attribute.Reverse),
          text(buf, 18, 8).contains(ScrollView.ThumbGlyph.toString)
        )
      },
      test("a short list has no scrollbar column") {
        for
          s <- Select.make(options)
          _ = press(s, key(SpecialKeyCode.Enter))
          drawn <- frame(VBox(Constraint.Fit -> s, Constraint.Fill -> Spacer), 20, 10, s)
          (buf, _) = drawn
        yield assertTrue(row(buf, 5) == "│ Beta             │")
      },
      test("is not clipped by an enclosing ScrollView") {
        for
          s    <- Select.make(options)
          view <- ScrollView.make(VBox(Constraint.Fit -> s, Constraint.Fixed(10) -> Spacer))
          _ = press(s, key(SpecialKeyCode.Enter))
          drawn <- frame(VBox(Constraint.Fixed(4) -> view, Constraint.Fill -> Spacer), 20, 10, s)
          (buf, _) = drawn
        yield
          // The viewport ends at row 3; the options sit on rows 4 to 6.
          assertTrue(row(buf, 4).startsWith("│ Alpha"), row(buf, 6).startsWith("│ Gamma"))
      },
      test("a closed select draws no popup") {
        for
          s <- Select.make(options)
          drawn <- frame(VBox(Constraint.Fit -> s, Constraint.Fill -> Spacer), 20, 10, s)
          (_, layout) = drawn
        yield assertTrue(layout.popups.isEmpty)
      }
    )
  )
