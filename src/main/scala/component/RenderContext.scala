package io.github.wickedsik.wsconsole
package component

/**
 * Immutable per-frame snapshot of framework-owned state that components
 * may read but never write.
 *
 * Captured once at the start of each frame by the render loop and once
 * at dispatch time by the event dispatcher. Stable for the duration of
 * the frame / dispatch — analogous to React's "props don't change
 * during render" guarantee. Single-fiber render loop guarantees the
 * snapshot is not racy in practice.
 *
 * Discipline: shared = lifted, local = local.
 *   - State that two or more components must agree on (currently focus;
 *     future: theme, layout config) lives in framework services and is
 *     exposed read-only through this snapshot.
 *   - State that belongs to one widget (pending activation, animation
 *     phase, scroll position, input cursor) stays in that widget as
 *     private mutable state with a UIO-shaped public surface.
 *
 * Extensibility: shape is intentionally fixed at v1. Adding `theme` or
 * `layoutConfig` later is a non-breaking addition (named field). A
 * consumer-extensible context (Map[ContextKey[A], A]) is deferred
 * until a real consumer expresses the need.
 */
final case class RenderContext(focus: FocusSnapshot)

object RenderContext:
  /**
   * Empty context for tests and components that do not read shared state.
   * No focused component; `isFocused` returns `false` for every id.
   */
  val empty: RenderContext = RenderContext(FocusSnapshot(None))

/**
 * Read-only view of the currently-focused component id, frozen at frame
 * or dispatch boundary.
 *
 * Components query `isFocused(this.id)` from their render / handleEvent
 * paths. The snapshot does not carry the full focus cycle — that is a
 * framework-internal concern (see `FocusOrder`).
 */
final case class FocusSnapshot(focused: Option[ComponentId]):
  def isFocused(id: ComponentId): Boolean = focused.contains(id)
