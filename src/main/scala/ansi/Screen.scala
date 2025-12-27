package io.github.wickedsik.wsconsole
package ansi

object Screen {
  val ClearFromCursor = "\u001B[J" // Clear from cursor to end of screen (or ESC[0J)
  val ClearToCursor = "\u001B[1J" // Clear from start of screen to cursor
  val ClearAll = "\u001B[2J" // Clear entire screen
  val ClearAllWithScrollback = "\u001B[3J" // Clear screen + scrollback buffer
  val ClearLineFromCursor = "\u001B[K" // Clear from cursor to end of line (or ESC[0K)
  val ClearLineToCursor = "\u001B[1K" // Clear from start of line to cursor
  val ClearLine = "\u001B[2K" // Clear entire line
}
