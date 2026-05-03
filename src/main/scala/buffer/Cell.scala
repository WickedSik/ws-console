package io.github.wickedsik.wsconsole
package buffer

/**
 * A single character with styling at a screen position.
 *
 * The `width` field reserves space for future wide-character support
 * (CJK, emoji). In Layer 2 it always defaults to 1 — wide-character
 * handling is deferred to a later phase.
 */
final case class Cell(
  char:  Char,
  style: CellStyle = CellStyle.Empty,
  width: Int       = 1
)

object Cell:
  /** A blank cell — a space with no styling. Used as the default fill for buffers. */
  val Empty: Cell = Cell(' ')
