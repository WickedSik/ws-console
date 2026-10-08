package io.github.wickedsik.wsconsole
package buffer

import zio.Scope
import zio.test.*

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
    }
  )
