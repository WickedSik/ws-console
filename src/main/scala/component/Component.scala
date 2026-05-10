package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import event.{Event, EventResult}
import geometry.Rect

/**
 * The Layer 4 visual contract, extended in Layer 6 with identity, event
 * handling, focus opt-in, and a child-layout accessor used by the
 * render pipeline.
 *
 * A `Component` knows how to draw itself into a [[Rect]] on a [[Canvas]].
 * Rendering is synchronous and pure: no ZIO effects, no internal state,
 * no I/O. The component tree is data; rendering is a fold over that data
 * into cells.
 *
 * Layer 6 additions are all defaulted so existing components continue to
 * compose unchanged:
 *   - `id` — framework-assigned identity used by layout + dispatch
 *   - `handleEvent` — defaults to `EventResult.Ignored`; interactive
 *     components override
 *   - `focusable` — defaults to `false`; opt-in for focus-cycle inclusion
 *   - `childLayouts` — defaults to empty (leaf); composite components
 *     override to expose how their assigned area splits across children
 *
 * `Component` is an open extension point: library consumers are
 * expected to define their own widgets by extending this trait.
 */
trait Component:
  /** Framework-assigned identity. Stable for the component instance's lifetime. */
  val id: ComponentId = ComponentId.fresh()

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

  /**
   * How this component's assigned area splits across its children.
   *
   * Defaults to empty — leaf components have no children. Composite
   * components (containers, panels) override to expose the
   * `(child, rect)` pairs the render pipeline will recurse into. The
   * `LayoutManager` walks this accessor to build a `LayoutResult`.
   *
   * The pairs returned must mirror what `render` actually draws — if
   * `render` skips a child for an undersized area, `childLayouts` must
   * skip it too (or return a zero-size rect for it). The two methods
   * are two views of the same layout decision.
   */
  def childLayouts(area: Rect): Seq[(Component, Rect)] = Seq.empty

  /**
   * Handle an event. Defaults to `Ignored`, which causes the dispatcher
   * to bubble the event to the parent. Interactive components override.
   */
  def handleEvent(event: Event): EventResult = EventResult.Ignored

  /**
   * Whether this component participates in the focus cycle. Defaults to
   * `false` — most visual-only components opt out, keeping the
   * `focusNext`/`focusPrevious` order well-defined.
   */
  def focusable: Boolean = false
