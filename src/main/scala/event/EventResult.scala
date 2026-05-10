package io.github.wickedsik.wsconsole
package event

/**
 * Component's response to event handling.
 *
 * The dispatcher offers events to the focused component first; if the
 * component returns `Ignored`, the event bubbles to the parent (and so
 * on up the tree, then to the application's root handler). `Consumed`
 * and `RequestRedraw` both stop propagation; the difference is whether
 * a frame is requested.
 *
 * **Single return value per dispatch (Q3 resolved 2026-05-10).** With
 * `EventFilter`/`EventListener` deferred, no composition rule is needed
 * — bubbling is the only result-folding at this iteration.
 */
sealed trait EventResult

object EventResult:
  /** Event handled; stop propagation. No frame requested. */
  case object Consumed extends EventResult

  /** Event not handled; continue propagation up the parent chain. */
  case object Ignored extends EventResult

  /** Event handled; stop propagation and request a redraw. */
  case object RequestRedraw extends EventResult
