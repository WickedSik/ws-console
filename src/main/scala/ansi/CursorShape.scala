package io.github.wickedsik.wsconsole
package ansi

sealed trait AnsiCursorShape:
  def sequence: Int
  def toAnsi: String = s"\u001B[$sequence q"

enum CursorShape(val sequence: Int) extends AnsiCursorShape:
  case Default extends CursorShape(0)
  case BlinkingBlock extends CursorShape(1)
  case SteadyBlock extends CursorShape(2)
  case BlinkingUnderline extends CursorShape(3)
  case SteadyUnderline extends CursorShape(4)
  case BlinkingBar extends CursorShape(5)
  case SteadyBar extends CursorShape(6)
