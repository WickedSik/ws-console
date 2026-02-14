package io.github.wickedsik.wsconsole
package terminal

/**
 * Detected capabilities of the current terminal.
 *
 * Pure data class - detection logic lives in TerminalFactory, not here.
 * Test-friendly: construct directly with desired values for test scenarios.
 *
 * @param colorSupport   Level of color support available
 * @param supportsUnicode Whether the terminal can render Unicode characters
 * @param supportsMouseTracking Whether mouse tracking escape sequences are supported
 * @param supportsAlternateBuffer Whether the terminal supports alternate screen buffer
 * @param isTTY Whether the output is connected to an interactive terminal
 * @param size Current terminal dimensions
 */
case class TerminalCapabilities(
  colorSupport: ColorSupport,
  supportsUnicode: Boolean,
  supportsMouseTracking: Boolean,
  supportsAlternateBuffer: Boolean,
  isTTY: Boolean,
  size: TerminalSize
)
