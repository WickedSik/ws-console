package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId}

import zio.{Ref, UIO, ZIO}

/**
 * Tracks and manages keyboard focus across a component tree.
 *
 * The focus order is derived from the most recent `LayoutResult.order`
 * (a depth-first / pre-order walk from the root) filtered for
 * `focusable: true`. `focusNext`/`focusPrevious` cycle through that
 * filtered order.
 *
 * Applications drive focus changes via `Tab` / `Shift+Tab` bindings —
 * the framework does not pre-bind those keys, but exposes
 * `focusNext`/`focusPrevious` as the canonical navigation primitives.
 */
trait FocusManager:
  /** The currently-focused component id, if any. */
  def focused: UIO[Option[ComponentId]]

  /** Update the focusable cycle from the latest layout walk. */
  def updateFocusables(order: Vector[Component]): UIO[Unit]

  /** Focus a specific id; returns `true` if the id is in the focusable cycle. */
  def focus(id: ComponentId): UIO[Boolean]

  /** Move focus to the next focusable component (wraps to start). */
  def focusNext(): UIO[Unit]

  /** Move focus to the previous focusable component (wraps to end). */
  def focusPrevious(): UIO[Unit]

  /** Clear focus entirely. */
  def clear(): UIO[Unit]

object FocusManager:

  private final case class State(
    cycle:   Vector[ComponentId],
    focused: Option[ComponentId]
  )

  /** Allocate a fresh, empty `FocusManager` backed by a `Ref`. */
  def make: UIO[FocusManager] =
    Ref.make(State(Vector.empty, None)).map { ref =>
      new FocusManager:
        def focused: UIO[Option[ComponentId]] =
          ref.get.map(_.focused)

        def updateFocusables(order: Vector[Component]): UIO[Unit] =
          ref.update { s =>
            val cycle = order.collect { case c if c.focusable => c.id }
            // If the previously-focused component is still focusable,
            // preserve focus; otherwise clear it.
            val focused = s.focused.filter(cycle.contains)
            State(cycle, focused)
          }

        def focus(id: ComponentId): UIO[Boolean] =
          ref.modify { s =>
            if s.cycle.contains(id) then (true, s.copy(focused = Some(id)))
            else (false, s)
          }

        def focusNext(): UIO[Unit] =
          ref.update(advance(_, +1))

        def focusPrevious(): UIO[Unit] =
          ref.update(advance(_, -1))

        def clear(): UIO[Unit] =
          ref.update(_.copy(focused = None))
    }

  private def advance(s: State, step: Int): State =
    if s.cycle.isEmpty then s
    else
      val nextIdx = s.focused match
        case None      => if step > 0 then 0 else s.cycle.size - 1
        case Some(cur) =>
          val cur_idx = s.cycle.indexOf(cur)
          if cur_idx < 0 then 0
          else math.floorMod(cur_idx + step, s.cycle.size)
      s.copy(focused = Some(s.cycle(nextIdx)))
