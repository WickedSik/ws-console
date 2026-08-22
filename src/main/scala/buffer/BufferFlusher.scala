package io.github.wickedsik.wsconsole
package buffer

import ansi.{AnsiBuilder, Sgr}

/**
 * Translates a sequence of [[RenderOp]]s into ANSI output.
 *
 * '''Stateless contract.''' Every op emits its own cursor-position
 * escape regardless of where the cursor "should" be — the redundant
 * `moveTo` bytes are part of the contract.
 *
 * '''One escape per style.''' Reset + cell style share a single SGR:
 * `Sgr.Reset ++ style` renders as `ESC[0;1;33m`, not three separate
 * sequences. See [[ansi.Sgr]].
 *
 * Op dispatch:
 *   - `Cell(x, y, cell)` → `moveTo(y+1, x+1) + sgr(reset ++ style) + cell.text`
 *     (`cell.text` is a grapheme cluster; may be more than one `Char` for
 *     supplementary codepoints and base+VS / ZWJ sequences). Cells whose
 *     `text` is empty are continuation markers for a wide grapheme in the
 *     previous cell — the flusher emits nothing for them, because the
 *     terminal cursor has already advanced past that column.
 *   - `SetScrollRegion(region)` → `AnsiBuilder.setScrollRegion(top+1, bottom+1)`
 *   - `ResetScrollRegion` → `AnsiBuilder.resetScrollRegion`
 *   - `ScrollRegionLine(region, line)` → `moveTo(bottom+1, 1) + cells + "\n"`
 *     (the trailing newline is the DECSTBM scroll trigger)
 */
object BufferFlusher:

  /**
   * Build an [[AnsiBuilder]] that applies all `ops`.
   *
   * `parkAt` is an optional 0-indexed `(x, y)` the cursor moves to
   * after the final op. Defaults to `None`.
   *
   * '''Cursor park.''' A small diff leaves the cursor mid-screen; any
   * process sharing the TTY that emits a cursor-relative erase (e.g.
   * `sbt`'s `ED 0`) destroys everything below. Parking at the bottom-
   * right corner reduces the blast radius to a single cell. This is
   * defence in depth, not a fix — the fix is not sharing the TTY with
   * a foreign writer.
   */
  def toAnsi(ops: Seq[RenderOp], parkAt: Option[(Int, Int)] = None): AnsiBuilder =
    if ops.isEmpty then AnsiBuilder()
    else
      val withOps = ops.foldLeft(AnsiBuilder()) { (b, op) =>
        op match
          case RenderOp.Cell(_, _, cell) if cell.isContinuation =>
            // Continuation of a wide grapheme in the cell to the left — the
            // terminal has already occupied this column, so emit nothing.
            b

          case RenderOp.Cell(x, y, cell) =>
            // 0-indexed cell coordinates → 1-indexed terminal coordinates.
            b.moveTo(y + 1, x + 1)
              .raw((Sgr.Reset ++ cell.style.sgr).toAnsi)
              .text(cell.text)

          case RenderOp.SetScrollRegion(region) =>
            b.setScrollRegion(region.top + 1, region.bottom + 1)

          case RenderOp.ResetScrollRegion =>
            b.resetScrollRegion

          case RenderOp.ScrollRegionLine(region, line) =>
            // Scroll region up by one, then place the new line at the
            // bottom row — mirrors `appendLineInRegion`.
            val scrolled = b.scrollUp
            val placed   = scrolled.moveTo(region.bottom + 1, 1)
            line.cells.foldLeft(placed) { (acc, cell) =>
              if cell.isContinuation then acc
              else
                acc
                  .raw((Sgr.Reset ++ cell.style.sgr).toAnsi)
                  .text(cell.text)
            }
      }
      val reset = withOps.reset
      parkAt.fold(reset) { (x, y) => reset.moveTo(y + 1, x + 1) }
