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
final case class TerminalSize(rows: Int, cols: Int):
  require(rows > 0, s"rows must be positive, got: $rows")
  require(cols > 0, s"cols must be positive, got: $cols")

  /** Total character positions visible in the terminal */
  def area: Int = rows * cols

object TerminalSize:

  /**
   * The size for dimensions reported at runtime, such as a host query:
   * `None` unless both are positive. A pty that is detached or not yet
   * sized can report `0 0`. Construct a size the program chooses with
   * `TerminalSize(rows, cols)`, which treats a non-positive value as a
   * defect.
   */
  def from(rows: Int, cols: Int): Option[TerminalSize] =
    Option.when(rows > 0 && cols > 0)(TerminalSize(rows, cols))
