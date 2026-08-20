package io.github.wickedsik.wsconsole
package buffer

import unicode.BoxDrawing

/**
 * Box border style used by [[Canvas.drawBox]]. Each variant carries a
 * [[BoxDrawing]] glyph set; the trait re-exports the common corner and
 * edge characters for direct cell placement.
 *
 * The [[inset]] method is the self-describing frame cost — how many
 * cells the border consumes on each edge. Framed components compose
 * their content region as `area.inner(border.inset).inner(padding)`
 * without knowing which variant they were handed. A [[Borderless]]
 * frame reports zero, so a borderless component's size equals its
 * content rather than `content + 2`.
 */
sealed trait BoxStyle(style: BoxDrawing):
  def topLeft:     Char = style.topLeft
  def topRight:    Char = style.topRight
  def bottomLeft:  Char = style.bottomLeft
  def bottomRight: Char = style.bottomRight
  def horizontal:  Char = style.horizontal
  def vertical:    Char = style.vertical

  /** Cells this border consumes on each edge. `0` means no glyphs are drawn. */
  def inset: Int = 1

object BoxStyle:
  case object Single extends BoxStyle(BoxDrawing.SingleLine)
  case object Double extends BoxStyle(BoxDrawing.DoubleLine)

  /**
   * No border glyphs; the framed area equals the content region.
   *
   * The underlying [[BoxDrawing.Empty]] glyphs exist only to satisfy the
   * trait's constructor — [[Canvas.drawBox]] short-circuits when
   * [[inset]] is zero, so the spaces are never actually written.
   */
  case object Borderless extends BoxStyle(BoxDrawing.Empty):
    override val inset: Int = 0
