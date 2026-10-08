package io.github.wickedsik.wsconsole
package ansi

object Query:
  val CursorPosition: String = s"${Csi.ESC}[6n" // Response: ESC[${row};${col}R
  val DeviceAttributes: String = s"${Csi.ESC}[c"
