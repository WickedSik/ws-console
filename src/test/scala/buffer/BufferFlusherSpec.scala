package io.github.wickedsik.wsconsole
package buffer

import ansi.{AnsiBuilder, FgColor}
import zio.Scope
import zio.test.*

object BufferFlusherSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BufferFlusher")(

    test("empty op stream produces an empty AnsiBuilder") {
      assertTrue(BufferFlusher.toAnsi(Seq.empty).build.isEmpty)
    },

    suite("Cell ops")(
      test("Cell op produces moveTo + reset + char") {
        val ops      = Seq(RenderOp.Cell(2, 3, Cell('x')))
        val expected = AnsiBuilder().moveTo(4, 3).reset.text("x").reset.build
        assertTrue(BufferFlusher.toAnsi(ops).build == expected)
      },

      test("Cell op with style emits the style escape between reset and char") {
        val cell    = Cell('x', red)
        val ops     = Seq(RenderOp.Cell(0, 0, cell))
        val output  = BufferFlusher.toAnsi(ops).build
        // Output must contain the style ANSI somewhere before the character
        val styleAt = output.indexOf(red.toAnsi)
        val charAt  = output.indexOf("x")
        assertTrue(styleAt >= 0, charAt > styleAt)
      }
    ),

    suite("region ops")(
      test("SetScrollRegion delegates to AnsiBuilder.setScrollRegion with 1-indexed bounds") {
        val ops      = Seq(RenderOp.SetScrollRegion(ScrollRegion(2, 5)))
        val expected = AnsiBuilder().setScrollRegion(3, 6).reset.build
        assertTrue(BufferFlusher.toAnsi(ops).build == expected)
      },

      test("ResetScrollRegion delegates to AnsiBuilder.resetScrollRegion") {
        val ops      = Seq(RenderOp.ResetScrollRegion)
        val expected = AnsiBuilder().resetScrollRegion.reset.build
        assertTrue(BufferFlusher.toAnsi(ops).build == expected)
      }
    ),

    suite("ScrollRegionLine")(
      test("emits SU before moveTo, places cells at region bottom, no trailing newline") {
        val region = ScrollRegion(1, 3)
        val line   = Line.text("ab")
        val output = BufferFlusher.toAnsi(Seq(RenderOp.ScrollRegionLine(region, line))).build
        val suAt   = output.indexOf(AnsiBuilder().scrollUp.build)
        val moveAt = output.indexOf(AnsiBuilder().moveTo(4, 1).build)
        val aAt    = output.indexOf("a")
        val bAt    = output.indexOf("b")
        assertTrue(
          suAt    >= 0,
          moveAt   > suAt,
          aAt      > moveAt,
          bAt      > aAt,
          // No standalone newline characters in the cell-emission stream
          !output.substring(moveAt).contains("\n")
        )
      },

      test("a single-char line produces SU + moveTo + char + reset (no LF)") {
        val region = ScrollRegion(0, 2)
        val output = BufferFlusher.toAnsi(Seq(RenderOp.ScrollRegionLine(region, Line.text("x")))).build
        val xAt    = output.indexOf("x")
        assertTrue(xAt >= 0, !output.substring(xAt).contains("\n"))
      }
    ),

    suite("cursor park")(
      test("parkAt appends a moveTo after the trailing reset") {
        val ops      = Seq(RenderOp.Cell(2, 3, Cell('x')))
        val expected = AnsiBuilder().moveTo(4, 3).reset.text("x").reset.moveTo(10, 20).build
        // parkAt is 0-indexed (x, y), matching RenderOp.Cell.
        assertTrue(BufferFlusher.toAnsi(ops, parkAt = Some((19, 9))).build == expected)
      },

      test("omitting parkAt leaves the byte stream unchanged") {
        val ops = Seq(RenderOp.Cell(2, 3, Cell('x')))
        assertTrue(BufferFlusher.toAnsi(ops).build == BufferFlusher.toAnsi(ops, parkAt = None).build)
      },

      test("an empty op stream stays empty even when a park is requested") {
        assertTrue(BufferFlusher.toAnsi(Seq.empty, parkAt = Some((19, 9))).build.isEmpty)
      },

      test("the park is the last thing emitted after a multi-op stream") {
        val ops    = Seq(RenderOp.Cell(0, 0, Cell('a')), RenderOp.Cell(1, 0, Cell('b')))
        val output = BufferFlusher.toAnsi(ops, parkAt = Some((7, 5))).build
        assertTrue(output.endsWith(AnsiBuilder().moveTo(6, 8).build))
      }
    ),

    suite("mixed op streams")(
      test("processes ops in order") {
        val region = ScrollRegion(0, 2)
        val ops    = Seq(
          RenderOp.SetScrollRegion(region),
          RenderOp.ScrollRegionLine(region, Line.text("a")),
          RenderOp.Cell(0, 0, Cell('z'))
        )
        val output = BufferFlusher.toAnsi(ops).build
        val setAt  = output.indexOf(AnsiBuilder().setScrollRegion(1, 3).build)
        val lineAt = output.indexOf("a", math.max(0, setAt))
        val cellAt = output.indexOf("z", math.max(0, lineAt))
        assertTrue(setAt >= 0, lineAt > setAt, cellAt > lineAt)
      }
    )
  )
