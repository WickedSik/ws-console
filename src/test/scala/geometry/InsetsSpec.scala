package io.github.wickedsik.wsconsole
package geometry

import zio.Scope
import zio.test.*

object InsetsSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Insets")(
    test("constructor stores per-edge values in declared order") {
      val i = Insets(top = 1, right = 2, bottom = 3, left = 4)
      assertTrue(
        i.top == 1,
        i.right == 2,
        i.bottom == 3,
        i.left == 4
      )
    },
    test("zero is all four edges at zero") {
      assertTrue(
        Insets.zero.top == 0,
        Insets.zero.right == 0,
        Insets.zero.bottom == 0,
        Insets.zero.left == 0,
        Insets.zero == Insets(0, 0, 0, 0)
      )
    },
    test("all sets every edge to the same value") {
      val i = Insets.all(3)
      assertTrue(
        i.top == 3,
        i.right == 3,
        i.bottom == 3,
        i.left == 3,
        i == Insets(3, 3, 3, 3)
      )
    },
    test("symmetric assigns horizontal to left/right and vertical to top/bottom") {
      val i = Insets.symmetric(horizontal = 4, vertical = 2)
      assertTrue(
        i.top == 2,
        i.bottom == 2,
        i.left == 4,
        i.right == 4
      )
    },
    test("insets with same fields compare equal") {
      val a = Insets(1, 2, 3, 4)
      val b = Insets(1, 2, 3, 4)
      assertTrue(a == b, a.hashCode == b.hashCode)
    },
    test("insets differing in any field compare unequal") {
      assertTrue(
        Insets(1, 2, 3, 4) != Insets(9, 2, 3, 4),
        Insets(1, 2, 3, 4) != Insets(1, 9, 3, 4),
        Insets(1, 2, 3, 4) != Insets(1, 2, 9, 4),
        Insets(1, 2, 3, 4) != Insets(1, 2, 3, 9)
      )
    }
  )
