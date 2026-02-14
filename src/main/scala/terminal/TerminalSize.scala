package io.github.wickedsik.wsconsole
package terminal

/**
 * Terminal dimensions in rows and columns.
 *
 * Named fields prevent the common (rows, cols) vs (width, height) tuple confusion.
 * Rows count vertical lines, cols count horizontal character positions.
 *
 * @param rows Number of visible rows (vertical)
 * @param cols Number of visible columns (horizontal)
 */
case class TerminalSize(rows: Int, cols: Int):
  require(rows > 0, s"rows must be positive, got: $rows")
  require(cols > 0, s"cols must be positive, got: $cols")

  /** Total character positions visible in the terminal */
  def area: Int = rows * cols
