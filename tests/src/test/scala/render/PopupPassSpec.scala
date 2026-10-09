package io.github.wickedsik.wsconsole
package render

import buffer.{Canvas, Cell, ScreenBuffer}
import component.{Component, FocusSnapshot, Popup, RenderContext, ScrollView, Spacer, VBox}
import geometry.Rect
import layout.Constraint
import testkit.FrameHarness
import testkit.RenderHarness.*

import zio.{Scope, ZIO}
import zio.test.*

object PopupPassSpec extends ZIOSpecDefault:

  /** Fills its area with `glyph`. */
  final private class Fill(glyph: Char, override val focusable: Boolean = false) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      canvas.fillRect(area, Cell(glyph))

  /**
   * A focusable component that, while `open`, asks for one `width ×
   * height` popup of `content` anchored to its own area. `child`, when
   * given, fills the owner's area.
   */
  final private class Owner(
    content: Component,
    width: Int,
    height: Int,
    child: Option[Component] = None,
    glyph: Char = 'o'
  ) extends Component:
    override val focusable: Boolean = true
    var open: Boolean = true

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      canvas.fillRect(area, Cell(glyph))
      child.foreach(_.render(area, canvas, ctx))

    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      child.map(_ -> area).toSeq

    override def popups(area: Rect): Seq[Popup] =
      if open then Seq(Popup(content, area, width, height)) else Seq.empty

  /** `owner` on the top row of `area`, `rest` filling the rows below it. */
  private def topRow(owner: Component, rest: Component = Spacer): VBox =
    VBox(Constraint.Fixed(1) -> owner, Constraint.Fill -> rest)

  private def pass(root: Component, area: Rect, focused: Option[Component]): LayoutResult =
    PopupPass(LayoutManager.default.resolve(root, area), area, focused.map(_.id), LayoutManager.default)

  private def focusOn(c: Component): RenderContext = RenderContext(FocusSnapshot(Some(c.id)))

  /** Run one frame of the real pipeline and return the drawn buffer. */
  private def drawn(root: Component, width: Int, height: Int, ctx: RenderContext): ZIO[Any, Any, ScreenBuffer] =
    for
      h <- FrameHarness.make(width, height)
      _ <- h.renderTree(root, ctx)
    yield h.drawnBuffer

  def spec: Spec[TestEnvironment & Scope, Any] = suite("PopupPass")(
    suite("place")(
      test("goes below the anchor when the height fits there") {
        assertTrue(PopupPass.place(Rect(2, 1, 5, 1), 8, 3, Rect(0, 0, 20, 10)) == Rect(2, 2, 8, 3))
      },
      test("flips above when only the space above fits") {
        assertTrue(PopupPass.place(Rect(2, 8, 5, 1), 8, 3, Rect(0, 0, 20, 10)) == Rect(2, 5, 8, 3))
      },
      test("takes the larger side, cut to its rows, when neither fits") {
        // 5 rows below, 4 above.
        assertTrue(PopupPass.place(Rect(0, 4, 5, 1), 8, 8, Rect(0, 0, 20, 10)) == Rect(0, 5, 8, 5))
      },
      test("a tie between the sides goes below") {
        // 4 rows below, 4 above.
        assertTrue(PopupPass.place(Rect(0, 4, 5, 2), 8, 6, Rect(0, 0, 20, 10)) == Rect(0, 6, 8, 4))
      },
      test("shifts left until it fits at the right edge") {
        assertTrue(PopupPass.place(Rect(15, 0, 5, 1), 8, 2, Rect(0, 0, 20, 10)) == Rect(12, 1, 8, 2))
      },
      test("is clamped to the bounds' width") {
        assertTrue(PopupPass.place(Rect(5, 0, 5, 1), 30, 2, Rect(0, 0, 20, 10)) == Rect(0, 1, 20, 2))
      },
      test("is empty when the anchor fills the bounds") {
        assertTrue(PopupPass.place(Rect(0, 0, 20, 10), 8, 2, Rect(0, 0, 20, 10)).isEmpty)
      }
    ),
    suite("collection")(
      test("an unfocused owner shows no popup") {
        val owner = new Owner(Spacer, 10, 3)
        val other = new Fill('x', focusable = true)
        val root = topRow(owner, other)
        val area = Rect(0, 0, 20, 10)
        assertTrue(
          pass(root, area, None).popups.isEmpty,
          pass(root, area, Some(other)).popups.isEmpty
        )
      },
      test("a focused owner's popup is placed below it") {
        val content = new Fill('p')
        val owner = new Owner(content, 10, 3)
        val result = pass(topRow(owner), Rect(0, 0, 20, 10), Some(owner))
        assertTrue(result.popups == Vector(content -> Rect(0, 1, 10, 3)))
      },
      test("an owner containing the focused component shows its popup") {
        val content = new Fill('p')
        val inner = new Fill('i', focusable = true)
        val owner = new Owner(content, 10, 3, child = Some(inner))
        val result = pass(topRow(owner), Rect(0, 0, 20, 10), Some(inner))
        assertTrue(result.popups.map(_._1) == Vector(content))
      },
      test("a closed owner shows nothing") {
        val owner = new Owner(new Fill('p'), 10, 3)
        owner.open = false
        assertTrue(pass(topRow(owner), Rect(0, 0, 20, 10), Some(owner)).popups.isEmpty)
      },
      test("an anchor scrolled out of a ScrollView drops the popup; scrolled in, it returns") {
        val content = new Fill('p')
        val owner = new Owner(content, 5, 2)
        val rows: Seq[(Constraint, Component)] =
          Vector.tabulate(30)(i => (Constraint.Fixed(1): Constraint) -> (if i == 20 then owner else Spacer))
        for view <- ScrollView.make(VBox(rows*))
        yield
          val area = Rect(0, 0, 10, 5)
          val hidden = pass(view, area, Some(owner))
          view.scrollTo(18)
          val shown = pass(view, area, Some(owner))
          assertTrue(
            hidden.popups.isEmpty,
            // Owner on viewport row 2, popup on rows 3-4.
            shown.popups == Vector(content -> Rect(0, 3, 5, 2))
          )
      }
    ),
    suite("layout merge")(
      test("popup content joins rects, order and parents, parented to the owner") {
        val leaf = new Fill('l', focusable = true)
        val content = VBox(Constraint.Fixed(1) -> leaf)
        val owner = new Owner(content, 10, 3)
        val result = pass(topRow(owner), Rect(0, 0, 20, 10), Some(owner))
        assertTrue(
          result.rects.get(leaf.id).contains(Rect(0, 1, 10, 1)),
          result.order.contains(leaf),
          result.parents.get(content.id).contains(owner.id),
          result.parents.get(leaf.id).contains(content.id)
        )
      },
      test("focusables inside a popup stay out of the focus order") {
        val leaf = new Fill('l', focusable = true)
        val owner = new Owner(VBox(Constraint.Fixed(1) -> leaf), 10, 3)
        val result = pass(topRow(owner), Rect(0, 0, 20, 10), Some(owner))
        assertTrue(
          result.focusOrder.contains(owner.id),
          !result.focusOrder.contains(leaf.id)
        )
      },
      test("popup content is not asked for popups of its own") {
        val nested = new Owner(new Fill('n'), 4, 1)
        val owner = new Owner(nested, 10, 3)
        val result = pass(topRow(owner), Rect(0, 0, 20, 10), Some(owner))
        assertTrue(result.popups.map(_._1) == Vector(nested))
      }
    ),
    suite("drawing")(
      test("a popup draws over a sibling rendered after its owner") {
        val owner = new Owner(new Fill('p'), 4, 2)
        for buf <- drawn(topRow(owner, new Fill('x')), 10, 4, focusOn(owner))
        yield assertTrue(
          buf.glyphGrid == Vector(
            "oooooooooo",
            "ppppxxxxxx",
            "ppppxxxxxx",
            "xxxxxxxxxx"
          )
        )
      },
      test("a popup is opaque: cells its content leaves unwritten are blank") {
        val owner = new Owner(Spacer, 4, 2)
        for buf <- drawn(topRow(owner, new Fill('x')), 10, 4, focusOn(owner))
        yield assertTrue(buf.glyphGrid(1) == "....xxxxxx", buf.glyphGrid(2) == "....xxxxxx")
      },
      test("without focus nothing is drawn") {
        val owner = new Owner(new Fill('p'), 4, 2)
        for buf <- drawn(topRow(owner, new Fill('x')), 10, 4, RenderContext.empty)
        yield assertTrue(buf.glyphGrid(1) == "xxxxxxxxxx")
      },
      test("a popup is not clipped by an enclosing ScrollView's viewport") {
        val owner = new Owner(new Fill('p'), 5, 4)
        for
          view <- ScrollView.make(
                    VBox(
                      Constraint.Fixed(1) -> owner,
                      Constraint.Fixed(1) -> new Fill('a'),
                      Constraint.Fixed(1) -> new Fill('b')
                    )
                  )
          root = VBox(Constraint.Fixed(3) -> view, Constraint.Fill -> new Fill('x'))
          buf <- drawn(root, 10, 6, focusOn(owner))
        yield
          val grid = buf.glyphGrid
          assertTrue(
            // Viewport rows 1-2 and the rows below it, rows 3-4.
            grid(1).take(6) == "pppppa",
            grid(2).take(6) == "pppppb",
            grid(3) == "pppppxxxxx",
            grid(4) == "pppppxxxxx",
            grid(5) == "xxxxxxxxxx"
          )
      }
    )
  )
