package io.github.wickedsik.wsconsole
package ansi

object Mode {
  val LineWrapOn = "\u001B[?7h"
  val LineWrapOff = "\u001B[?7l"
  val BracketedPasteOn = "\u001B[?2004h"
  val BracketedPasteOff = "\u001B[?2004l"
  // Bracketed paste markers (received in input, not sent)
  val BracketedPasteStart = "\u001B[200~"
  val BracketedPasteEnd = "\u001B[201~"
}
