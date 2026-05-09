package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import zio.Scope
import zio.test.*

object BufferManagerSpec extends ZIOSpecDefault:

  private val redA = Cell('A', CellStyle(fg = Foreground.Named(FgColor.Red)))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BufferManager")(

    test("current and previous start blank") {
      val m = BufferManager.of(4, 3)
      assertTrue(
        m.current.get(0, 0).contains(Cell.Empty),
        m.previous.get(0, 0).contains(Cell.Empty)
      )
    },

    test("diff delegates to current.diff(previous)") {
      val m = BufferManager.of(3, 3)
      m.current.set(1, 1, redA)
      val updates = m.diff()
      assertTrue(
        updates.size == 1,
        updates.head == RenderOp.Cell(1, 1, redA)
      )
    },

    test("swap rotates current and previous") {
      val m = BufferManager.of(3, 3)
      m.current.set(0, 0, redA)
      val originallyCurrent = m.current
      m.swap()
      // The buffer that held redA is now `previous`
      assertTrue(m.previous eq originallyCurrent, m.previous.get(0, 0).contains(redA))
    },

    test("swap clears the new current buffer") {
      val m = BufferManager.of(3, 3)
      // Pre-populate the buffer that will become the new current
      m.previous.set(2, 2, redA)
      m.swap()
      assertTrue(m.current.get(2, 2).contains(Cell.Empty))
    },

    test("two swaps return original buffer to current") {
      val m = BufferManager.of(3, 3)
      val originallyCurrent = m.current
      m.swap()
      m.swap()
      assertTrue(m.current eq originallyCurrent)
    },

    suite("scroll-region orchestration")(
      test("diff emits SetScrollRegion when current declares a region for the first time") {
        val m = BufferManager.of(4, 5)
        m.current.setScrollRegion(ScrollRegion(1, 3))
        val ops = m.diff()
        assertTrue(ops.headOption.contains(RenderOp.SetScrollRegion(ScrollRegion(1, 3))))
      },

      test("diff emits ResetScrollRegion when current loses an existing region") {
        val m = BufferManager.of(4, 5)
        // Frame 1: declare region, diff, swap
        m.current.setScrollRegion(ScrollRegion(1, 3))
        m.diff()
        m.swap()
        // Frame 2: clear the region on the new current
        m.current.clearScrollRegion()
        val ops = m.diff()
        assertTrue(ops.contains(RenderOp.ResetScrollRegion))
      },

      test("diff emits ScrollRegionLine ops drained from current's pending queue") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        val l1 = Line.text("AAAA")
        val l2 = Line.text("BBBB")
        m.current.enqueueScrollLine(RenderOp.ScrollRegionLine(region, l1))
        m.current.enqueueScrollLine(RenderOp.ScrollRegionLine(region, l2))
        val ops = m.diff()
        assertTrue(
          ops.contains(RenderOp.ScrollRegionLine(region, l1)),
          ops.contains(RenderOp.ScrollRegionLine(region, l2))
        )
      },

      test("swap propagates the scroll-region declaration to the new current") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        m.swap()
        assertTrue(m.current.scrollRegion.contains(region))
      },

      test("swap clears pending queue on the buffer that becomes previous") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        m.current.enqueueScrollLine(RenderOp.ScrollRegionLine(region, Line.text("AAAA")))
        m.swap()
        assertTrue(m.previous.pendingScrollLines.isEmpty)
      },

      test("closure-loop: no spurious SetScrollRegion between frames in steady state") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        val ops1 = m.diff()
        m.swap()
        // Frame 2: nothing changed; region should NOT be re-declared
        val ops2 = m.diff()
        assertTrue(
          ops1.contains(RenderOp.SetScrollRegion(region)),
          !ops2.exists {
            case _: RenderOp.SetScrollRegion => true
            case _                           => false
          }
        )
      },

      test("swap preserves cells inside the active region (so mirror accumulation survives)") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        // Stage accumulated content on the buffer that will become new current
        m.previous.setScrollRegion(region)
        m.previous.set(0, 1, redA)
        m.previous.set(0, 2, redA)
        m.previous.set(0, 3, redA)
        m.swap()
        // The just-swapped current must retain its in-region cells
        assertTrue(
          m.current.scrollRegion.contains(region),
          m.current.get(0, 1).contains(redA),
          m.current.get(0, 2).contains(redA),
          m.current.get(0, 3).contains(redA)
        )
      },

      test("swap wipes cells outside the active region on the new current") {
        val m      = BufferManager.of(4, 5)
        val region = ScrollRegion(1, 3)
        m.current.setScrollRegion(region)
        m.previous.setScrollRegion(region)
        m.previous.set(0, 0, redA) // outside (above region)
        m.previous.set(0, 4, redA) // outside (below region)
        m.swap()
        assertTrue(
          m.current.get(0, 0).contains(Cell.Empty),
          m.current.get(0, 4).contains(Cell.Empty)
        )
      }
    )
  )
