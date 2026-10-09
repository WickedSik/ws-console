package io.github.wickedsik.wsconsole
package component

import buffer.{BoxStyle, Canvas, Cell}
import event.{EventResult, KeyEvent, KeyModifier, SpecialKeyCode}
import event.KeyEvent.SpecialKey
import geometry.Rect
import layout.Constraint
import render.{EventDispatcher, FocusManager, LayoutManager}
import testkit.RenderHarness

// Project imports precede zio.test.*, whose `render` would otherwise shadow the package.
import zio.Scope
import zio.test.*

object ScrollViewSpec extends ZIOSpecDefault:

  private val ctx = RenderContext.empty

  /** `n` one-row text lines labelled `r0`, `r1`, ... */
  private def lines(n: Int): VBox =
    VBox((0 until n).map(i => (Constraint.Fixed(1): Constraint) -> (Text(s"r$i"): Component))*)

  /** A focusable leaf, for dispatch through the bubble chain. */
  final private class Leaf extends Component:
    override val focusable: Boolean = true
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  private def key(code: SpecialKeyCode, mods: Set[KeyModifier] = Set.empty): KeyEvent = SpecialKey(code, mods)
  private val ctrl = Set(KeyModifier.Ctrl)

  /** Lay `view` out in `area` (the frame does this before any key arrives). */
  private def layOut(view: Component, area: Rect): Unit = view.childLayouts(area): Unit

  private def text(buf: buffer.ScreenBuffer, x: Int, y: Int): Option[String] = buf.get(x, y).map(_.text)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ScrollView")(
    suite("layout")(
      test("lays the child out one column narrow, at its preferred height, shifted by the offset") {
        for
          view <- ScrollView.make(lines(20))
          area = Rect(2, 3, 10, 5)
          _ = layOut(view, area)
          _ = view.scrollTo(4)
        yield assertTrue(view.childLayouts(area).map(_._2) == Seq(Rect(2, -1, 9, 20)))
      },
      test("short content is laid out at the viewport height") {
        for view <- ScrollView.make(lines(2))
        yield assertTrue(view.childLayouts(Rect(0, 0, 10, 6)).map(_._2) == Seq(Rect(0, 0, 9, 6)))
      },
      test("a child with no preference fills the viewport") {
        for view <- ScrollView.make(Spacer)
        yield assertTrue(view.childLayouts(Rect(0, 0, 10, 6)).map(_._2) == Seq(Rect(0, 0, 9, 6)))
      },
      test("has no preferred height of its own") {
        for view <- ScrollView.make(lines(20))
        yield assertTrue(view.preferredHeight(10).isEmpty)
      },
      test("offscreen focusables keep full-size rects, so they stay in the focus order") {
        val leaves = Vector.fill(10)(new Leaf)
        val box = VBox(leaves.map(l => (Constraint.Fixed(1): Constraint) -> (l: Component))*)
        for view <- ScrollView.make(box)
        yield
          val result = LayoutManager.default.resolve(view, Rect(0, 0, 10, 3))
          assertTrue(
            result.focusOrder.ids == leaves.map(_.id),
            result.rects(leaves.last.id) == Rect(0, 9, 9, 1)
          )
      }
    ),
    suite("offset clamping")(
      test("clamps at both ends") {
        for
          view <- ScrollView.make(lines(20))
          area = Rect(0, 0, 10, 5)
          _ = layOut(view, area)
          _ = view.scrollTo(-3)
          low = view.offset
          _ = view.scrollTo(99)
          high = view.offset
        yield assertTrue(low == 0, high == 15)
      },
      test("re-clamps when the viewport grows (resize)") {
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = view.scrollTo(15)
          _ = layOut(view, Rect(0, 0, 10, 12))
        yield assertTrue(view.offset == 8)
      },
      test("returns to zero when the content shrinks to fit") {
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = view.scrollTo(10)
          _ = layOut(view, Rect(0, 0, 10, 30))
        yield assertTrue(view.offset == 0)
      }
    ),
    suite("clipping")(
      test("shows only the rows inside the viewport") {
        for
          view <- ScrollView.make(lines(20))
          area = Rect(0, 1, 10, 3)
          _ = layOut(view, area)
          _ = view.scrollTo(5)
          buf = RenderHarness.renderToBuffer(10, 6)(view, area)
        yield assertTrue(
          buf.get(0, 0).contains(Cell.Empty),
          text(buf, 0, 1).contains("r") && text(buf, 1, 1).contains("5"),
          text(buf, 1, 3).contains("7"),
          buf.get(0, 4).contains(Cell.Empty)
        )
      },
      test("a bordered child straddling the top edge is cut, not shifted") {
        val content = VBox(
          Constraint.Fixed(1)  -> Text("top"),
          Constraint.Fixed(4)  -> Panel(child = Text("in")),
          Constraint.Fixed(10) -> Spacer
        )
        for
          view <- ScrollView.make(content)
          area = Rect(0, 0, 10, 5)
          _ = layOut(view, area)
          _ = view.scrollTo(2) // panel spans content rows 1..4 → viewport rows -1..2
          buf = RenderHarness.renderToBuffer(10, 5)(view, area)
        yield assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.midLeft),
          text(buf, 1, 0).contains("i"),
          buf.get(0, 1).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(0, 2).map(_.char).contains(BoxStyle.Single.bottomLeft),
          buf.get(8, 2).map(_.char).contains(BoxStyle.Single.bottomRight)
        )
      },
      test("a bordered child straddling the bottom edge is cut and nothing leaks below") {
        val content = VBox(
          Constraint.Fixed(3)  -> Spacer,
          Constraint.Fixed(4)  -> Panel(child = Text("in")),
          Constraint.Fixed(10) -> Spacer
        )
        for
          view <- ScrollView.make(content)
          area = Rect(0, 0, 10, 5)
          buf = RenderHarness.renderToBuffer(10, 8)(view, area)
        yield assertTrue(
          buf.get(0, 3).map(_.char).contains(BoxStyle.Single.topLeft),
          buf.get(0, 4).map(_.char).contains(BoxStyle.Single.midLeft),
          (0 until 10).forall(x => buf.get(x, 5).contains(Cell.Empty)),
          (0 until 10).forall(x => buf.get(x, 6).contains(Cell.Empty))
        )
      },
      test("short content fills the viewport: a bordered child closes at its bottom") {
        for
          view <- ScrollView.make(Panel(child = Text("x")))
          buf = RenderHarness.renderToBuffer(10, 6)(view)
        yield assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
          buf.get(0, 5).map(_.char).contains(BoxStyle.Single.bottomLeft),
          buf.get(8, 5).map(_.char).contains(BoxStyle.Single.bottomRight)
        )
      },
      test("CJK is clipped on whole glyphs at the scrollbar column") {
        for
          view <- ScrollView.make(VBox(Constraint.Fixed(1) -> Text("中文中文")))
          buf = RenderHarness.renderToBuffer(6, 2)(view)
        yield assertTrue(
          text(buf, 0, 0).contains("中"),
          text(buf, 2, 0).contains("文"),
          buf.get(4, 0).contains(Cell.Empty),
          buf.get(5, 0).map(_.char).contains(ScrollView.ThumbGlyph)
        )
      },
      test("Cyrillic rows scrolled above the viewport disappear whole") {
        val content = VBox(
          Constraint.Fixed(1) -> Text("Привет"),
          Constraint.Fixed(1) -> Text("Мир"),
          Constraint.Fixed(5) -> Spacer
        )
        for
          view <- ScrollView.make(content)
          area = Rect(0, 0, 8, 3)
          _ = layOut(view, area)
          _ = view.scrollTo(1)
          buf = RenderHarness.renderToBuffer(8, 3)(view, area)
        yield assertTrue(
          text(buf, 0, 0).contains("М"),
          text(buf, 2, 0).contains("р")
        )
      }
    ),
    suite("keepInView")(
      test("applies on the first layout, scrolling the least that shows the row") {
        for view <- ScrollView.make(lines(30))
        yield
          view.keepInView(20)
          layOut(view, Rect(0, 0, 10, 5))
          // Row 20 becomes the last visible row: 20 + 1 - 5.
          assertTrue(view.offset == 16)
      },
      test("scrolls up to a row above, and leaves a visible row alone") {
        for view <- ScrollView.make(lines(30))
        yield
          val area = Rect(0, 0, 10, 5)
          view.keepInView(20)
          layOut(view, area)
          view.keepInView(3)
          layOut(view, area)
          val afterUp = view.offset
          view.keepInView(5)
          layOut(view, area)
          assertTrue(afterUp == 3, view.offset == 3)
      },
      test("is consumed by one layout") {
        for view <- ScrollView.make(lines(30))
        yield
          val area = Rect(0, 0, 10, 5)
          view.keepInView(20)
          layOut(view, area)
          view.scrollTo(0)
          layOut(view, area)
          assertTrue(view.offset == 0)
      },
      test("rows past the content are clamped; more rows than fit show their top") {
        for
          a <- ScrollView.make(lines(30))
          b <- ScrollView.make(lines(30))
        yield
          val area = Rect(0, 0, 10, 5)
          a.keepInView(29, rows = 5)
          layOut(a, area)
          b.keepInView(10, rows = 8)
          layOut(b, area)
          assertTrue(a.offset == 25, b.offset == 10)
      }
    ),
    suite("scrollbar")(
      test("the thumb spans the whole track when the content fits") {
        for
          view <- ScrollView.make(lines(2))
          buf = RenderHarness.renderToBuffer(10, 4)(view)
        yield assertTrue((0 until 4).forall(y => buf.get(9, y).map(_.char).contains(ScrollView.ThumbGlyph)))
      },
      test("thumb size is proportional to viewport / content") {
        assertTrue(
          ScrollView.thumb(view = 10, content = 20, offset = 0) == (0, 5),
          ScrollView.thumb(view = 5, content = 100, offset = 0) == (0, 1),
          ScrollView.thumb(view = 5, content = 5, offset = 0) == (0, 5)
        )
      },
      test("thumb position at top, middle, and bottom") {
        // view 10, content 40 → thumb 3 rows, travel 7, max offset 30.
        assertTrue(
          ScrollView.thumb(10, 40, 0) == (0, 3),
          ScrollView.thumb(10, 40, 15) == (4, 3),
          ScrollView.thumb(10, 40, 30) == (7, 3),
          // One row from either end is never drawn as touching it.
          ScrollView.thumb(10, 40, 1)._1 == 1,
          ScrollView.thumb(10, 40, 29)._1 == 6
        )
      },
      test("renders track and thumb in the right-hand column") {
        for
          view <- ScrollView.make(lines(20))
          area = Rect(0, 0, 10, 5)
          _ = layOut(view, area)
          _ = view.scrollTo(15)
          buf = RenderHarness.renderToBuffer(10, 5)(view, area)
        yield assertTrue(
          (0 until 4).forall(y => buf.get(9, y).map(_.char).contains(ScrollView.TrackGlyph)),
          buf.get(9, 4).map(_.char).contains(ScrollView.ThumbGlyph)
        )
      }
    ),
    suite("keys")(
      test("Ctrl+PgDn / Ctrl+PgUp move by the viewport height minus one") {
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          down = view.handleEvent(key(SpecialKeyCode.PgDn, ctrl), ctx)
          afterDown = view.offset
          up = view.handleEvent(key(SpecialKeyCode.PgUp, ctrl), ctx)
        yield assertTrue(
          down == EventResult.RequestRedraw,
          afterDown == 4,
          up == EventResult.RequestRedraw,
          view.offset == 0
        )
      },
      test("Ctrl+End / Ctrl+Home jump to the ends") {
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = view.handleEvent(key(SpecialKeyCode.End, ctrl), ctx)
          end = view.offset
          _ = view.handleEvent(key(SpecialKeyCode.Home, ctrl), ctx)
        yield assertTrue(end == 15, view.offset == 0)
      },
      test("bare scroll keys are left to the focused control; only Ctrl alone scrolls") {
        val codes = Seq(
          SpecialKeyCode.PgUp,
          SpecialKeyCode.PgDn,
          SpecialKeyCode.Up,
          SpecialKeyCode.Down,
          SpecialKeyCode.Home,
          SpecialKeyCode.End
        )
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = view.scrollTo(7)
          bare = codes.map(c => view.handleEvent(key(c), ctx))
          chorded = codes.map(c => view.handleEvent(key(c, Set(KeyModifier.Ctrl, KeyModifier.Shift)), ctx))
        yield assertTrue(
          bare.forall(_ == EventResult.Ignored),
          chorded.forall(_ == EventResult.Ignored),
          view.offset == 7
        )
      },
      test("Ctrl+Up / Ctrl+Down move by one row") {
        for
          view <- ScrollView.make(lines(20))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = view.handleEvent(key(SpecialKeyCode.Down, ctrl), ctx)
          _ = view.handleEvent(key(SpecialKeyCode.Down, ctrl), ctx)
          _ = view.handleEvent(key(SpecialKeyCode.Up, ctrl), ctx)
        yield assertTrue(view.offset == 1)
      },
      test("a key that cannot move the view is Ignored") {
        for
          view <- ScrollView.make(lines(20))
          fits <- ScrollView.make(lines(2))
          _ = layOut(view, Rect(0, 0, 10, 5))
          _ = layOut(fits, Rect(0, 0, 10, 5))
        yield assertTrue(
          view.handleEvent(key(SpecialKeyCode.PgUp, ctrl), ctx) == EventResult.Ignored,
          view.handleEvent(key(SpecialKeyCode.Home, ctrl), ctx) == EventResult.Ignored,
          fits.handleEvent(key(SpecialKeyCode.PgDn, ctrl), ctx) == EventResult.Ignored,
          fits.handleEvent(key(SpecialKeyCode.End, ctrl), ctx) == EventResult.Ignored
        )
      },
      test("Ctrl+Home bubbles past a focused TextInput to the view") {
        for
          input <- TextInput.make(value = "abc")
          view  <- ScrollView.make(VBox(Constraint.Fixed(3) -> input, Constraint.Fixed(20) -> Spacer))
          tree = view
          layout = LayoutManager.default.resolve(tree, Rect(0, 0, 10, 5))
          _ = view.scrollTo(10)
          fm      <- FocusManager.make
          _       <- fm.setOrder(layout.focusOrder)
          _       <- fm.focus(input.id)
          focused <- fm.focused
          result <- EventDispatcher
                      .make(fm)
                      .dispatch(key(SpecialKeyCode.Home, ctrl), layout, tree, ctx.copy(focus = FocusSnapshot(focused)))
        yield assertTrue(result == EventResult.RequestRedraw, view.offset == 0, input.caret == 3)
      },
      test("Ctrl+Down bubbles past a focused RadioGroup to the view") {
        for
          radios <- RadioGroup.make(Seq("a", "b", "c"))
          view   <- ScrollView.make(VBox(Constraint.Fit -> radios, Constraint.Fixed(20) -> Spacer))
          layout = LayoutManager.default.resolve(view, Rect(0, 0, 10, 5))
          fm      <- FocusManager.make
          _       <- fm.setOrder(layout.focusOrder)
          _       <- fm.focus(radios.id)
          focused <- fm.focused
          result <- EventDispatcher
                      .make(fm)
                      .dispatch(key(SpecialKeyCode.Down, ctrl), layout, view, ctx.copy(focus = FocusSnapshot(focused)))
        yield assertTrue(result == EventResult.RequestRedraw, view.offset == 1, radios.selected == 0)
      },
      test("nested views: the innermost that can scroll consumes the key") {
        val leaf = new Leaf
        for
          inner <- ScrollView.make(VBox(Constraint.Fixed(1) -> leaf, Constraint.Fixed(9) -> Spacer))
          outer <- ScrollView.make(VBox(Constraint.Fixed(4) -> inner, Constraint.Fixed(20) -> Spacer))
          layout = LayoutManager.default.resolve(outer, Rect(0, 0, 12, 6))
          fm      <- FocusManager.make
          _       <- fm.setOrder(layout.focusOrder)
          _       <- fm.focus(leaf.id)
          focused <- fm.focused
          focusCtx = ctx.copy(focus = FocusSnapshot(focused))
          d = EventDispatcher.make(fm)
          _ <- d.dispatch(key(SpecialKeyCode.PgDn, ctrl), layout, outer, focusCtx)
          innerAfterFirst = inner.offset
          outerAfterFirst = outer.offset
          _ <- d.dispatch(key(SpecialKeyCode.End, ctrl), layout, outer, focusCtx)
          _ <- d.dispatch(key(SpecialKeyCode.PgDn, ctrl), layout, outer, focusCtx)
        yield assertTrue(
          // Inner viewport 4 rows → page 3; the outer stays put.
          innerAfterFirst == 3,
          outerAfterFirst == 0,
          // Once the inner is at its end, Ctrl+PgDn falls through to the outer.
          inner.offset == 6,
          outer.offset == 5
        )
      }
    ),
    suite("focus stop")(
      test("is not focusable by default; opts in for read-only content") {
        for
          plain <- ScrollView.make(lines(3))
          stop  <- ScrollView.make(lines(3), focusable = true)
        yield assertTrue(!plain.focusable, stop.focusable)
      }
    )
  )
