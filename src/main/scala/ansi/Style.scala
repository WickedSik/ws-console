package io.github.wickedsik.wsconsole
package ansi

object Style:
  val Reset: String = s"${Csi.ESC}[0m"
  val Bold: String = s"${Csi.ESC}[1m"
  val Dim: String = s"${Csi.ESC}[2m"
  val Italic: String = s"${Csi.ESC}[3m"
  val Underline: String = s"${Csi.ESC}[4m"
  val Blink: String = s"${Csi.ESC}[5m"
  val Reverse: String = s"${Csi.ESC}[7m"
  val Hidden: String = s"${Csi.ESC}[8m"
  val Strikethrough: String = s"${Csi.ESC}[9m"
