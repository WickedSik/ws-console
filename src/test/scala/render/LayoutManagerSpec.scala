package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.*
import geometry.Rect
import layout.Constraint

import zio.Scope
import zio.test.*

object LayoutManagerSpec extends ZIOSpecDefault:

  /** Minimal leaf component that records nothing — only its id matters. */
  private final case class Leaf() extends Component:
    def render(area: Rect, canvas: Canvas): Unit = ()

  def spec: Spec[TestEnvironment & Scope, Any] = suite("LayoutManager")(

    test("single leaf gets the full area") {
      val leaf   = Leaf()
      val area   = Rect(0, 0, 80, 24)
      val result = LayoutManager.default.resolve(leaf, area)
      assertTrue(
        result.rects(leaf.id) == area,
        result.order == Vector(leaf),
        result.parents.isEmpty
      )
    },

    test("HBox with two Fill children splits area equally") {
      val a = Leaf()
      val b = Leaf()
      val box = HBox(
        Constraint.Fill -> a,
        Constraint.Fill -> b
      )
      val result = LayoutManager.default.resolve(box, Rect(0, 0, 20, 5))
      assertTrue(
        result.rects(a.id) == Rect(0, 0, 10, 5),
        result.rects(b.id) == Rect(10, 0, 10, 5),
        result.parents(a.id) == box.id,
        result.parents(b.id) == box.id,
        !result.parents.contains(box.id)
      )
    },

    test("nested VBox(HBox(a, b), c) places all leaves correctly") {
      val a   = Leaf()
      val b   = Leaf()
      val c   = Leaf()
      val row = HBox(a, b)
      val col = VBox(row, c)
      val result = LayoutManager.default.resolve(col, Rect(0, 0, 20, 10))

      // Vertical: each child gets 5 rows. Inner HBox gets row 0..4, c gets row 5..9.
      assertTrue(
        result.rects(row.id) == Rect(0, 0, 20, 5),
        result.rects(c.id)   == Rect(0, 5, 20, 5),
        // HBox splits its 20-col strip equally
        result.rects(a.id) == Rect(0, 0, 10, 5),
        result.rects(b.id) == Rect(10, 0, 10, 5),
        result.parents(a.id) == row.id,
        result.parents(b.id) == row.id,
        result.parents(row.id) == col.id,
        result.parents(c.id)   == col.id
      )
    },

    test("zero-size area results in zero rects but every component appears") {
      val a   = Leaf()
      val b   = Leaf()
      val box = HBox(a, b)
      val result = LayoutManager.default.resolve(box, Rect(0, 0, 0, 0))
      assertTrue(
        result.rects.contains(a.id),
        result.rects.contains(b.id),
        result.rects(box.id).isEmpty,
        result.rects(a.id).isEmpty,
        result.rects(b.id).isEmpty
      )
    },

    test("Panel walks into its child via childLayouts") {
      val inner = Leaf()
      val panel = Panel(inner)
      val result = LayoutManager.default.resolve(panel, Rect(0, 0, 10, 5))
      assertTrue(
        result.rects.contains(inner.id),
        // Panel insets by 1 on every side
        result.rects(inner.id) == Rect(1, 1, 8, 3)
      )
    },

    test("re-running on the same tree returns the same id keys") {
      val a   = Leaf()
      val b   = Leaf()
      val box = HBox(a, b)
      val a1 = LayoutManager.default.resolve(box, Rect(0, 0, 10, 5))
      val a2 = LayoutManager.default.resolve(box, Rect(0, 0, 10, 5))
      assertTrue(a1.rects.keySet == a2.rects.keySet)
    }
  )
