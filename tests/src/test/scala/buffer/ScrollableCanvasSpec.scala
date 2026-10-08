package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import geometry.Rect
import zio.Scope
import zio.test.*

object ScrollableCanvasSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ScrollableCanvas")(
    suite("Canvas.scrollRegion factory")(
      test("declares the region on the underlying buffer") {
        val buffer = ScreenBuffer.of(4, 5)
        val canvas = Canvas(buffer)
        canvas.scrollRegion(top = 1, bottom = 3)
        assertTrue(buffer.scrollRegion.contains(ScrollRegion(1, 3)))
      },
      test("rejects top < 0") {
        val canvas = Canvas(ScreenBuffer.of(4, 5))
        assertTrue(scala.util.Try(canvas.scrollRegion(-1, 3)).isFailure)
      },
      test("rejects bottom >= canvas height") {
        val canvas = Canvas(ScreenBuffer.of(4, 5))
        assertTrue(scala.util.Try(canvas.scrollRegion(1, 5)).isFailure)
      },
      test("rejects bottom < top") {
        val canvas = Canvas(ScreenBuffer.of(4, 5))
        assertTrue(scala.util.Try(canvas.scrollRegion(3, 1)).isFailure)
      },
      test("translates canvas-local rows to buffer-global rows on a subCanvas") {
        val buffer = ScreenBuffer.of(4, 10)
        val sub = Canvas(buffer).subCanvas(Rect(0, 5, 4, 4))
        sub.scrollRegion(top = 0, bottom = 2)
        assertTrue(buffer.scrollRegion.contains(ScrollRegion(5, 7)))
      }
    ),
    suite("appendLine")(
      test("shifts buffer rows up and writes the new line in the bottom row") {
        val buffer = ScreenBuffer.of(4, 5)
        val canvas = Canvas(buffer)
        // Pre-fill region rows
        buffer.set(0, 1, Cell('a'))
        buffer.set(0, 2, Cell('b'))
        buffer.set(0, 3, Cell('c'))
        val scroller = canvas.scrollRegion(1, 3)
        scroller.appendLine(Line.text("XXXX"))
        assertTrue(
          buffer.get(0, 1).contains(Cell('b')),
          buffer.get(0, 2).contains(Cell('c')),
          buffer.get(0, 3).contains(Cell('X')),
          buffer.get(3, 3).contains(Cell('X'))
        )
      },
      test("queues a ScrollRegionLine op on the buffer") {
        val buffer = ScreenBuffer.of(4, 5)
        val scroller = Canvas(buffer).scrollRegion(1, 3)
        scroller.appendLine(Line.text("XXXX", red))
        val pending = buffer.pendingScrollLines
        assertTrue(
          pending.size == 1,
          pending.head.region == ScrollRegion(1, 3),
          pending.head.line.cells.forall(_.style == red),
          pending.head.line.width == 4
        )
      },
      test("pads a short line to buffer width with Cell.Empty") {
        val buffer = ScreenBuffer.of(6, 5)
        val scroller = Canvas(buffer).scrollRegion(1, 3)
        scroller.appendLine(Line.text("ab"))
        val emitted = buffer.pendingScrollLines.head.line
        assertTrue(
          emitted.width == 6,
          emitted.cells(0) == Cell('a'),
          emitted.cells(1) == Cell('b'),
          emitted.cells(2) == Cell.Empty,
          emitted.cells(5) == Cell.Empty
        )
      },
      test("truncates a long line to buffer width") {
        val buffer = ScreenBuffer.of(3, 5)
        val scroller = Canvas(buffer).scrollRegion(1, 3)
        scroller.appendLine(Line.text("abcdef"))
        val emitted = buffer.pendingScrollLines.head.line
        assertTrue(
          emitted.width == 3,
          emitted.cells(0) == Cell('a'),
          emitted.cells(2) == Cell('c')
        )
      }
    ),
    suite("clear")(
      test("fills region cells with Cell.Empty and tears down the region") {
        val buffer = ScreenBuffer.of(4, 5)
        val scroller = Canvas(buffer).scrollRegion(1, 3)
        buffer.set(0, 1, Cell('A'))
        buffer.set(2, 2, Cell('B'))
        scroller.clear()
        assertTrue(
          buffer.get(0, 1).contains(Cell.Empty),
          buffer.get(2, 2).contains(Cell.Empty),
          buffer.scrollRegion.isEmpty
        )
      },
      test("does not touch cells outside the region") {
        val buffer = ScreenBuffer.of(4, 5)
        val scroller = Canvas(buffer).scrollRegion(1, 3)
        buffer.set(0, 0, Cell('T'))
        buffer.set(0, 4, Cell('B'))
        scroller.clear()
        assertTrue(
          buffer.get(0, 0).contains(Cell('T')),
          buffer.get(0, 4).contains(Cell('B'))
        )
      }
    )
  )
