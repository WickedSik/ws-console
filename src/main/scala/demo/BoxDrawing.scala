package io.github.wickedsik.wsconsole
package demo

/**
 * Unicode box drawing characters, block elements, and spinner frames.
 *
 * Provides constants for rendering terminal UI elements without
 * relying on ASCII approximations.
 */
object BoxDrawing:

  // Single-line box drawing
  val TopLeft: String      = "┌"
  val TopRight: String     = "┐"
  val BottomLeft: String   = "└"
  val BottomRight: String  = "┘"
  val Horizontal: String   = "─"
  val Vertical: String     = "│"
  val VerticalRight: String  = "├"
  val VerticalLeft: String   = "┤"
  val HorizontalDown: String = "┬"
  val HorizontalUp: String   = "┴"
  val Cross: String          = "┼"

  // Double-line box drawing
  val DoubleTopLeft: String      = "╔"
  val DoubleTopRight: String     = "╗"
  val DoubleBottomLeft: String   = "╚"
  val DoubleBottomRight: String  = "╝"
  val DoubleHorizontal: String   = "═"
  val DoubleVertical: String     = "║"
  val DoubleVerticalRight: String  = "╠"
  val DoubleVerticalLeft: String   = "╣"
  val DoubleHorizontalDown: String = "╦"
  val DoubleHorizontalUp: String   = "╩"
  val DoubleCross: String          = "╬"

  /** Block elements for sub-character precision progress bars (index 0 = empty, 8 = full) */
  val BlockElements: Array[String] = Array(
    " ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█"
  )

  /** Braille dot spinner frames for loading animations */
  val Spinner: Array[String] = Array(
    "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
  )

  /** Build a horizontal line of the given width using the specified character */
  def horizontalLine(width: Int, char: String = Horizontal): String =
    char * width

  /** Build a double horizontal line of the given width */
  def doubleHorizontalLine(width: Int): String =
    DoubleHorizontal * width
