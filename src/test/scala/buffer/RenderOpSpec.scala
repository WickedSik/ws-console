package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import zio.Scope
import zio.test.*

object RenderOpSpec extends ZIOSpecDefault:

  private val red    = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val region = ScrollRegion(3, 22)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RenderOp")(

    suite("Cell")(
      test("carries position and cell payload") {
        val op: RenderOp.Cell = RenderOp.Cell(3, 7, Cell('x', red))
        assertTrue(op.x == 3, op.y == 7, op.cell == Cell('x', red))
      },

      test("equal cell ops compare equal") {
        val a: RenderOp = RenderOp.Cell(0, 0, Cell.Empty)
        val b: RenderOp = RenderOp.Cell(0, 0, Cell.Empty)
        assertTrue(a == b, a.hashCode == b.hashCode)
      },

      test("cell ops differing in any field compare unequal") {
        val a: RenderOp = RenderOp.Cell(0, 0, Cell('a'))
        assertTrue(
          a != RenderOp.Cell(1, 0, Cell('a')),
          a != RenderOp.Cell(0, 1, Cell('a')),
          a != RenderOp.Cell(0, 0, Cell('b'))
        )
      }
    ),

    suite("SetScrollRegion")(
      test("carries the supplied region") {
        val op: RenderOp.SetScrollRegion = RenderOp.SetScrollRegion(region)
        assertTrue(op.region == region)
      },

      test("equal region ops compare equal") {
        val a: RenderOp = RenderOp.SetScrollRegion(region)
        val b: RenderOp = RenderOp.SetScrollRegion(region)
        assertTrue(a == b)
      }
    ),

    suite("ResetScrollRegion")(
      test("is a singleton") {
        assertTrue(RenderOp.ResetScrollRegion eq RenderOp.ResetScrollRegion)
      }
    ),

    suite("ScrollRegionLine")(
      test("carries region and line payload") {
        val line                          = Line.text("hello", red)
        val op: RenderOp.ScrollRegionLine = RenderOp.ScrollRegionLine(region, line)
        assertTrue(op.region == region, op.line == line)
      },

      test("equal scroll-line ops compare equal") {
        val a: RenderOp = RenderOp.ScrollRegionLine(region, Line.text("hi"))
        val b: RenderOp = RenderOp.ScrollRegionLine(region, Line.text("hi"))
        assertTrue(a == b)
      }
    ),

    suite("variants are distinct types")(
      test("pattern matching dispatches by variant") {
        val ops: Seq[RenderOp] = Seq(
          RenderOp.Cell(0, 0, Cell.Empty),
          RenderOp.SetScrollRegion(region),
          RenderOp.ResetScrollRegion,
          RenderOp.ScrollRegionLine(region, Line.Empty)
        )
        val tags = ops.map {
          case _: RenderOp.Cell             => "cell"
          case _: RenderOp.SetScrollRegion  => "set"
          case RenderOp.ResetScrollRegion   => "reset"
          case _: RenderOp.ScrollRegionLine => "line"
        }
        assertTrue(tags == Seq("cell", "set", "reset", "line"))
      }
    )
  )
