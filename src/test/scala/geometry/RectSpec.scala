package io.github.wickedsik.wsconsole
package geometry

import zio.Scope
import zio.test.*

object RectSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Rect")(

    test("empty rectangles are detected") {
      assertTrue(
        Rect(0, 0, 0, 10).isEmpty,
        Rect(0, 0, 10, 0).isEmpty,
        Rect(0, 0, -1, 10).isEmpty,
        !Rect(0, 0, 1, 1).isEmpty
      )
    },

    test("contains accepts interior points and rejects exterior points") {
      val r = Rect(2, 3, 4, 5)
      assertTrue(
        r.contains(2, 3),
        r.contains(5, 7),
        !r.contains(6, 7),
        !r.contains(5, 8),
        !r.contains(1, 3),
        !r.contains(2, 2)
      )
    },

    test("contains rejects all points for empty rectangles") {
      val empty = Rect(0, 0, 0, 0)
      assertTrue(
        !empty.contains(0, 0),
        !empty.contains(-1, -1)
      )
    },

    test("intersects detects overlapping rectangles") {
      val a = Rect(0, 0, 5, 5)
      val b = Rect(3, 3, 5, 5)
      val c = Rect(10, 10, 2, 2)
      assertTrue(
        a.intersects(b),
        b.intersects(a),
        !a.intersects(c)
      )
    },

    test("intersects treats touching edges as non-overlapping") {
      val a = Rect(0, 0, 5, 5)
      val b = Rect(5, 0, 5, 5)
      assertTrue(!a.intersects(b))
    },

    test("intersects returns false for empty rectangles") {
      val r = Rect(0, 0, 5, 5)
      val e = Rect(0, 0, 0, 0)
      assertTrue(
        !r.intersects(e),
        !e.intersects(r)
      )
    },

    test("inner shrinks by margin on all sides") {
      val r = Rect(2, 3, 10, 8).inner(2)
      assertTrue(
        r.x == 4,
        r.y == 5,
        r.width == 6,
        r.height == 4
      )
    },

    test("inner with oversized margin produces empty rectangle") {
      val r = Rect(0, 0, 4, 4).inner(5)
      assertTrue(
        r.isEmpty,
        r.width == 0,
        r.height == 0
      )
    },

    test("inner(Insets) shrinks per edge and shifts origin by (left, top)") {
      val r = Rect(2, 3, 10, 8).inner(Insets(top = 1, right = 2, bottom = 3, left = 4))
      assertTrue(
        r.x      == 6,  // 2 + left(4)
        r.y      == 4,  // 3 + top(1)
        r.width  == 4,  // 10 - left(4) - right(2)
        r.height == 4   // 8  - top(1)  - bottom(3)
      )
    },

    test("inner(Insets.zero) returns a rectangle with the same fields") {
      val r = Rect(2, 3, 10, 8)
      assertTrue(r.inner(Insets.zero) == r)
    },

    test("inner(Insets.all(n)) matches inner(n)") {
      val r = Rect(1, 1, 12, 9)
      assertTrue(r.inner(Insets.all(2)) == r.inner(2))
    },

    test("inner(Insets) with oversized insets produces empty rectangle") {
      val r = Rect(0, 0, 4, 4).inner(Insets(top = 3, right = 5, bottom = 3, left = 5))
      assertTrue(
        r.isEmpty,
        r.width  == 0,
        r.height == 0
      )
    },

    test("inner(Insets) origin shift applies even when the result is empty") {
      // Left inset pushes origin right; width collapses to zero.
      // Origin still shifts — no clamping of x or y is specified.
      val r = Rect(0, 0, 2, 2).inner(Insets(top = 0, right = 5, bottom = 0, left = 3))
      assertTrue(
        r.x      == 3,
        r.y      == 0,
        r.width  == 0,
        r.height == 2
      )
    }
  )
