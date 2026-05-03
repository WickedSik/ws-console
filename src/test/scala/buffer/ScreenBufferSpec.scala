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
        val negH  = scala.util.Try(ScreenBuffer.of(10, -1))
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

    suite("clear")(
      test("clear resets every cell to Empty") {
        val b = ScreenBuffer.of(3, 3)
        b.set(0, 0, redA)
        b.set(2, 2, redB)
        b.clear()
        assertTrue(
          b.get(0, 0).contains(Cell.Empty),
          b.get(2, 2).contains(Cell.Empty)
        )
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
        val current  = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        current.set(1, 1, redA)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 1,
          updates.head == CellUpdate(1, 1, redA)
        )
      },

      test("cell removed in current produces an Empty update") {
        val current  = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        previous.set(2, 0, redA)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 1,
          updates.head == CellUpdate(2, 0, Cell.Empty)
        )
      },

      test("multiple changes produce updates in row-major order") {
        val current  = ScreenBuffer.of(3, 3)
        val previous = ScreenBuffer.of(3, 3)
        current.set(2, 0, redA)
        current.set(0, 1, redB)
        val updates = current.diff(previous)
        assertTrue(
          updates.size == 2,
          updates.head.x == 2 && updates.head.y == 0,
          updates(1).x == 0 && updates(1).y == 1
        )
      }
    )
  )
