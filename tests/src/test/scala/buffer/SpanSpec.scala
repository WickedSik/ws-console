package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor

import zio.Scope
import zio.test.*

object SpanSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val bold = CellStyle(attributes = Set(Attribute.Bold))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Span / StyledLine")(
    suite("CellStyle.over")(
      test("a set colour wins, Inherit falls through, attributes union") {
        val base = CellStyle(
          fg = Foreground.Named(FgColor.White),
          bg = Background.Indexed(17),
          attributes = Set(Attribute.Bold)
        )
        val span = CellStyle(fg = Foreground.Named(FgColor.Red), attributes = Set(Attribute.Underline))
        assertTrue(
          span.over(base) == CellStyle(
            fg = Foreground.Named(FgColor.Red),
            bg = Background.Indexed(17),
            attributes = Set(Attribute.Bold, Attribute.Underline)
          ),
          CellStyle.Empty.over(base) == base,
          span.over(CellStyle.Empty) == span
        )
      }
    ),
    suite("Spans")(
      test("plain strings become unstyled spans, spans pass through") {
        assertTrue(Spans("Name", Span("*", red)) == StyledLine(Vector(Span("Name"), Span("*", red))))
      }
    ),
    suite("StyledLine")(
      test("width counts columns across spans, CJK and emoji are two wide") {
        assertTrue(
          Spans("ab", Span("中文", red)).width == 6,
          Spans("Привет").width == 6,
          Spans("👍🏽").width == 2,
          StyledLine.Empty.width == 0
        )
      },
      test("text concatenates spans; isEmpty ignores empty spans") {
        assertTrue(
          Spans("a", Span("b", red)).text == "ab",
          Spans("", Span("", red)).isEmpty,
          !Spans("", " ").isEmpty
        )
      },
      test("over lays every span over the base") {
        assertTrue(
          Spans("a", Span("b", red)).over(bold) == StyledLine(Vector(Span("a", bold), Span("b", red.over(bold))))
        )
      },
      test("truncate keeps whole graphemes and stops at the first cut span") {
        val line = Spans(Span("ab中", red), Span("cd"))
        assertTrue(
          line.truncate(10) == line,
          line.truncate(4) == StyledLine(Vector(Span("ab中", red))),
          // 中 does not fit in column 3; "cd" must not slide into its place.
          line.truncate(3) == StyledLine(Vector(Span("ab", red))),
          line.truncate(0) == StyledLine.Empty
        )
      },
      test("draw advances by columns, each span in its own style") {
        val buffer = ScreenBuffer.of(6, 1)
        Spans(Span("中", red), Span("ab")).draw(Canvas(buffer), 1, 0)
        assertTrue(
          buffer.get(1, 0).contains(Cell("中", red)),
          buffer.get(2, 0).exists(_.isContinuation),
          buffer.get(3, 0).contains(Cell('a')),
          buffer.get(4, 0).contains(Cell('b'))
        )
      }
    )
  )
