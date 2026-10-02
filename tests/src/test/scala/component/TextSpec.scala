package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Cell, CellStyle, Foreground, Span, Spans}
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
    test("CJK truncates by columns: a wide glyph that would straddle the edge is left out") {
      val buf = renderToBuffer(5, 1)(Text("中文字", redStyle))
      // 中 and 文 take columns 0–3; 字 would need 4–5 and is dropped whole.
      assertGrid(buf, "中.文..") &&
      assertCell(buf, 0, 0, Cell("中", redStyle)) &&
      assertCell(buf, 2, 0, Cell("文", redStyle))
    },
    test("CJK centers and right-aligns by columns, not chars") {
      val center = renderToBuffer(8, 1)(Text("中文", redStyle, Alignment.Center))
      val right = renderToBuffer(8, 1)(Text("中文", redStyle, Alignment.Right))
      // 2 chars but 4 columns: centered at (8 - 4) / 2 = 2, right at 8 - 4 = 4.
      assertGrid(center, "..中.文...") &&
      assertCell(center, 2, 0, Cell("中", redStyle)) &&
      assertGrid(right, "....中.文.") &&
      assertCell(right, 4, 0, Cell("中", redStyle))
    },
    test("Cyrillic and emoji align by columns") {
      val cyrillic = renderToBuffer(8, 1)(Text("Мир", redStyle, Alignment.Right))
      val emoji = renderToBuffer(6, 1)(Text("👍🏽", redStyle, Alignment.Center))
      assertGrid(cyrillic, ".....Мир") &&
      assertCell(emoji, 2, 0, Cell("👍🏽", redStyle))
    },
    test("a flag is two columns: aligned and truncated as a wide glyph") {
      val right = renderToBuffer(6, 1)(Text("a🇳🇱", redStyle, Alignment.Right))
      val cut = renderToBuffer(4, 1)(Text("ab🇳🇱", redStyle), area = Rect(0, 0, 3, 1))
      // 3 columns: right-aligned at 6 - 3 = 3, the flag on 4–5 with a continuation cell.
      assertCell(right, 3, 0, Cell('a', redStyle)) &&
      assertCell(right, 4, 0, Cell("🇳🇱", redStyle)) &&
      assertTrue(right.get(5, 0).exists(_.isContinuation)) &&
      assertGrid(cut, "ab..")
    },
    test("spans draw in their own styles on one line") {
      val buf = renderToBuffer(8, 1)(Text(Spans("Name", Span("*", redStyle))))
      assertGrid(buf, "Name*...") &&
      assertCell(buf, 0, 0, Cell('N')) &&
      assertCell(buf, 4, 0, Cell('*', redStyle))
    },
    test("spans align as one line and truncate across the span boundary") {
      val line = Spans(Span("ab", redStyle), "中文")
      val right = renderToBuffer(8, 1)(Text(line, Alignment.Right))
      val cut = renderToBuffer(5, 1)(Text(line))
      assertGrid(right, "..ab中.文.") &&
      assertCell(right, 2, 0, Cell('a', redStyle)) &&
      assertCell(right, 4, 0, Cell("中", CellStyle.Empty)) &&
      assertGrid(cut, "ab中..")
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
