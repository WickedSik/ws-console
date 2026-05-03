package io.github.wickedsik.wsconsole
package buffer

import unicode.BoxDrawing

/**
 * Box border style used by [[Canvas.drawBox]]. Each variant carries the
 * Unicode characters for its corners and edges.
 */
sealed trait BoxStyle:
  def topLeft:     Char
  def topRight:    Char
  def bottomLeft:  Char
  def bottomRight: Char
  def horizontal:  Char
  def vertical:    Char

object BoxStyle:

  case object Single extends BoxStyle:
    val topLeft:     Char = BoxDrawing.TopLeft.charAt(0)
    val topRight:    Char = BoxDrawing.TopRight.charAt(0)
    val bottomLeft:  Char = BoxDrawing.BottomLeft.charAt(0)
    val bottomRight: Char = BoxDrawing.BottomRight.charAt(0)
    val horizontal:  Char = BoxDrawing.Horizontal.charAt(0)
    val vertical:    Char = BoxDrawing.Vertical.charAt(0)

  case object Double extends BoxStyle:
    val topLeft:     Char = BoxDrawing.DoubleTopLeft.charAt(0)
    val topRight:    Char = BoxDrawing.DoubleTopRight.charAt(0)
    val bottomLeft:  Char = BoxDrawing.DoubleBottomLeft.charAt(0)
    val bottomRight: Char = BoxDrawing.DoubleBottomRight.charAt(0)
    val horizontal:  Char = BoxDrawing.DoubleHorizontal.charAt(0)
    val vertical:    Char = BoxDrawing.DoubleVertical.charAt(0)
