package io.github.wickedsik.wsconsole
package ansi

object AlternateBuffer:
  val Enter: String = s"${Csi.ESC}[?1049h" // Switch to alternate screen buffer
  val Exit: String = s"${Csi.ESC}[?1049l" // Return to normal screen buffer
