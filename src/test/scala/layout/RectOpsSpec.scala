package io.github.wickedsik.wsconsole
package layout

import geometry.Rect
import zio.Scope
import zio.test.*

object RectOpsSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Rect.split / LayoutEngine.split")(

    suite("Horizontal")(
      test("partitions along x; preserves full y-extent") {
        val area = Rect(0, 0, 100, 24)
        val l    = Layout.horizontal(Constraint.Fixed(20), Constraint.Fill, Constraint.Fixed(15))
        val rs   = LayoutEngine.split(l, area)
        assertTrue(
          rs.size == 3,
          rs(0) == Rect(0,  0, 20, 24),
          rs(1) == Rect(20, 0, 65, 24),
          rs(2) == Rect(85, 0, 15, 24)
        )
      },

      test("origins respect non-zero area origin") {
        val area = Rect(10, 5, 50, 8)
        val l    = Layout.horizontal(Constraint.Fixed(10), Constraint.Fixed(20), Constraint.Fill)
        val rs   = LayoutEngine.split(l, area)
        assertTrue(
          rs(0) == Rect(10, 5, 10, 8),
          rs(1) == Rect(20, 5, 20, 8),
          rs(2) == Rect(40, 5, 20, 8)
        )
      },

      test("zero-width cells produce zero-area rects (not elided)") {
        val area = Rect(0, 0, 10, 5)
        // Two Fixed(60) over-subscribe width 10 → [10, 0]
        val l    = Layout.horizontal(Constraint.Fixed(60), Constraint.Fixed(60))
        val rs   = LayoutEngine.split(l, area)
        assertTrue(
          rs.size == 2,
          rs(0) == Rect(0,  0, 10, 5),
          rs(1) == Rect(10, 0, 0,  5),
          rs(1).isEmpty
        )
      }
    ),

    suite("Vertical")(
      test("partitions along y; preserves full x-extent") {
        val area = Rect(0, 0, 80, 24)
        val l    = Layout.vertical(Constraint.Fixed(3), Constraint.Fill, Constraint.Fixed(2))
        val rs   = LayoutEngine.split(l, area)
        assertTrue(
          rs.size == 3,
          rs(0) == Rect(0, 0,  80, 3),
          rs(1) == Rect(0, 3,  80, 19),
          rs(2) == Rect(0, 22, 80, 2)
        )
      }
    ),

    suite("extension method")(
      test("rect.split delegates to LayoutEngine.split") {
        val area = Rect(2, 3, 40, 10)
        val l    = Layout.horizontal(Constraint.Percentage(50), Constraint.Percentage(50))
        val viaExt    = area.split(l)
        val viaEngine = LayoutEngine.split(l, area)
        assertTrue(viaExt == viaEngine)
      }
    ),

    suite("edge cases")(
      test("empty layout returns empty Seq") {
        val rs = LayoutEngine.split(Layout.horizontal(), Rect(0, 0, 10, 5))
        assertTrue(rs.isEmpty)
      },

      test("empty area produces empty rects") {
        val rs = LayoutEngine.split(
          Layout.horizontal(Constraint.Fill, Constraint.Fill),
          Rect(0, 0, 0, 5)
        )
        assertTrue(
          rs.size == 2,
          rs.forall(_.isEmpty)
        )
      }
    )
  )
