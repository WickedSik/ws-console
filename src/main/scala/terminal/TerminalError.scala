package io.github.wickedsik.wsconsole
package terminal

import java.io.IOException

/**
 * Terminal does not meet minimum requirements for ws-console.
 *
 * Thrown during validation when the terminal lacks required capabilities
 * (TTY, color support, Unicode). The message includes what's missing
 * and which terminals are supported.
 */
class UnsupportedTerminalException(message: String) extends IOException(message)

/**
 * Terminal state has become corrupted or inconsistent.
 *
 * Thrown when operations fail due to terminal state issues, such as
 * failing to enter/exit raw mode or restore cursor position.
 */
class TerminalStateException(message: String) extends IOException(message)
