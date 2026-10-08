package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import geometry.Rect
import zio.Scope
import zio.test.*

object ScreenBufferSpec extends ZIOSpecDefault:

  private val redA = Cell('A', CellStyle(fg = Foreground.Named(FgColor.Red)))
  private val redB = Cell('B', CellStyle(fg = Foreground.Named(FgColor.Red)))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ScreenBuffer")(
    suite("construction")(
      test("buffer reports correct dimensions") {
        val b = ScreenBuffer.of(80, 24)
        assertTrue(b.width == 80, b.height == 24)
      },
      test("non-positive dimensions are rejected") {
        val zeroW = scala.util.Try(ScreenBuffer.of(0, 10))
        val negH = scala.util.Try(ScreenBuffer.of(10, -1))
        assertTrue(zeroW.isFailure, negH.isFailure)
      },
      test("new buffer is filled with Cell.Empty") {
        val b = ScreenBuffer.of(3, 2)
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.get(2, 1).contains(Cell.Empty)
        )
      }
    ),
    suite("get / set")(
      test("get returns the cell that was set") {
        val b = ScreenBuffer.of(5, 5)
        b.set(2, 3, redA)
        assertTrue(b.get(2, 3).contains(redA))
      },
      test("get returns None for out-of-bounds coordinates") {
        val b = ScreenBuffer.of(5, 5)
        assertTrue(
          b.get(-1, 0).isEmpty,
          b.get(0, -1).isEmpty,
          b.get(5, 0).isEmpty,
          b.get(0, 5).isEmpty
        )
      },
      test("out-of-bounds set is silently discarded") {
        val b = ScreenBuffer.of(5, 5)
        b.set(10, 10, redA)
        b.set(-1, 0, redA)
        // No exception; in-bounds reads still return Empty
        assertTrue(b.get(0, 0).contains(Cell.Empty))
      }
    ),
    suite("fill")(
      test("fills a rectangle and leaves outside cells untouched") {
        val b = ScreenBuffer.of(10, 10)
        b.fill(Rect(2, 2, 3, 2), redA)
        assertTrue(
          b.get(2, 2).contains(redA),
          b.get(4, 3).contains(redA),
          b.get(1, 2).contains(Cell.Empty),
          b.get(5, 2).contains(Cell.Empty),
          b.get(2, 4).contains(Cell.Empty)
        )
      },
      test("fill clips to buffer bounds") {
        val b = ScreenBuffer.of(5, 5)
        b.fill(Rect(3, 3, 10, 10), redA)
        assertTrue(
          b.get(3, 3).contains(redA),
          b.get(4, 4).contains(redA)
        )
      },
      test("empty rect is a no-op") {
        val b = ScreenBuffer.of(5, 5)
        b.fill(Rect(0, 0, 0, 0), redA)
        assertTrue(b.get(0, 0).contains(Cell.Empty))
      }
    ),
    suite("clearCells")(
      test("resets every cell to Empty") {
        val b = ScreenBuffer.of(3, 3)
        b.set(0, 0, redA)
        b.set(2, 2, redB)
        b.clearCells()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.get(2, 2).contains(Cell.Empty)
        )
      },
      test("preserves scroll-region declaration") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.set(0, 0, redA)
        b.clearCells()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.scrollRegion.contains(ScrollRegion(1, 3))
        )
      }
    ),
    suite("reset")(
      test("wipes cells AND scroll-region declaration") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.set(0, 0, redA)
        b.reset()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.scrollRegion.isEmpty
        )
      }
    ),
    suite("clearOutsideRegion")(
      test("with no region, wipes all cells (same as clearCells)") {
        val b = ScreenBuffer.of(3, 3)
        b.set(0, 0, redA)
        b.set(2, 2, redB)
        b.clearOutsideRegion()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.get(2, 2).contains(Cell.Empty)
        )
      },
      test("with active region, preserves cells inside the region") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.set(0, 1, redA)
        b.set(2, 2, redB)
        b.set(3, 3, redA)
        b.clearOutsideRegion()
        assertTrue(
          b.get(0, 1).contains(redA),
          b.get(2, 2).contains(redB),
          b.get(3, 3).contains(redA)
        )
      },
      test("with active region, wipes cells outside the region") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.set(0, 0, redA) // outside (above region)
        b.set(0, 1, redA) // inside
        b.set(0, 4, redA) // outside (below region)
        b.clearOutsideRegion()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.get(0, 1).contains(redA),
          b.get(0, 4).contains(Cell.Empty)
        )
      },
      test("preserves the scroll-region declaration") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.clearOutsideRegion()
        assertTrue(b.scrollRegion.contains(ScrollRegion(1, 3)))
      }
    ),
    suite("diff with pending scroll lines")(
      test("skips cell ops in region rows when pending lines exist") {
        val current = ScreenBuffer.of(4, 5)
        val previous = ScreenBuffer.of(4, 5)
        current.setScrollRegion(ScrollRegion(1, 3))
        // Make cells differ inside region rows
        current.set(0, 1, redA)
        current.set(0, 2, redB)
        // And outside the region too
        current.set(0, 0, redA)
        current.set(0, 4, redB)
        // Enqueue a scroll line — this should make the diff skip region rows
        current.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("YYYY")))

        val ops = current.diff(previous)
        assertTrue(
          // Cell ops only for rows OUTSIDE the region
          ops.contains(RenderOp.Cell(0, 0, redA)),
          ops.contains(RenderOp.Cell(0, 4, redB)),
          // Cells inside region rows are NOT emitted as cell ops
          !ops.contains(RenderOp.Cell(0, 1, redA)),
          !ops.contains(RenderOp.Cell(0, 2, redB))
        )
      },
      test("emits region-row cell ops normally when no pending lines") {
        val current = ScreenBuffer.of(4, 5)
        val previous = ScreenBuffer.of(4, 5)
        current.setScrollRegion(ScrollRegion(1, 3))
        current.set(0, 1, redA)
        // No pending scroll lines → cell-diff covers the whole buffer
        val ops = current.diff(previous)
        assertTrue(ops.contains(RenderOp.Cell(0, 1, redA)))
      }
    ),
    suite("scrollRegion")(
      test("starts as None on a fresh buffer") {
        assertTrue(ScreenBuffer.of(4, 5).scrollRegion.isEmpty)
      },
      test("setScrollRegion / clearScrollRegion round-trip") {
        val b = ScreenBuffer.of(4, 5)
        val region = ScrollRegion(1, 3)
        b.setScrollRegion(region)
        val afterSet = b.scrollRegion
        b.clearScrollRegion()
        val afterClear = b.scrollRegion
        assertTrue(
          afterSet.contains(region),
          afterClear.isEmpty
        )
      },
      test("setScrollRegion rejects bottom outside buffer height") {
        val b = ScreenBuffer.of(4, 5)
        assertTrue(scala.util.Try(b.setScrollRegion(ScrollRegion(1, 5))).isFailure)
      }
    ),
    suite("appendLineInRegion")(
      test("shifts rows up and writes the new line at the bottom") {
        val b = ScreenBuffer.of(4, 5)
        val region = ScrollRegion(1, 3)
        // Pre-fill region rows 1, 2, 3 with distinct content
        b.set(0, 1, Cell('a'))
        b.set(0, 2, Cell('b'))
        b.set(0, 3, Cell('c'))
        b.appendLineInRegion(region, Line.text("XXXX"))
        assertTrue(
          // Row 1 took row 2's old content
          b.get(0, 1).contains(Cell('b')),
          // Row 2 took row 3's old content
          b.get(0, 2).contains(Cell('c')),
          // Row 3 (bottom) holds the new line's first cell
          b.get(0, 3).contains(Cell('X')),
          b.get(3, 3).contains(Cell('X'))
        )
      },
      test("does not touch rows outside the supplied region") {
        val b = ScreenBuffer.of(4, 5)
        b.set(0, 0, Cell('T'))
        b.set(0, 4, Cell('B'))
        b.appendLineInRegion(ScrollRegion(1, 3), Line.text("XXXX"))
        assertTrue(
          b.get(0, 0).contains(Cell('T')),
          b.get(0, 4).contains(Cell('B'))
        )
      },
      test("does not require the buffer's stored scrollRegion to be set") {
        val b = ScreenBuffer.of(4, 5)
        // No setScrollRegion call — the operation is purely parameterised
        b.appendLineInRegion(ScrollRegion(1, 3), Line.text("XXXX"))
        assertTrue(
          b.get(0, 3).contains(Cell('X')),
          b.scrollRegion.isEmpty
        )
      },
      test("throws when region bottom exceeds buffer height") {
        val b = ScreenBuffer.of(4, 5)
        assertTrue(
          scala.util.Try(b.appendLineInRegion(ScrollRegion(1, 5), Line.text("XXXX"))).isFailure
        )
      },
      test("throws when line width does not match buffer width") {
        val b = ScreenBuffer.of(4, 5)
        assertTrue(
          scala.util.Try(b.appendLineInRegion(ScrollRegion(1, 3), Line.text("XX"))).isFailure,
          scala.util.Try(b.appendLineInRegion(ScrollRegion(1, 3), Line.text("XXXXXX"))).isFailure
        )
      }
    ),
    suite("pendingScrollLines")(
      test("starts empty on a fresh buffer") {
        assertTrue(ScreenBuffer.of(4, 5).pendingScrollLines.isEmpty)
      },
      test("enqueueScrollLine appends to the queue in order") {
        val b = ScreenBuffer.of(4, 5)
        val region = ScrollRegion(1, 3)
        val l1 = Line.text("AAAA")
        val l2 = Line.text("BBBB")
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(region, l1))
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(region, l2))
        assertTrue(
          b.pendingScrollLines.size == 2,
          b.pendingScrollLines(0) == RenderOp.ScrollRegionLine(region, l1),
          b.pendingScrollLines(1) == RenderOp.ScrollRegionLine(region, l2)
        )
      },
      test("clearPendingScrollLines drops the queue") {
        val b = ScreenBuffer.of(4, 5)
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("AAAA")))
        b.clearPendingScrollLines()
        assertTrue(b.pendingScrollLines.isEmpty)
      },
      test("enqueueScrollLine validates region bottom and line width") {
        val b = ScreenBuffer.of(4, 5)
        val regionTooBig = scala.util.Try(
          b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 5), Line.text("XXXX")))
        )
        val lineTooNarrow = scala.util.Try(
          b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("XX")))
        )
        assertTrue(regionTooBig.isFailure, lineTooNarrow.isFailure)
      },
      test("setScrollRegion clears the pending queue when the region changes") {
        val b = ScreenBuffer.of(4, 5)
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("AAAA")))
        b.setScrollRegion(ScrollRegion(0, 4))
        assertTrue(b.pendingScrollLines.isEmpty)
      },
      test("setScrollRegion preserves the pending queue when called with the same region") {
        val b: ScreenBuffer = ScreenBuffer.of(4, 5)
        val region = ScrollRegion(1, 3)
        val op: RenderOp.ScrollRegionLine = RenderOp.ScrollRegionLine(region, Line.text("AAAA"))
        b.setScrollRegion(region)
        b.enqueueScrollLine(op)
        b.setScrollRegion(region)
        assertTrue(b.pendingScrollLines == Seq(op))
      },
      test("clearScrollRegion clears the pending queue") {
        val b = ScreenBuffer.of(4, 5)
        b.setScrollRegion(ScrollRegion(1, 3))
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("AAAA")))
        b.clearScrollRegion()
        assertTrue(b.pendingScrollLines.isEmpty)
      },
      test("reset clears the pending queue") {
        val b = ScreenBuffer.of(4, 5)
        b.enqueueScrollLine(RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("AAAA")))
        b.reset()
        assertTrue(b.pendingScrollLines.isEmpty)
      },
      test("clearCells preserves the pending queue") {
        val b: ScreenBuffer = ScreenBuffer.of(4, 5)
        val op: RenderOp.ScrollRegionLine = RenderOp.ScrollRegionLine(ScrollRegion(1, 3), Line.text("AAAA"))
        b.enqueueScrollLine(op)
        b.clearCells()
        assertTrue(b.pendingScrollLines == Seq(op))
      }
    ),
    suite("diff")(
      test("identical buffers produce no updates") {
        val a = ScreenBuffer.of(4, 3)
        val b = ScreenBuffer.of(4, 3)
        a.set(1, 1, redA)
        b.set(1, 1, redA)
        assertTrue(a.diff(b).isEmpty)
      },
      test("single changed cell produces one update") {
        val current = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        current.set(1, 1, redA)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 1,
          updates.head == RenderOp.Cell(1, 1, redA)
        )
      },
      test("cell removed in current produces an Empty update") {
        val current = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        previous.set(2, 0, redA)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 1,
          updates.head == RenderOp.Cell(2, 0, Cell.Empty)
        )
      },
      test("multiple changes produce updates in row-major order") {
        val current = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        current.set(2, 0, redA)
        current.set(0, 1, redB)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 2,
          updates(0) == RenderOp.Cell(2, 0, redA),
          updates(1) == RenderOp.Cell(0, 1, redB)
        )
      }
    )
  )
