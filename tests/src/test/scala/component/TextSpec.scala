package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Cell, CellStyle, Foreground}
import geometry.Rect
import testkit.GridAssertions.{assertCell, assertGrid}
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

object TextSpec extends ZIOSpecDefault:

  private val redStyle = CellStyle(fg = Foreground.Named(FgColor.Red))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Text")(
    test("renders a left-aligned line at area origin") {
      val buf = renderToBuffer(6, 1)(Text("hi", redStyle, Alignment.Left))
      assertGrid(buf, "hi....") &&
      assertCell(buf, 0, 0, Cell('h', redStyle)) &&
      assertCell(buf, 1, 0, Cell('i', redStyle))
    },
    test("centers text within the area width") {
      val buf = renderToBuffer(10, 1)(Text("hi", redStyle, Alignment.Center))
      // (10 - 2) / 2 = 4 → text starts at x=4
      assertGrid(buf, "....hi....") &&
      assertCell(buf, 4, 0, Cell('h', redStyle))
    },
    test("right-aligns text against the area's right edge") {
      val buf = renderToBuffer(10, 1)(Text("hi", redStyle, Alignment.Right))
      // 10 - 2 = 8 → text starts at x=8
      assertGrid(buf, "........hi")
    },
    test("respects a non-zero area origin") {
      val buf = renderToBuffer(8, 4)(Text("hi", redStyle), area = Rect(2, 1, 6, 1))
      assertGrid(
        buf,
        """
          |........
          |..hi....
          |........
          |........
          |""".stripMargin
      ) &&
      assertCell(buf, 2, 1, Cell('h', redStyle))
    },
    test("truncates content longer than area width") {
      val buf = renderToBuffer(8, 1)(Text("hello world", redStyle), area = Rect(0, 0, 5, 1))
      // Only "hello" fits in width 5; columns 5-7 stay empty.
      assertGrid(buf, "hello...")
    },
    test("empty content is a no-op") {
      val buf = renderToBuffer(6, 1)(Text("", redStyle))
      assertGrid(buf, "......")
    },
    test("empty area is a no-op") {
      val buf = renderToBuffer(6, 1)(Text("hi", redStyle), area = Rect(0, 0, 0, 0))
      assertGrid(buf, "......")
    }
  )
