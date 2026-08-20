package io.github.wickedsik.wsconsole
package event

import buffer.Frame

import zio.ZIO

import java.io.IOException

/**
 * Component's response to event handling.
 *
 * The dispatcher offers events to the focused component first; if the
 * component returns `Ignored`, the event bubbles to the parent (and so
 * on up the tree, then to the application's root handler). Any non-
 * `Ignored` result stops propagation.
 *
 * The four cases partition by **scope of consequence** — how far
 * outside itself the component's answer reaches:
 *
 *   - `Ignored`       — nothing; the event is offered to the parent
 *   - `Consumed`      — propagation only; nothing further happens
 *   - `RequestRedraw` — my own local state; the framework repaints
 *   - `Perform`       — the world outside me; the framework runs the effect
 *
 * Pick the narrowest case that is true. A component that only changed
 * its own cells returns `RequestRedraw`, never `Perform(ZIO.unit)`.
 *
 * `Perform` carries an unexecuted `ZIO` value describing frame-level
 * work — a panel swap, a quit, a focus change. The component never
 * runs it; the render loop does, after dispatch settles. `Perform`
 * always schedules a redraw.
 *
 * Dispatch returns a single [[EventResult]] — bubbling is the only
 * result-folding, no composition rule is needed.
 */
sealed trait EventResult

object EventResult:
  /** Event handled; stop propagation. No frame requested. */
  case object Consumed extends EventResult

  /** Event not handled; continue propagation up the parent chain. */
  case object Ignored extends EventResult

  /** Event handled; stop propagation and request a redraw. */
  case object RequestRedraw extends EventResult

  /**
   * Event handled; stop propagation, run the effect on the render-loop
   * fiber, and request a redraw. The effect is unexecuted — the
   * component describes work; the framework carries it out.
   *
   * The payload is typed `ZIO[Frame, IOException, Unit]` so a component
   * can describe frame-level work and nothing wider. `Terminal` appears
   * in no component signature, and a component executes nothing itself.
   */
  final case class Perform(effect: ZIO[Frame, IOException, Unit]) extends EventResult
