package io.github.wickedsik.wsconsole
package ansi

/**
 * Standalone escapes for the text attributes, one sequence each.
 *
 * These are the rendered form of the corresponding [[Sgr]] constants and
 * exist for callers that want a ready-made string. To apply several
 * attributes at once, compose `Sgr` values with `++` and render once —
 * concatenating these constants emits one escape per attribute.
 */
object Style:
  val Reset: String = Sgr.Reset.toAnsi
  val Bold: String = Sgr.Bold.toAnsi
  val Dim: String = Sgr.Dim.toAnsi
  val Italic: String = Sgr.Italic.toAnsi
  val Underline: String = Sgr.Underline.toAnsi
  val Blink: String = Sgr.Blink.toAnsi
  val Reverse: String = Sgr.Reverse.toAnsi
  val Hidden: String = Sgr.Hidden.toAnsi
  val Strikethrough: String = Sgr.Strikethrough.toAnsi
