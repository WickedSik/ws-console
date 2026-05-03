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
        updates.head == CellUpdate(1, 1, redA)
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
    }
  )
