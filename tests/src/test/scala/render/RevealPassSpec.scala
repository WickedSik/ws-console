package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.{Component, RenderContext, ScrollView, Spacer, VBox}
import geometry.Rect
import layout.Constraint

import zio.Scope
import zio.test.*

object RevealPassSpec extends ZIOSpecDefault:

  final private class Leaf extends Component:
    override val focusable: Boolean = true
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** `n` one-row focusable leaves stacked in a VBox. */
  private def column(leaves: Seq[Component]): VBox =
    VBox(leaves.map(l => (Constraint.Fixed(1): Constraint) -> l)*)

  private def inside(target: Rect, viewport: Rect): Boolean =
    target.y >= viewport.y && target.y + target.height <= viewport.y + viewport.height

  /** Lay out, reveal, lay out again — what the pipeline does in one frame. */
  private def revealAndRelayout(root: Component, area: Rect, target: Component): (Boolean, LayoutResult) =
    val first = LayoutManager.default.resolve(root, area)
    val moved = RevealPass(first, target.id)
    (moved, LayoutManager.default.resolve(root, area))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RevealPass")(
    test("scrolls down the minimum distance to show an offscreen target below") {
      val leaves = Vector.fill(30)(Leaf())
      for view <- ScrollView.make(column(leaves))
      yield
        val area = Rect(0, 0, 10, 5)
        val (moved, after) = revealAndRelayout(view, area, leaves(20))
        assertTrue(
          moved,
          // Row 20 becomes the last visible row: offset 20 + 1 - 5.
          view.offset == 16,
          after.rects(leaves(20).id) == Rect(0, 4, 9, 1)
        )
    },
    test("scrolls up the minimum distance to show an offscreen target above") {
      val leaves = Vector.fill(30)(Leaf())
      for view <- ScrollView.make(column(leaves))
      yield
        val area = Rect(0, 0, 10, 5)
        val _ = LayoutManager.default.resolve(view, area)
        view.scrollTo(16)
        val (moved, after) = revealAndRelayout(view, area, leaves(3))
        assertTrue(moved, view.offset == 3, after.rects(leaves(3).id) == Rect(0, 0, 9, 1))
    },
    test("a target already in view moves nothing") {
      val leaves = Vector.fill(30)(Leaf())
      for view <- ScrollView.make(column(leaves))
      yield
        val (moved, _) = revealAndRelayout(view, Rect(0, 0, 10, 5), leaves(2))
        assertTrue(!moved, view.offset == 0)
    },
    test("a target taller than the viewport aligns its top edge") {
      val tall = Leaf()
      val content = VBox(
        Constraint.Fixed(10) -> Spacer,
        Constraint.Fixed(8)  -> tall,
        Constraint.Fixed(10) -> Spacer
      )
      for view <- ScrollView.make(content)
      yield
        val (moved, after) = revealAndRelayout(view, Rect(0, 0, 10, 5), tall)
        assertTrue(moved, view.offset == 10, after.rects(tall.id).y == 0)
    },
    test("nested views both scroll so the target lands inside each viewport") {
      val leaves = Vector.fill(15)(Leaf())
      for
        inner <- ScrollView.make(column(leaves))
        outer <- ScrollView.make(
                   VBox(
                     Constraint.Fixed(10) -> Spacer,
                     Constraint.Fixed(4)  -> inner,
                     Constraint.Fixed(10) -> Spacer
                   )
                 )
      yield
        val area = Rect(0, 0, 12, 6)
        val (moved, after) = revealAndRelayout(outer, area, leaves(12))
        val target = after.rects(leaves(12).id)
        assertTrue(
          moved,
          inner.offset > 0,
          outer.offset > 0,
          inside(target, after.rects(inner.id)),
          inside(target, area)
        )
    },
    test("a component outside the layout is a no-op") {
      for view <- ScrollView.make(column(Vector.fill(3)(Leaf())))
      yield
        val layout = LayoutManager.default.resolve(view, Rect(0, 0, 10, 5))
        assertTrue(!RevealPass(layout, Leaf().id))
    }
  )
