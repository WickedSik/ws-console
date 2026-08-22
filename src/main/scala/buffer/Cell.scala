package io.github.wickedsik.wsconsole
package buffer

/**
 * A single rendered position with styling.
 *
 * `text` is a grapheme cluster — typically one `Char` (ASCII, BMP), but for
 * codepoints outside the BMP it is a surrogate pair (`"🧹"` = two `Char`s),
 * and for grapheme sequences it may be a base codepoint followed by variation
 * selectors, combining marks, or ZWJ-joined codepoints (`"⚠️"`, `"👨‍👩‍👧"`).
 * Callers that produce visible text via [[Canvas.putText]] never construct
 * cells directly — the canvas iterates grapheme clusters via
 * `java.text.BreakIterator` and packs each cluster into one cell.
 *
 * '''Column width is out of scope for this type.''' A `Cell` occupies exactly
 * one buffer slot regardless of how many terminal columns the terminal chooses
 * to render its `text` in. East-Asian wide characters and most emoji occupy
 * two terminal columns but only one `Cell`; the visual next-column overlap is
 * documented and awaits a separate width-tracking pass in the render pipeline.
 */
final case class Cell(
  text:  String,
  style: CellStyle = CellStyle.Empty
):

  /**
   * Legacy accessor: the first `Char` of `text`, or space for an empty cell.
   * Retained so test harnesses and any single-char consumer (progress bar
   * shading, box-drawing character reads) that predate the grapheme-cluster
   * representation keep compiling. For a supplementary codepoint this returns
   * only the high surrogate; use [[text]] for full-codepoint work.
   */
  def char: Char = if text.isEmpty then ' ' else text.charAt(0)

  /**
   * Whether this cell is a continuation of a wide grapheme in the cell to its
   * left. Continuation cells hold empty text — the flusher emits no bytes for
   * them, since the terminal cursor is already past this column after drawing
   * the wide char.
   */
  def isContinuation: Boolean = text.isEmpty

object Cell:
  /** A blank cell — a space with no styling. Default fill for buffers. */
  val Empty: Cell = Cell(" ")

  /**
   * Char-based factory retained so single-`Char` producers stay ergonomic:
   * box-drawing glyphs, progress-bar fill chars, the space that clears a
   * region, and every existing `Cell('X', style)` call site keep compiling
   * without churn.
   */
  def apply(char: Char): Cell = Cell(char.toString, CellStyle.Empty)

  /** Char-based factory with an explicit style. */
  def apply(char: Char, style: CellStyle): Cell = Cell(char.toString, style)
