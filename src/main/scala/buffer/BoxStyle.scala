package io.github.wickedsik.wsconsole
package buffer

import unicode.BoxDrawing

/**
 * Box border style used by [[Canvas.drawBox]]. Each variant carries a
 * [[BoxDrawing]] glyph set; the trait re-exports the common corner and
 * edge characters for direct cell placement.
 */
sealed trait BoxStyle(style: BoxDrawing):
  def topLeft:     Char = style.topLeft
  def topRight:    Char = style.topRight
  def bottomLeft:  Char = style.bottomLeft
  def bottomRight: Char = style.bottomRight
  def horizontal:  Char = style.horizontal
  def vertical:    Char = style.vertical

object BoxStyle:
  case object Single extends BoxStyle(BoxDrawing.SingleLine)
  case object Double extends BoxStyle(BoxDrawing.DoubleLine)
