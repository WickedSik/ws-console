package io.github.wickedsik.wsconsole
package ansi

object Screen:
  val ClearFromCursor: String = s"${Csi.ESC}[J" // Clear from cursor to end of screen (or ESC[0J)
  val ClearToCursor: String = s"${Csi.ESC}[1J" // Clear from start of screen to cursor
  val ClearAll: String = s"${Csi.ESC}[2J" // Clear entire screen
  val ClearAllWithScrollback: String = s"${Csi.ESC}[3J" // Clear screen + scrollback buffer
  val ClearLineFromCursor: String = s"${Csi.ESC}[K" // Clear from cursor to end of line (or ESC[0K)
  val ClearLineToCursor: String = s"${Csi.ESC}[1K" // Clear from start of line to cursor
  val ClearLine: String = s"${Csi.ESC}[2K" // Clear entire line
