package io.github.wickedsik.wsconsole
package testkit

import component.Text

import zio.Scope
import zio.test.*

import RenderHarness.{glyphGrid, renderToBuffer}
import GridAssertions.{assertChar, assertGrid}

/**
 * Guards the assertion layer itself — in particular the empty-cell sentinel
 * policy and its one sharp edge: a rendered glyph equal to the sentinel.
 */
object GridAssertionsSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("GridAssertions")(

    test("the sentinel makes trailing padding visible") {
      val buf = renderToBuffer(3, 1)(Text("a"))
      assertGrid(buf, "a..")
    },

    test("glyphGrid rejects content that collides with the empty-cell sentinel") {
      // A literal '.' is indistinguishable from padding in a char-only grid, so
      // the harness fails fast rather than silently conflating them.
      val buf = renderToBuffer(3, 1)(Text("."))
      assertTrue(
        scala.util.Try(buf.glyphGrid).isFailure,
        // ...yet the exact cell IS assertable value-based.
        buf.get(0, 0).exists(_.char == '.')
      )
    },

    test("assertChar disambiguates a literal period from padding") {
      val period = renderToBuffer(3, 1)(Text("."))
      val blank  = renderToBuffer(3, 1)(Text(""))
      assertChar(period, 0, 0, '.') && assertChar(blank, 0, 0, ' ')
    }
  )
