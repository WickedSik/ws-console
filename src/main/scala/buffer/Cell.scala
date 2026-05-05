package io.github.wickedsik.wsconsole
package buffer

/**
 * A single character with styling at a screen position.
 */
final case class Cell(
  char:  Char,
  style: CellStyle = CellStyle.Empty
)

object Cell:
  /** A blank cell — a space with no styling. Used as the default fill for buffers. */
  val Empty: Cell = Cell(' ')
