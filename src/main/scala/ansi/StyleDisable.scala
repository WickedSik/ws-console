package io.github.wickedsik.wsconsole
package ansi

/** Standalone escapes that switch text attributes back off. See [[Style]]. */
object StyleDisable:
  val BoldDim: String       = Sgr.NoBoldDim.toAnsi // Disables both bold and dim
  val Italic: String        = Sgr.NoItalic.toAnsi
  val Underline: String     = Sgr.NoUnderline.toAnsi
  val Blink: String         = Sgr.NoBlink.toAnsi
  val Reverse: String       = Sgr.NoReverse.toAnsi
  val Hidden: String        = Sgr.NoHidden.toAnsi
  val Strikethrough: String = Sgr.NoStrikethrough.toAnsi
