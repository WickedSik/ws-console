package io.github.wickedsik.wsconsole
package layout

import zio.Scope
import zio.test.*

object LayoutEngineSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("LayoutEngine.resolve")(
    suite("edge cases")(
      test("empty constraints produce empty result") {
        val sizes = LayoutEngine.resolve(Layout.horizontal(), 100)
        assertTrue(sizes.isEmpty)
      },
      test("available = 0 yields all zeros") {
        val l = Layout.horizontal(Constraint.Fixed(10), Constraint.Fill, Constraint.Percentage(30))
        val sizes = LayoutEngine.resolve(l, 0)
        assertTrue(sizes == Seq(0, 0, 0))
      },
      test("negative available yields all zeros") {
        val l = Layout.horizontal(Constraint.Fixed(10), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, -5)
        assertTrue(sizes == Seq(0, 0))
      }
    ),
    suite("Fixed-only")(
      test("sums to ≤ available when within budget") {
        val l = Layout.horizontal(Constraint.Fixed(20), Constraint.Fixed(30))
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(20, 30), sizes.sum <= 100)
      },
      test("over-subscription truncates left-to-right; later cells get 0") {
        val l = Layout.horizontal(Constraint.Fixed(60), Constraint.Fixed(60))
        val sizes = LayoutEngine.resolve(l, 80)
        assertTrue(sizes == Seq(60, 20))
      },
      test("over-subscription with three cells fully exhausted") {
        val l = Layout.horizontal(Constraint.Fixed(50), Constraint.Fixed(50), Constraint.Fixed(50))
        val sizes = LayoutEngine.resolve(l, 80)
        assertTrue(sizes == Seq(50, 30, 0))
      }
    ),
    suite("Percentage")(
      test("Percentage(33) of 100 alone leaves rest unallocated (under-specification)") {
        val sizes = LayoutEngine.resolve(Layout.horizontal(Constraint.Percentage(33)), 100)
        // Single Percentage(33), residual=67, count=1, 67 > 1 → no distribution.
        assertTrue(sizes == Seq(33), sizes.sum == 33)
      },
      test("three Percentage(33) of 100 distributes floor remainder to first") {
        val l = Layout.horizontal(
          Constraint.Percentage(33),
          Constraint.Percentage(33),
          Constraint.Percentage(33)
        )
        val sizes = LayoutEngine.resolve(l, 100)
        // Residual=1, count=3, 1 ≤ 3 → distribute to first.
        assertTrue(sizes == Seq(34, 33, 33), sizes.sum == 100)
      },
      test("Percentage 50/50 of 100 splits exactly") {
        val l = Layout.horizontal(Constraint.Percentage(50), Constraint.Percentage(50))
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(50, 50))
      },
      test("two Percentage(50) of 99 distributes floor remainder") {
        val l = Layout.horizontal(Constraint.Percentage(50), Constraint.Percentage(50))
        val sizes = LayoutEngine.resolve(l, 99)
        // floor(99*50/100) = 49 each, residual=1, count=2, 1 ≤ 2 → distribute.
        assertTrue(sizes == Seq(50, 49), sizes.sum == 99)
      },
      test("Percentage(50) alone of 99 leaves rest unallocated") {
        val sizes = LayoutEngine.resolve(Layout.horizontal(Constraint.Percentage(50)), 99)
        // Residual=50, count=1, 50 > 1 → no distribution.
        assertTrue(sizes == Seq(49), sizes.sum == 49)
      },
      test("Percentage(40) + Fixed(20) of 100 leaves un-allocated cells alone") {
        val l = Layout.horizontal(Constraint.Percentage(40), Constraint.Fixed(20))
        val sizes = LayoutEngine.resolve(l, 100)
        // No Fill; residual=40, count=1, 40 > 1 → no distribution. Sum = 60.
        assertTrue(sizes == Seq(40, 20), sizes.sum == 60)
      }
    ),
    suite("Fill")(
      test("single Fill takes all available") {
        val sizes = LayoutEngine.resolve(Layout.horizontal(Constraint.Fill), 80)
        assertTrue(sizes == Seq(80))
      },
      test("two Fill cells share equally with even available") {
        val l = Layout.horizontal(Constraint.Fill, Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 80)
        assertTrue(sizes == Seq(40, 40))
      },
      test("three Fill cells share with leftover going to earliest") {
        val l = Layout.horizontal(Constraint.Fill, Constraint.Fill, Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(34, 33, 33), sizes.sum == 100)
      },
      test("Fill absorbs residual after Percentage and Fixed") {
        val l = Layout.horizontal(
          Constraint.Percentage(33),
          Constraint.Percentage(33),
          Constraint.Fill
        )
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(33, 33, 34), sizes.sum == 100)
      },
      test("Fill alongside Fixed") {
        val l = Layout.horizontal(Constraint.Fixed(20), Constraint.Fill, Constraint.Fixed(10))
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(20, 70, 10))
      }
    ),
    suite("Bounded")(
      test("Bounded(Fixed) clamped upward by min") {
        val l = Layout.horizontal(Constraint.atLeast(20, Constraint.Fixed(10)), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(20, 80))
      },
      test("Bounded(Fixed) clamped downward by max") {
        val l = Layout.horizontal(Constraint.atMost(15, Constraint.Fixed(50)), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(15, 85))
      },
      test("Bounded(Percentage) clamped upward by min") {
        // Percentage(10) of 100 = 10, but min is 25 → clamped to 25
        val l = Layout.horizontal(Constraint.atLeast(25, Constraint.Percentage(10)), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(25, 75))
      },
      test("Bounded(Percentage) clamped downward by max") {
        // Percentage(80) of 100 = 80, but max is 30 → clamped to 30
        val l = Layout.horizontal(Constraint.atMost(30, Constraint.Percentage(80)), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes == Seq(30, 70))
      },
      test("atLeast(20, Fill) preserves min when sharing with Fill") {
        val l = Layout.horizontal(
          Constraint.atLeast(20, Constraint.Fill),
          Constraint.Fill,
          Constraint.Fixed(60)
        )
        val sizes = LayoutEngine.resolve(l, 100)
        // Fixed(60) takes 60. Floor 20 reserved for Bounded(Fill). Residual = 100 - 60 - 20 = 20.
        // Two Fill-wanting cells share 20: Bounded gets 10 more (total 30), Fill gets 10. Total: 60+30+10 = 100.
        assertTrue(sizes == Seq(30, 10, 60), sizes.sum == 100)
      },
      test("atMost(10, Fill) caps and redistributes to other Fill") {
        val l = Layout.horizontal(Constraint.atMost(10, Constraint.Fill), Constraint.Fill)
        val sizes = LayoutEngine.resolve(l, 100)
        // Bounded(Fill) capped at 10; remaining 90 redistributed to plain Fill.
        assertTrue(sizes == Seq(10, 90))
      },
      test("bounded(min, max, Fill) honours both bounds") {
        val l = Layout.horizontal(
          Constraint.bounded(15, 25, Constraint.Fill),
          Constraint.Fill
        )
        val sizes = LayoutEngine.resolve(l, 100)
        // Floor 15. Residual 85 split equally: Bounded gets 42, capped at 25 → uses 10. Fill gets 42 + 33 redistributed.
        // Iteration: share=42 each, Bounded takes 10 (cap reached), Fill takes 42; remaining = 85-52=33; redistribute to Fill alone.
        // Final: Bounded=25, Fill=42+33=75. Total: 100.
        assertTrue(sizes == Seq(25, 75), sizes.sum == 100)
      }
    ),
    suite("invariants")(
      test("sum is always ≤ available") {
        val cases = List(
          Layout.horizontal(Constraint.Fixed(40), Constraint.Fill, Constraint.Fixed(20)) -> 100,
          Layout.horizontal(Constraint.Percentage(50), Constraint.Fill) -> 80,
          Layout.horizontal(Constraint.Fixed(60), Constraint.Fixed(60), Constraint.Fixed(60)) -> 80,
          Layout.vertical(Constraint.Fill, Constraint.Fill, Constraint.Fill) -> 13,
          Layout.horizontal(Constraint.atMost(10, Constraint.Fill), Constraint.Fill) -> 100
        )
        val results = cases.map { case (l, avail) =>
          LayoutEngine.resolve(l, avail).sum <= avail
        }
        assertTrue(results.forall(identity))
      },
      test("result length equals constraint count") {
        val l = Layout.horizontal(Constraint.Fixed(5), Constraint.Fill, Constraint.Percentage(20))
        val sizes = LayoutEngine.resolve(l, 100)
        assertTrue(sizes.size == 3)
      }
    )
  )
