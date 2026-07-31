package io.github.wickedsik.wsconsole
package ansi

object Mode:
  val LineWrapOn: String = s"${Csi.ESC}[?7h"
  val LineWrapOff: String = s"${Csi.ESC}[?7l"
  val BracketedPasteOn: String = s"${Csi.ESC}[?2004h"
  val BracketedPasteOff: String = s"${Csi.ESC}[?2004l"
  // Bracketed paste markers (received in input, not sent)
  val BracketedPasteStart: String = s"${Csi.ESC}[200~"
  val BracketedPasteEnd: String = s"${Csi.ESC}[201~"
