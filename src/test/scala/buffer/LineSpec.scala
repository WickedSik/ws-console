package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import geometry.Rect
import zio.Scope
import zio.test.*

object LineSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val blue = CellStyle(fg = Foreground.Named(FgColor.Blue))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Line")(
    suite("Empty")(
      test("has zero cells") {
        assertTrue(Line.Empty.cells.isEmpty, Line.Empty.width == 0)
      }
    ),
    suite("text")(
      test("builds a line whose cells carry the supplied style") {
        val line = Line.text("hi", red)
        assertTrue(
          line.width == 2,
          line.cells == Seq(Cell('h', red), Cell('i', red))
        )
      },
      test("defaults to CellStyle.Empty when no style supplied") {
        val line = Line.text("ok")
        assertTrue(line.cells.forall(_.style == CellStyle.Empty))
      },
      test("empty string yields an empty line") {
        assertTrue(Line.text("").cells.isEmpty)
      }
    ),
    suite("cells")(
      test("preserves the supplied cell sequence verbatim") {
        val cs = Seq(Cell('a', red), Cell('b', blue))
        assertTrue(Line.cells(cs).cells == cs)
      }
    ),
    suite("runs")(
      test("concatenates heterogeneously-styled runs in order") {
        val line = Line.runs("ab" -> red, "cd" -> blue)
        assertTrue(
          line.width == 4,
          line.cells == Seq(
            Cell('a', red),
            Cell('b', red),
            Cell('c', blue),
            Cell('d', blue)
          )
        )
      },
      test("zero runs yields an empty line") {
        assertTrue(Line.runs().cells.isEmpty)
      }
    ),
    suite("fill")(
      test("builds a line of width matching rect.width filled with the supplied cell") {
        val line = Line.fill(Rect(0, 0, 4, 1), Cell('-', red))
        assertTrue(
          line.width == 4,
          line.cells.forall(_ == Cell('-', red))
        )
      },
      test("defaults to Cell.Empty when no fill supplied") {
        val line = Line.fill(Rect(0, 0, 3, 1))
        assertTrue(line.cells.forall(_ == Cell.Empty))
      },
      test("rejects rects with height other than 1") {
        val tall = scala.util.Try(Line.fill(Rect(0, 0, 4, 2)))
        val flat = scala.util.Try(Line.fill(Rect(0, 0, 4, 0)))
        assertTrue(tall.isFailure, flat.isFailure)
      }
    ),
    suite("at")(
      test("projects a line into a height-1 Rect at the given anchor") {
        val line = Line.text("abc")
        assertTrue(line.at(5, 7) == Rect(5, 7, 3, 1))
      },
      test("empty line projects to a zero-width Rect") {
        assertTrue(Line.Empty.at(0, 0) == Rect(0, 0, 0, 1))
      }
    )
  )
