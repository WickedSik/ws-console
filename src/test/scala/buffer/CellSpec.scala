package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import zio.Scope
import zio.test.*

object CellSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Cell")(

    test("default cell has empty style") {
      val c = Cell('a')
      assertTrue(
        c.char == 'a',
        c.style == CellStyle.Empty
      )
    },

    test("Cell.Empty is a space with default styling") {
      assertTrue(
        Cell.Empty.char == ' ',
        Cell.Empty.style == CellStyle.Empty
      )
    },

    test("cells with same fields compare equal") {
      val style = CellStyle(fg = Foreground.Named(FgColor.Red))
      val a = Cell('x', style)
      val b = Cell('x', style)
      assertTrue(a == b, a.hashCode == b.hashCode)
    },

    test("cells differing in any field compare unequal") {
      val s1 = CellStyle(fg = Foreground.Named(FgColor.Red))
      val s2 = CellStyle(fg = Foreground.Named(FgColor.Blue))
      assertTrue(
        Cell('a') != Cell('b'),
        Cell('a', s1) != Cell('a', s2)
      )
    }
  )
