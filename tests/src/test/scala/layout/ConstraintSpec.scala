package io.github.wickedsik.wsconsole
package layout

import zio.Scope
import zio.test.*

object ConstraintSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Constraint")(
    suite("Fixed")(
      test("accepts non-negative sizes") {
        val a = Constraint.Fixed(0)
        val b = Constraint.Fixed(42)
        assertTrue(a.size == 0, b.size == 42)
      },
      test("rejects negative size") {
        val res = scala.util.Try(Constraint.Fixed(-1))
        assertTrue(res.failed.toOption.collect { case e: IllegalArgumentException => e }.nonEmpty)
      }
    ),
    suite("Percentage")(
      test("accepts values in [0, 100]") {
        val a = Constraint.Percentage(0)
        val b = Constraint.Percentage(100)
        val c = Constraint.Percentage(33)
        assertTrue(a.percent == 0, b.percent == 100, c.percent == 33)
      },
      test("rejects negative percent") {
        val res = scala.util.Try(Constraint.Percentage(-1))
        assertTrue(res.isFailure)
      },
      test("rejects percent > 100") {
        val res = scala.util.Try(Constraint.Percentage(101))
        assertTrue(res.isFailure)
      }
    ),
    suite("Fill")(
      test("Fill is a singleton case") {
        assertTrue(Constraint.Fill eq Constraint.Fill)
      }
    ),
    suite("Bounded")(
      test("accepts min-only") {
        val b = Constraint.atLeast(20, Constraint.Fill)
        assertTrue(b == Constraint.Bounded(Some(20), None, Constraint.Fill))
      },
      test("accepts max-only") {
        val b = Constraint.atMost(50, Constraint.Percentage(80))
        assertTrue(b == Constraint.Bounded(None, Some(50), Constraint.Percentage(80)))
      },
      test("accepts both bounds when min <= max") {
        val b = Constraint.bounded(10, 30, Constraint.Fill)
        assertTrue(b == Constraint.Bounded(Some(10), Some(30), Constraint.Fill))
      },
      test("rejects when neither min nor max is defined") {
        val res = scala.util.Try(Constraint.Bounded(None, None, Constraint.Fill))
        assertTrue(res.isFailure)
      },
      test("rejects negative min") {
        val res = scala.util.Try(Constraint.atLeast(-1, Constraint.Fill))
        assertTrue(res.isFailure)
      },
      test("rejects negative max") {
        val res = scala.util.Try(Constraint.atMost(-1, Constraint.Fill))
        assertTrue(res.isFailure)
      },
      test("rejects min > max") {
        val res = scala.util.Try(Constraint.bounded(50, 10, Constraint.Fill))
        assertTrue(res.isFailure)
      },
      test("rejects nesting Bounded inside Bounded") {
        val res = scala.util.Try {
          Constraint.atLeast(5, Constraint.atMost(10, Constraint.Fill))
        }
        assertTrue(res.isFailure)
      }
    )
  )
