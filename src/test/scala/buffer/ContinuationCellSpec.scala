package io.github.wickedsik.wsconsole
package buffer

import zio.test.*

/**
 * End-to-end coverage of the wide-grapheme + continuation-cell contract
 * across `Canvas.putText`, `ScreenBuffer.diff`, and `BufferFlusher.toAnsi`.
 *
 * Regression driver: even after graphemes reached the buffer intact, wide
 * chars still overlapped the next visual column because the flusher emitted
 * one moveTo-per-cell without accounting for the terminal cursor advancing
 * two columns after a wide glyph. Continuation cells (empty text) let the
 * flusher skip that column so the geometry lines up.
 */
object ContinuationCellSpec extends ZIOSpecDefault:

  def spec = suite("wide grapheme + continuation cell")(
    test("putText reserves a continuation cell to the right of a wide grapheme") {
      val buffer = ScreenBuffer.of(5, 1)
      Canvas(buffer).putText(0, 0, "A📊B", CellStyle.Empty)

      assertTrue(
        buffer.get(0, 0).map(_.text) == Some("A"),
        buffer.get(1, 0).map(_.text) == Some("📊"),
        buffer.get(2, 0).exists(_.isContinuation),
        buffer.get(3, 0).map(_.text) == Some("B")
      )
    },
    test("BufferFlusher emits nothing for a continuation cell") {
      val buffer = ScreenBuffer.of(3, 1)
      Canvas(buffer).putText(0, 0, "📊B", CellStyle.Empty)

      val bytes = BufferFlusher.toAnsi(buffer.diffAll).build

      // The continuation cell at (1, 0) must NOT produce a `moveTo(1, 2)` —
      // otherwise the terminal receives an addressed empty cell that
      // overwrites the emoji's right half. Assert on the addressed
      // positions decoded from the wire.
      val decoded = testkit.AnsiGrid.decode(bytes)
      assertTrue(
        decoded.contains((0, 0)), // 📊 addressed
        !decoded.contains((1, 0)), // continuation NOT addressed
        decoded.contains((2, 0)) // B addressed
      )
    },
    test("wide grapheme in an odd column still leaves geometry intact") {
      val buffer = ScreenBuffer.of(6, 1)
      Canvas(buffer).putText(0, 0, "AB📊CD", CellStyle.Empty)

      assertTrue(
        buffer.get(0, 0).map(_.text) == Some("A"),
        buffer.get(1, 0).map(_.text) == Some("B"),
        buffer.get(2, 0).map(_.text) == Some("📊"),
        buffer.get(3, 0).exists(_.isContinuation),
        buffer.get(4, 0).map(_.text) == Some("C"),
        buffer.get(5, 0).map(_.text) == Some("D")
      )
    },
    test("narrow chars produce no continuation cells") {
      val buffer = ScreenBuffer.of(3, 1)
      Canvas(buffer).putText(0, 0, "abc", CellStyle.Empty)

      assertTrue(
        buffer.get(0, 0).exists(!_.isContinuation),
        buffer.get(1, 0).exists(!_.isContinuation),
        buffer.get(2, 0).exists(!_.isContinuation)
      )
    }
  )
