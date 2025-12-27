package io.github.wickedsik.wsconsole
package ansi

object StyleDisable:
  val BoldDim = "\u001B[22m" // Disables both bold and dim
  val Italic = "\u001B[23m"
  val Underline = "\u001B[24m"
  val Blink = "\u001B[25m"
  val Reverse = "\u001B[27m"
  val Hidden = "\u001B[28m"
  val Strikethrough = "\u001B[29m"
