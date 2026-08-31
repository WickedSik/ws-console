package io.github.wickedsik.wsconsole
package geometry

import zio.Scope
import zio.test.*

object SidesSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Sides")(
    suite("constants")(
      test("all enables every edge") {
        val s = Sides.all
        assertTrue(s.top, s.right, s.bottom, s.left, s.nonEmpty, !s.isEmpty)
      },
      test("none disables every edge") {
        val s = Sides.none
        assertTrue(!s.top, !s.right, !s.bottom, !s.left, s.isEmpty, !s.nonEmpty)
      }
    ),
    suite("symmetric")(
      test("horizontal enables left+right only") {
        val s = Sides.symmetric(horizontal = true, vertical = false)
        assertTrue(s.left, s.right, !s.top, !s.bottom)
      },
      test("vertical enables top+bottom only") {
        val s = Sides.symmetric(horizontal = false, vertical = true)
        assertTrue(s.top, s.bottom, !s.left, !s.right)
      },
      test("both true equals all") {
        assertTrue(Sides.symmetric(horizontal = true, vertical = true) == Sides.all)
      },
      test("both false equals none") {
        assertTrue(Sides.symmetric(horizontal = false, vertical = false) == Sides.none)
      }
    ),
    suite("toInsets")(
      test("all maps to Insets.all(1)") {
        assertTrue(Sides.all.toInsets == Insets.all(1))
      },
      test("none maps to Insets.zero") {
        assertTrue(Sides.none.toInsets == Insets.zero)
      },
      test("mixed edges map to the matching per-side inset") {
        val s = Sides(top = true, right = false, bottom = true, left = false)
        assertTrue(s.toInsets == Insets(top = 1, right = 0, bottom = 1, left = 0))
      },
      test("symmetric round-trips through toInsets") {
        val s = Sides.symmetric(horizontal = true, vertical = false)
        assertTrue(s.toInsets == Insets.symmetric(horizontal = 1, vertical = 0))
      }
    )
  )
