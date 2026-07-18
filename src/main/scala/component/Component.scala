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
 * Shared = lifted, local = local. Components receive a read-only
 * [[RenderContext]] at render and event time, carrying snapshots of
 * framework-owned state (focus today; theme / layout config later).
 * Components query the context for shared state; they do not cache
 * shared state in their own fields. Widget-owned local state (animation
 * phase, pending activation, scroll position, input cursor) stays
 * inside the widget — the discipline is documented; reviewers enforce.
 *
 * Layer 6 additions are all defaulted so existing components continue
 * to compose unchanged:
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
   * `child.render(childRect, canvas, ctx)` directly — Layer 2's
   * `Canvas.subCanvas` is reserved for cases where strict per-component
   * clipping is needed (e.g. drawing a region must not overflow into
   * sibling regions).
   *
   * `ctx` is the per-frame snapshot of framework-owned state. Read what
   * you need; never mutate. The snapshot is stable for the duration of
   * this render call — focus does not flip mid-frame.
   *
   * Cell-coverage contract: components MAY leave cells in `area`
   * unwritten. The framework guarantees that on each frame, the
   * `BufferManager.swap()` rotation clears `current` to `Cell.Empty`
   * outside any active scroll region before any component draws. The
   * diff then correctly transitions previously-styled cells back to
   * blank on the next frame. This is the "Option B" contract:
   * components draw what they want; the framework owns blank cells.
   * The corollary: a component cannot rely on the buffer to retain
   * cells from a prior frame — every visible cell must be (re)drawn
   * by some component each frame it should appear.
   */
  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit

  /**
   * How this component's assigned area splits across its children.
   *
   * Defaults to empty — leaf components have no children. Composite
   * components (containers, panels) override to expose the
   * `(child, rect)` pairs the render pipeline will recurse into. The
   * `LayoutManager` walks this accessor to build a `LayoutResult`.
   *
   * Pure geometry — no shared-state read. `RenderContext` is not
   * threaded here; layout decisions must not depend on focus or other
   * frame-snapshot state.
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
   *
   * `ctx` is the per-dispatch snapshot of framework-owned state — same
   * shape as the render-time snapshot. Guard event handlers on
   * `ctx.focus.isFocused(this.id)` rather than caching focus state
   * locally.
   */
  def handleEvent(event: Event, ctx: RenderContext): EventResult = EventResult.Ignored

  /**
   * Whether this component participates in the focus cycle. Defaults to
   * `false` — most visual-only components opt out, keeping the
   * `focusNext`/`focusPrevious` order well-defined.
   *
   * Declaring `focusable = true` does NOT register the component with
   * any manager; the render pipeline discovers focusables on each
   * frame's tree walk. A focusable component in a collapsed layout
   * branch (zero-size rect) is excluded from that frame's cycle.
   */
  def focusable: Boolean = false
