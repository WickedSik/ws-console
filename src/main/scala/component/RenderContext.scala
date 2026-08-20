package io.github.wickedsik.wsconsole
package component

import java.time.Instant

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
 *   - State that two or more components must agree on (focus, wall-clock
 *     time; future: theme, layout config) lives in framework services
 *     and is exposed read-only through this snapshot.
 *   - State that belongs to one widget (pending activation, animation
 *     phase, scroll position, input cursor) stays in that widget as
 *     private mutable state with a UIO-shaped public surface.
 *
 * The [[timestamp]] field is the wall-clock reading the render loop
 * takes once per frame boundary. Components derive time-driven state
 * (spinner phase, animation frame, blink) from it as a pure function of
 * the snapshot — never by calling `Instant.now()` themselves. Because
 * the reading is a function of real time, animations run at a rate
 * independent of render cadence: a faster loop does not spin faster.
 *
 * Extensibility: shape is intentionally fixed. Adding `theme` or
 * `layoutConfig` later is a non-breaking addition (named field). A
 * consumer-extensible context (Map[ContextKey[A], A]) is deferred
 * until a real consumer expresses the need.
 */
final case class RenderContext(
  focus:     FocusSnapshot,
  timestamp: Instant = Instant.EPOCH
)

object RenderContext:
  /**
   * Empty context for tests and components that do not read shared state.
   * No focused component; `isFocused` returns `false` for every id. The
   * timestamp is fixed at `Instant.EPOCH` — a stable value that lets
   * timestamp-driven components render deterministically in unit tests.
   */
  val empty: RenderContext = RenderContext(FocusSnapshot(None), Instant.EPOCH)

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
