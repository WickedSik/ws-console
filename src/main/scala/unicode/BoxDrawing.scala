package io.github.wickedsik.wsconsole
package unicode

/**
 * Unicode box-drawing glyph set.
 *
 * Each field is a single character (BMP code point) suitable for direct
 * placement in a [[buffer.Cell]] or for assembly into multi-character
 * border strings.
 */
final case class BoxDrawing(
  topLeft:        Char,
  topRight:       Char,
  bottomLeft:     Char,
  bottomRight:    Char,
  horizontal:     Char,
  vertical:       Char,
  verticalRight:  Char,
  verticalLeft:   Char,
  horizontalDown: Char,
  horizontalUp:   Char,
  cross:          Char
):
  /** Build a horizontal line of the given width using this set's [[horizontal]] glyph. */
  def horizontalLine(width: Int): String = horizontal.toString * width

object BoxDrawing:
  /** Single-line box drawing characters. */
  val SingleLine: BoxDrawing =
    BoxDrawing('┌', '┐', '└', '┘', '─', '│', '├', '┤', '┬', '┴', '┼')

  /** Double-line box drawing characters. */
  val DoubleLine: BoxDrawing =
    BoxDrawing('╔', '╗', '╚', '╝', '═', '║', '╠', '╣', '╦', '╩', '╬')
