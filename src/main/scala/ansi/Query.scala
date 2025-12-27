package io.github.wickedsik.wsconsole
package ansi

object Query {
  val CursorPosition = "\u001B[6n" // Response: ESC[${row};${col}R
  val DeviceAttributes = "\u001B[c"
}
