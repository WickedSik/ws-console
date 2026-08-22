package io.github.wickedsik.wsconsole
package buffer

import zio.test.*

/**
 * Integration coverage that `Canvas.putText` packs a supplementary codepoint
 * into a SINGLE cell (rather than two half-surrogate cells) and that
 * `BufferFlusher` re-emits the full grapheme text on the wire.
 *
 * Regression pin for the "🧹 renders as ??" bug: before the grapheme fix
 * the pair was split by `text.charAt(i)` iteration.
 */
object CanvasGraphemeSpec extends ZIOSpecDefault:

  def spec = suite("Canvas + BufferFlusher: grapheme cluster round-trip")(
    test("putText writes a supplementary emoji into one cell as full text") {
      val buffer = ScreenBuffer.of(5, 1)
      val canvas = Canvas(buffer)
      canvas.putText(0, 0, "🧹ok", CellStyle.Empty)

      // 🧹 is wide (2 columns) so it also reserves cell (1, 0) as a
      // continuation marker. "o" and "k" therefore land at (2, 0) and (3, 0).
      assertTrue(
        buffer.get(0, 0).map(_.text) == Some("🧹"),
        buffer.get(1, 0).exists(_.isContinuation),
        buffer.get(2, 0).map(_.text) == Some("o"),
        buffer.get(3, 0).map(_.text) == Some("k")
      )
    },
    test("BufferFlusher emits the full grapheme text, not a truncated Char") {
      val buffer = ScreenBuffer.of(3, 1)
      val canvas = Canvas(buffer)
      canvas.putText(0, 0, "🧹", CellStyle.Empty)

      val ops   = buffer.diffAll
      val bytes = BufferFlusher.toAnsi(ops).build

      // The emitted stream must contain the full "🧹" as its raw UTF-16 pair
      // (which serialise to the correct UTF-8 bytes at the terminal boundary).
      assertTrue(bytes.contains("🧹"))
    }
  )
