package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import geometry.Rect

/**
 * Escape-hatch leaf component for migrating legacy panels into the
 * component tree without purpose-built widgets.
 *
 * Receives a sub-canvas clipped to its assigned area and a callback
 * that may freely call any [[Canvas]] method — `putText`, `putChar`,
 * `drawBox`, `fillRect`, etc. Coordinates passed to the callback are
 * relative to the sub-canvas (i.e. (0, 0) is the top-left of the
 * component's area), so the callback's drawing logic doesn't need to
 * know its absolute position.
 *
 * Used for:
 *   - Dense per-cell rendering (e.g. colour grids) that would explode
 *     into tens of nested components if expressed structurally
 *   - Demonstrations of Layer 2 invariants (e.g. positional writes are
 *     order-independent) inside the component model
 *   - Bridging legacy panels until purpose-built widgets exist
 *
 * Prefer purpose-built components for repeated patterns; reach for
 * `RawCanvas` when the structure adds no value.
 */
final case class RawCanvas(draw: Canvas => Unit) extends Component:
  def render(area: Rect, canvas: Canvas): Unit =
    if area.isEmpty then return
    draw(canvas.subCanvas(area))
