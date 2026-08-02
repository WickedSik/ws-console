package io.github.wickedsik.wsconsole
package buffer

import ansi.{AnsiBuilder, Sgr}

/**
 * Translates a sequence of [[RenderOp]]s into ANSI output.
 *
 * **Stateless contract.** Every op emits its own cursor-position escape
 * regardless of where the cursor "should" be. This holds uniformly for
 * cell ops and scroll-line ops: the redundant `moveTo` bytes are part of
 * the contract. Cursor and active-style coalescing across ops is left for
 * a later optimisation pass (deferred per the task scroll's
 * `Deferred / Follow-up` section).
 *
 * '''One escape per style.''' The reset and the cell's own style share a
 * single SGR sequence: `Sgr.Reset ++ style` renders as `ESC[0;1;33m`, not
 * `ESC[0m` followed by `ESC[1m` followed by `ESC[33m`. An unstyled cell
 * still emits exactly `ESC[0m`, so the plain case is byte-identical to the
 * pre-merge flusher. See [[ansi.Sgr]].
 *
 * Op dispatch:
 *   - `Cell(x, y, cell)` → `moveTo(y+1, x+1) + sgr(reset ++ style) + char`
 *   - `SetScrollRegion(region)` → `AnsiBuilder.setScrollRegion(top+1, bottom+1)`
 *   - `ResetScrollRegion` → `AnsiBuilder.resetScrollRegion`
 *   - `ScrollRegionLine(region, line)` → `moveTo(bottom+1, 1) + cells + "\n"`
 *     (the trailing newline is the DECSTBM scroll trigger)
 */
object BufferFlusher:

  /**
   * Build an [[AnsiBuilder]] that, when written, applies all `ops`.
   *
   * `parkAt` is an optional 0-indexed `(x, y)` the cursor is moved to
   * after the final op — see [[toAnsi]]'s cursor-park contract below.
   * Defaults to `None`: the cursor is left wherever the last op put it.
   *
   * '''Cursor park (2026-07-31).''' A frame's last cell write leaves the
   * cursor in the middle of the screen whenever the diff is small. Any
   * process sharing the TTY that emits a *cursor-relative* erase then
   * destroys everything below that point. This is not hypothetical: `sbt`
   * appends `ED 0` (`erase from cursor to end of screen`) after our writes
   * when the demo runs unforked in sbt's JVM, and a Tab that repainted
   * only row 14 cost rows 15–24 — the demo's whole toolbar. See
   * `.claude/tasks/demo-toolbar-disappearance.md`.
   *
   * Parking at the bottom-right corner reduces that blast radius from
   * "the rest of the screen" to a single cell — `ED 0` erases from the
   * cursor *inclusive*, so no park position makes it a true no-op. This
   * is defence in depth, not a fix: the fix is not sharing the TTY with a
   * process that writes to it (`scripts/run-demo.sh`).
   */
  def toAnsi(ops: Seq[RenderOp], parkAt: Option[(Int, Int)] = None): AnsiBuilder =
    if ops.isEmpty then AnsiBuilder()
    else
      val withOps = ops.foldLeft(AnsiBuilder()) { (b, op) =>
        op match
          case RenderOp.Cell(x, y, cell) =>
            // 0-indexed cell coordinates → 1-indexed terminal coordinates.
            b.moveTo(y + 1, x + 1)
              .raw((Sgr.Reset ++ cell.style.sgr).toAnsi)
              .text(cell.char.toString)

          case RenderOp.SetScrollRegion(region) =>
            b.setScrollRegion(region.top + 1, region.bottom + 1)

          case RenderOp.ResetScrollRegion =>
            b.resetScrollRegion

          case RenderOp.ScrollRegionLine(region, line) =>
            // Scroll the region's content up by one (top discarded, bottom
            // becomes blank), then place the new line at the bottom row.
            // This matches the buffer's `appendLineInRegion` model exactly:
            // post-emission, the line lives at the region's bottom row.
            val scrolled = b.scrollUp
            val placed   = scrolled.moveTo(region.bottom + 1, 1)
            line.cells.foldLeft(placed) { (acc, cell) =>
              acc
                .raw((Sgr.Reset ++ cell.style.sgr).toAnsi)
                .text(cell.char.toString)
            }
      }
      val reset = withOps.reset
      parkAt.fold(reset) { (x, y) => reset.moveTo(y + 1, x + 1) }
