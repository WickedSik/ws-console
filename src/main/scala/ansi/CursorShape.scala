package io.github.wickedsik.wsconsole
package ansi

object CursorShape {
  val Default = "\u001B[0 q"
  val BlinkingBlock = "\u001B[1 q"
  val SteadyBlock = "\u001B[2 q"
  val BlinkingUnderline = "\u001B[3 q"
  val SteadyUnderline = "\u001B[4 q"
  val BlinkingBar = "\u001B[5 q"
  val SteadyBar = "\u001B[6 q"
}
