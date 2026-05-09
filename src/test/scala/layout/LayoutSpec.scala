package io.github.wickedsik.wsconsole
package layout

import zio.Scope
import zio.test.*

object LayoutSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Layout")(

    test("Direction enum has Horizontal and Vertical only") {
      val all = Direction.values.toSet
      assertTrue(
        all.contains(Direction.Horizontal),
        all.contains(Direction.Vertical),
        all.size == 2
      )
    },

    test("Layout.horizontal varargs constructs a Horizontal layout") {
      val l = Layout.horizontal(Constraint.Fixed(10), Constraint.Fill)
      assertTrue(
        l.direction == Direction.Horizontal,
        l.constraints == Seq(Constraint.Fixed(10), Constraint.Fill)
      )
    },

    test("Layout.vertical varargs constructs a Vertical layout") {
      val l = Layout.vertical(Constraint.Percentage(50), Constraint.Percentage(50))
      assertTrue(
        l.direction == Direction.Vertical,
        l.constraints == Seq(Constraint.Percentage(50), Constraint.Percentage(50))
      )
    },

    test("Layout.horizontal accepts a Seq via splat") {
      val cs: Seq[Constraint] = Seq(Constraint.Fixed(5), Constraint.Fill, Constraint.Fixed(5))
      val l = Layout.horizontal(cs*)
      assertTrue(l.constraints == cs)
    },

    test("empty Layout is valid") {
      val l = Layout.horizontal()
      assertTrue(l.direction == Direction.Horizontal, l.constraints.isEmpty)
    },

    test("equality is structural") {
      val a = Layout.vertical(Constraint.Fixed(3), Constraint.Fill)
      val b = Layout.vertical(Constraint.Fixed(3), Constraint.Fill)
      assertTrue(a == b)
    }
  )
