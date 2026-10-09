package io.github.wickedsik.wsconsole
package buffer

import zio.Scope
import zio.test.*

import geometry.{Rect, Sides}

object BoxStyleSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BoxStyle")(
    test("Single exposes the light-line glyph set on every grid position") {
      assertTrue(
        BoxStyle.Single.topLeft == '┌',
        BoxStyle.Single.topCenter == '─',
        BoxStyle.Single.topRight == '┐',
        BoxStyle.Single.midLeft == '│',
        BoxStyle.Single.midRight == '│',
        BoxStyle.Single.bottomLeft == '└',
        BoxStyle.Single.bottomCenter == '─',
        BoxStyle.Single.bottomRight == '┘'
      )
    },
    test("Double exposes the heavy-double-line glyph set on every grid position") {
      assertTrue(
        BoxStyle.Double.topLeft == '╔',
        BoxStyle.Double.topCenter == '═',
        BoxStyle.Double.topRight == '╗',
        BoxStyle.Double.midLeft == '║',
        BoxStyle.Double.midRight == '║',
        BoxStyle.Double.bottomLeft == '╚',
        BoxStyle.Double.bottomCenter == '═',
        BoxStyle.Double.bottomRight == '╝'
      )
    },
    test("Dashed exposes the light dashed edges with light corners") {
      assertTrue(
        BoxStyle.Dashed.topLeft == '┌',
        BoxStyle.Dashed.topCenter == '┄',
        BoxStyle.Dashed.topRight == '┐',
        BoxStyle.Dashed.midLeft == '┆',
        BoxStyle.Dashed.midRight == '┆',
        BoxStyle.Dashed.bottomLeft == '└',
        BoxStyle.Dashed.bottomCenter == '┄',
        BoxStyle.Dashed.bottomRight == '┘'
      )
    },
    test("drawBox renders Dashed on all four sides and corners, with a title on the top edge") {
      val buffer = ScreenBuffer.of(10, 4)
      Canvas(buffer).drawBox(Rect(0, 0, 10, 4), BoxStyle.Dashed, Sides.all, Some("Hi"))
      def row(y: Int): String = (0 until 10).map(x => buffer.get(x, y).map(_.char).getOrElse(' ')).mkString
      assertTrue(
        row(0) == "┌┄Hi┄┄┄┄┄┐",
        row(1) == "┆        ┆",
        row(2) == "┆        ┆",
        row(3) == "└┄┄┄┄┄┄┄┄┘"
      )
    }
  )
