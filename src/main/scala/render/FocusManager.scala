package io.github.wickedsik.wsconsole
package render

import component.ComponentId

import zio.{Ref, UIO, ZIO}

/**
 * Tracks and manages keyboard focus across a component tree.
 *
 * The focus order is supplied each frame by the render pipeline via
 * `setOrder(order: FocusOrder)` — `FocusOrder` is a side-channel of
 * `LayoutManager.resolve` carrying every component with `focusable = true`
 * whose assigned rect is non-empty. `focusNext`/`focusPrevious` cycle
 * through that ordered list.
 *
 * No component registers itself with the manager. Focus discovery is
 * an application-level concern handled by the render pipeline on every
 * frame — "what you can Tab to is exactly what is rendered, focusable,
 * and visible right now."
 *
 * When the focused id is no longer in the new order, the configured
 * [[FocusPolicy]] decides what to do — drop to `None`, move to the
 * first entry, or apply a custom strategy. Default policy is
 * `FocusPolicy.MoveToFirstOnRemoval` (friendly for the common
 * always-something-focused TUI shape); consumers wanting forms-style
 * "focus dies with the widget" semantics opt into
 * `FocusPolicy.DropOnRemoval` explicitly.
 *
 * Redraw semantics: every focus-mutating operation (`focus`,
 * `focusNext`, `focusPrevious`, `clear`) fires the `onChange`
 * callback supplied to [[FocusManager.make]]. Wired against the
 * render loop's redraw queue, this makes "focus changed → frame
 * scheduled" automatic — the application does not need to manually
 * request a redraw after navigating focus.
 *
 * `setOrder` also fires `onChange`, but *only* when reconciliation
 * actually moved the focused id. It runs inside the redraw cycle —
 * `RenderLoop.redraw` installs the new order *after* the render walk,
 * so a policy-driven focus move lands one frame too late to affect the
 * frame just drawn. Without the follow-up signal the screen keeps
 * showing the pre-reconciliation focus indefinitely, while the manager
 * reports the new id. Firing on change costs exactly one extra frame
 * and converges, because reconciliation is required to be idempotent
 * (see [[FocusPolicy]]); firing unconditionally would spin forever.
 */
trait FocusManager:
  /** The currently-focused component id, if any. */
  def focused: UIO[Option[ComponentId]]

  /** Install the focusable cycle for the new frame; reconciles current focus via the configured policy. */
  def setOrder(order: FocusOrder): UIO[Unit]

  /** Focus a specific id; returns `true` if the id is in the focusable cycle. */
  def focus(id: ComponentId): UIO[Boolean]

  /** Move focus to the next focusable component (wraps to start). */
  def focusNext(): UIO[Unit]

  /** Move focus to the previous focusable component (wraps to end). */
  def focusPrevious(): UIO[Unit]

  /** Clear focus entirely. */
  def clear(): UIO[Unit]

object FocusManager:

  /** Default reconciliation policy for `make` overloads that do not specify one. */
  val DefaultPolicy: FocusPolicy = FocusPolicy.MoveToFirstOnRemoval

  final private case class State(
    order: FocusOrder,
    focused: Option[ComponentId]
  )

  /**
   * Allocate a `FocusManager` with the default policy and no redraw
   * wiring. Useful for tests and for downstream consumers that drive
   * their own render loop.
   */
  def make: UIO[FocusManager] = make(DefaultPolicy, ZIO.unit)

  /**
   * Allocate a `FocusManager` with the default policy whose mutating
   * operations fire the `onChange` callback. Production wiring passes
   * the render loop's redraw enqueue here so navigation self-schedules
   * a frame.
   */
  def make(onChange: UIO[Unit]): UIO[FocusManager] = make(DefaultPolicy, onChange)

  /**
   * Allocate a `FocusManager` with an explicit `FocusPolicy` for
   * order-reconciliation. Consumers wanting forms-style "focus dies
   * with the widget" semantics pass `FocusPolicy.DropOnRemoval`;
   * custom strategies use `FocusPolicy.custom(...)`.
   */
  def make(policy: FocusPolicy, onChange: UIO[Unit] = ZIO.unit): UIO[FocusManager] =
    Ref.make(State(FocusOrder.empty, None)).map { ref =>
      new FocusManager:
        def focused: UIO[Option[ComponentId]] =
          ref.get.map(_.focused)

        def setOrder(order: FocusOrder): UIO[Unit] =
          ref.modify { s =>
            val reconciled = policy.reconcile(s.focused, order)
            (reconciled != s.focused, State(order, reconciled))
          }.flatMap(changed => ZIO.when(changed)(onChange).unit)

        def focus(id: ComponentId): UIO[Boolean] =
          ref.modify { s =>
            if s.order.contains(id) then (true, s.copy(focused = Some(id)))
            else (false, s)
          }.tap(_ => onChange)

        def focusNext(): UIO[Unit] =
          ref.update(advance(_, +1)) *> onChange

        def focusPrevious(): UIO[Unit] =
          ref.update(advance(_, -1)) *> onChange

        def clear(): UIO[Unit] =
          ref.update(_.copy(focused = None)) *> onChange
    }

  private def advance(s: State, step: Int): State =
    val ids = s.order.ids
    if ids.isEmpty then s
    else
      val nextIdx = s.focused match
        case None => if step > 0 then 0 else ids.size - 1
        case Some(cur) =>
          val cur_idx = ids.indexOf(cur)
          if cur_idx < 0 then 0
          else math.floorMod(cur_idx + step, ids.size)
      s.copy(focused = Some(ids(nextIdx)))
