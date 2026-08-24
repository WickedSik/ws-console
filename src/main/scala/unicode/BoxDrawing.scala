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
  topLeft: Char,
  topRight: Char,
  bottomLeft: Char,
  bottomRight: Char,
  horizontal: Char,
  vertical: Char,
  verticalRight: Char,
  verticalLeft: Char,
  horizontalDown: Char,
  horizontalUp: Char,
  cross: Char
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

  /**
   * All-space glyph set. Used as the underlying glyphs for a borderless
   * [[buffer.BoxStyle]] variant that never draws its edges — the space
   * fallback exists only to satisfy the trait's constructor. `drawBox`
   * short-circuits when the border reports a zero inset, so these
   * glyphs are never consumed in practice.
   */
  val Empty: BoxDrawing =
    BoxDrawing(' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ')
