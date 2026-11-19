package io.github.wickedsik.wsconsole
package app

import java.io.IOException

/**
 * Typed failures from [[PanelHost]] operations.
 *
 * Extends `IOException` so failures compose with Layer 7's error
 * machinery without widening any signature.
 */
enum PanelHostError(message: String) extends IOException(message):
  /**
   * Raised by `pop` on an empty stack. Propagates through
   * `Application.run` and terminates the program by default. Catch
   * explicitly for recoverable semantics:
   * `host.pop.catchSome { case PanelHostError.EmptyStack => ... }`.
   */
  case EmptyStack extends PanelHostError("Cannot pop from an empty panel stack")
