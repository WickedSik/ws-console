package io.github.wickedsik.wsconsole
package app

import java.io.IOException

/**
 * Typed failures from [[PanelHost]] operations.
 *
 * Extends `IOException` so failures compose with Layer 7's error
 * machinery without widening any signature.
 */
sealed abstract class PanelHostError(message: String) extends IOException(message)

object PanelHostError:
  /**
   * Raised by `pop` on an empty stack. Propagates through
   * `Application.run` and terminates the program by default. Catch
   * explicitly for recoverable semantics:
   * `host.pop.catchSome { case _: PanelHostError.EmptyStack => ... }`.
   */
  case object EmptyStack extends PanelHostError("Cannot pop from an empty panel stack")
