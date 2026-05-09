package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import geometry.Rect

/**
 * The Layer 4 visual contract.
 *
 * A `Component` knows how to draw itself into a [[Rect]] on a [[Canvas]].
 * Rendering is synchronous and pure: no ZIO effects, no internal state,
 * no I/O. The component tree is data; rendering is a fold over that data
 * into cells.
 *
 * Events, focus, and dispatch are deliberately absent from this layer —
 * they belong to Layer 5 once the component model has settled. The
 * Component contract here is *visual* only.
 *
 * `Component` is an open extension point: library consumers are
 * expected to define their own widgets by extending this trait.
 */
trait Component:
  /**
   * Draw this component into `area` on `canvas`. Coordinates inside
   * `area` are in the canvas's coordinate space (not relative). For
   * components that delegate to children, the standard pattern is to
   * compute child rects via the layout engine, then call
   * `child.render(childRect, canvas)` directly — Layer 2's
   * `Canvas.subCanvas` is reserved for cases where strict per-component
   * clipping is needed (e.g. drawing a region must not overflow into
   * sibling regions).
   */
  def render(area: Rect, canvas: Canvas): Unit
