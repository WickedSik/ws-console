package io.github.wickedsik.wsconsole
package ansi

object AlternateBuffer:
  val Enter = "\u001B[?1049h" // Switch to alternate screen buffer
  val Exit = "\u001B[?1049l" // Return to normal screen buffer
