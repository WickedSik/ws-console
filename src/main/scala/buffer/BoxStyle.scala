package io.github.wickedsik.wsconsole
package buffer

/**
 * Glyph set for a rectangular border laid out on a 9-cell grid
 * (mid-center is the content region and carries no glyph).
 *
 * `BoxStyle` describes only *what glyphs* a frame uses; *which edges*
 * are visible is orthogonal, carried by [[geometry.Sides]] on the
 * component that owns the frame. Corner glyphs are only written when
 * both adjacent edges are visible; the edge glyphs (`topCenter`,
 * `bottomCenter`, `midLeft`, `midRight`) fill the runs between corners
 * and extend into a suppressed corner cell when only one adjacent edge
 * is visible. See [[Canvas.drawBox]] for the rendering rules.
 */
final case class BoxStyle(
  topLeft: Char,
  topCenter: Char,
  topRight: Char,
  midLeft: Char,
  midRight: Char,
  bottomLeft: Char,
  bottomCenter: Char,
  bottomRight: Char
)

object BoxStyle:
  val Single: BoxStyle = BoxStyle(
    topLeft = '┌',
    topCenter = '─',
    topRight = '┐',
    midLeft = '│',
    midRight = '│',
    bottomLeft = '└',
    bottomCenter = '─',
    bottomRight = '┘'
  )

  val Double: BoxStyle = BoxStyle(
    topLeft = '╔',
    topCenter = '═',
    topRight = '╗',
    midLeft = '║',
    midRight = '║',
    bottomLeft = '╚',
    bottomCenter = '═',
    bottomRight = '╝'
  )
