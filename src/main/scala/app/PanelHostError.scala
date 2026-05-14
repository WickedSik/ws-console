package io.github.wickedsik.wsconsole
package app

import java.io.IOException

/**
 * Typed failures from [[PanelHost]] operations (Q8 ratified).
 *
 * Extends `IOException` so the failures compose with the rest of the
 * Layer 7 error machinery without widening any signature — `pop` still
 * fails on the `IOException` channel.
 *
 * The ADT is sealed in package `app`; the current single case
 * (`EmptyStack`) reflects the only failure mode of the v1 host.
 * Adding future cases (e.g. validation rules) lands non-breakingly.
 */
sealed abstract class PanelHostError(message: String) extends IOException(message)

object PanelHostError:
  /**
   * Raised by `pop` when the panel stack is already empty. The default
   * behaviour propagates this failure through `Application.run` and
   * terminates the program — an empty-stack pop on the root host means
   * "no panels left to render."
   *
   * Consumers wanting recoverable empty-pop semantics catch it
   * explicitly:
   * `host.pop.catchSome { case _: PanelHostError.EmptyStack => ... }`.
   */
  case object EmptyStack extends PanelHostError("Cannot pop from an empty panel stack")
