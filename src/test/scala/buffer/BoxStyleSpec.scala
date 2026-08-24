package io.github.wickedsik.wsconsole
package buffer

import zio.Scope
import zio.test.*

object BoxStyleSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BoxStyle")(
    test("Single reports an inset of one cell") {
      assertTrue(BoxStyle.Single.inset == 1)
    },
    test("Double reports an inset of one cell") {
      assertTrue(BoxStyle.Double.inset == 1)
    },
    test("Borderless reports an inset of zero cells") {
      assertTrue(BoxStyle.Borderless.inset == 0)
    },
    test("Single exposes the SingleLine glyph set") {
      assertTrue(
        BoxStyle.Single.topLeft == '┌',
        BoxStyle.Single.topRight == '┐',
        BoxStyle.Single.bottomLeft == '└',
        BoxStyle.Single.bottomRight == '┘',
        BoxStyle.Single.horizontal == '─',
        BoxStyle.Single.vertical == '│'
      )
    },
    test("Double exposes the DoubleLine glyph set") {
      assertTrue(
        BoxStyle.Double.topLeft == '╔',
        BoxStyle.Double.topRight == '╗',
        BoxStyle.Double.bottomLeft == '╚',
        BoxStyle.Double.bottomRight == '╝',
        BoxStyle.Double.horizontal == '═',
        BoxStyle.Double.vertical == '║'
      )
    },
    test("Borderless exposes space glyphs as a fallback the renderer never reads") {
      assertTrue(
        BoxStyle.Borderless.topLeft == ' ',
        BoxStyle.Borderless.topRight == ' ',
        BoxStyle.Borderless.bottomLeft == ' ',
        BoxStyle.Borderless.bottomRight == ' ',
        BoxStyle.Borderless.horizontal == ' ',
        BoxStyle.Borderless.vertical == ' '
      )
    }
  )
