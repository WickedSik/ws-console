package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, Cell, CellStyle, Foreground, Span, Spans, TextWrap}
import geometry.Rect
import testkit.GridAssertions.{assertCell, assertGrid, assertStyle}
import testkit.RenderHarness.{glyphGrid, renderToBuffer}

import zio.Scope
import zio.test.*

object WrappedTextSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val bold = CellStyle(attributes = Set(Attribute.Bold))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("WrappedText")(
    suite("layout")(
      test("wraps on words into successive rows") {
        val buf = renderToBuffer(9, 3)(WrappedText(Spans("the quick brown fox")))
        assertGrid(
          buf,
          """
            |the.quick
            |brown.fox
            |.........
            |""".stripMargin
        )
      },
      test("alignment applies to each wrapped line on its own") {
        val text = Spans("aa bbbb")
        val right = renderToBuffer(6, 2)(WrappedText(text, align = Alignment.Right))
        val center = renderToBuffer(6, 2)(WrappedText(text, align = Alignment.Center))
        assertGrid(right, "....aa\n..bbbb") &&
        assertGrid(center, "..aa..\n.bbbb.")
      },
      test("respects a non-zero area origin and clips rows below the area") {
        val buf = renderToBuffer(6, 4)(WrappedText(Spans("aa bb cc dd")), area = Rect(1, 1, 3, 2))
        assertGrid(
          buf,
          """
            |......
            |.aa...
            |.bb...
            |......
            |""".stripMargin
        )
      },
      test("CJK lands on whole cells, a wide glyph never straddles the edge") {
        val buf = renderToBuffer(5, 3)(WrappedText(Spans("中文字符测试")))
        assertGrid(
          buf,
          """
            |中.文..
            |字.符..
            |测.试..
            |""".stripMargin
        )
      },
      test("empty area is a no-op") {
        val buf = renderToBuffer(4, 1)(WrappedText(Spans("hi")), area = Rect(0, 0, 0, 0))
        assertGrid(buf, "....")
      }
    ),
    suite("maxLines")(
      test("overflow ends the last kept line with …") {
        val buf = renderToBuffer(9, 3)(WrappedText(Spans("one two three four"), maxLines = Some(2)))
        assertGrid(
          buf,
          """
            |one.two..
            |three…...
            |.........
            |""".stripMargin
        )
      },
      test("the marker replaces the last column when the kept line is full") {
        val wt = WrappedText(Spans("abcd efgh ijkl"), maxLines = Some(1))
        assertTrue(wt.lines(4).map(_.text) == Vector("abc" + TextWrap.Ellipsis))
      },
      test("text that fits within maxLines carries no marker") {
        val wt = WrappedText(Spans("one two"), maxLines = Some(2))
        assertTrue(wt.lines(9).map(_.text) == Vector("one two"))
      },
      test("maxLines below 1 is rejected") {
        assertTrue(scala.util.Try(WrappedText(Spans("x"), maxLines = Some(0))).isFailure)
      }
    ),
    suite("preferredHeight")(
      test("is the wrapped line count, growing as the width narrows") {
        val wt = WrappedText(Spans("the quick brown fox jumps"))
        assertTrue(
          wt.preferredHeight(40).contains(1),
          wt.preferredHeight(10).contains(3),
          wt.preferredHeight(5).contains(5),
          wt.preferredHeight(0).contains(0),
          WrappedText(Spans("")).preferredHeight(10).contains(0),
          WrappedText(Spans("a\n\nb")).preferredHeight(10).contains(3)
        )
      },
      test("is capped by maxLines") {
        val wt = WrappedText(Spans("the quick brown fox jumps"), maxLines = Some(2))
        assertTrue(
          wt.preferredHeight(5).contains(2),
          wt.preferredHeight(40).contains(1)
        )
      },
      test("matches the rows actually drawn") {
        val wt = WrappedText(Spans("Field help text runs up to seven lines in a narrow column 中文字符 · Кириллица"))
        val checks = Seq(6, 11, 20, 33).map { w =>
          val buf = renderToBuffer(w, 20)(wt)
          val drawn = buf.glyphGrid.count(_.exists(_ != '.'))
          wt.preferredHeight(w).contains(drawn)
        }
        assertTrue(checks.forall(identity))
      }
    ),
    suite("styles")(
      test("each span draws in its own style, laid over the base style") {
        val buf = renderToBuffer(8, 1)(WrappedText(Spans("Name", Span("*", red)), style = bold))
        assertGrid(buf, "Name*...") &&
        assertCell(buf, 0, 0, Cell('N', bold)) &&
        assertCell(buf, 4, 0, Cell('*', red.over(bold)))
      },
      test("a span keeps its style on the line it wraps onto") {
        val buf = renderToBuffer(5, 2)(WrappedText(Spans("ab ", Span("cdef ghi", red))))
        assertGrid(buf, "ab...\ncdef.") &&
        assertStyle(buf, 0, 0, CellStyle.Empty) &&
        assertStyle(buf, 0, 1, red)
      }
    )
  )
