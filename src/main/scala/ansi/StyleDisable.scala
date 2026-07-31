package io.github.wickedsik.wsconsole
package ansi

object StyleDisable:
  val BoldDim: String = s"${Csi.ESC}[22m" // Disables both bold and dim
  val Italic: String = s"${Csi.ESC}[23m"
  val Underline: String = s"${Csi.ESC}[24m"
  val Blink: String = s"${Csi.ESC}[25m"
  val Reverse: String = s"${Csi.ESC}[27m"
  val Hidden: String = s"${Csi.ESC}[28m"
  val Strikethrough: String = s"${Csi.ESC}[29m"
