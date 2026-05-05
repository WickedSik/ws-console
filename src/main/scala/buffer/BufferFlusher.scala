package io.github.wickedsik.wsconsole
package buffer

import ansi.AnsiBuilder

/**
 * Translates a sequence of [[CellUpdate]]s into ANSI output.
 *
 * For each update: emit a cursor-position escape, a style reset, the new
 * cell's style escape (if any), then the cell's character. The trailing
 * reset prevents subsequent terminal output from inheriting the last cell's
 * styling.
 *
 * This is a deliberately simple translation. A smarter version would track
 * cursor position and active style across updates to coalesce adjacent
 * cells and skip redundant escapes — left for a later optimisation pass.
 */
object BufferFlusher:

  /** Build an [[AnsiBuilder]] that, when written, applies all `updates`. */
  def toAnsi(updates: Seq[CellUpdate]): AnsiBuilder =
    if updates.isEmpty then AnsiBuilder()
    else
      val withUpdates = updates.foldLeft(AnsiBuilder()) { (b, u) =>
        // 0-indexed cell coordinates → 1-indexed terminal coordinates.
        val styleAnsi = u.cell.style.toAnsi
        val placed    = b.moveTo(u.y + 1, u.x + 1).reset
        val styled    = if styleAnsi.isEmpty then placed else placed.raw(styleAnsi)
        styled.text(u.cell.char.toString)
      }
      withUpdates.reset
