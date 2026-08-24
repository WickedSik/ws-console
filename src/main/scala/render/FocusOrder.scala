package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId}
import geometry.Rect

/**
 * A focusable component's identity and assigned rect within one frame.
 *
 * Produced by the render pipeline's tree walk as a side-channel of
 * layout resolution. `FocusManager` consumes the ordered list to derive
 * the Tab cycle; future mouse-focus paths hit-test against the rect.
 */
final case class FocusableEntry(id: ComponentId, area: Rect)

/**
 * Ordered list of focusable components for a single frame.
 *
 * Order matches the render walk (pre-order, depth-first) — Tab cycles
 * in the same order components are drawn. Empty when no focusables are
 * visible; `FocusManager` treats this as a stable state (`focusNext` /
 * `focusPrevious` are no-ops).
 *
 * Contract: an entry appears here iff its component is `focusable`
 * AND was assigned a non-empty rect during layout. Components in
 * collapsed branches (zero-size rect) are excluded for that frame
 * even though `focusable` remains `true` on the type — "what you can
 * Tab to is exactly what is rendered, focusable, and visible right now."
 */
final case class FocusOrder(entries: Vector[FocusableEntry]):
  def ids: Vector[ComponentId] = entries.map(_.id)
  def isEmpty: Boolean = entries.isEmpty
  def nonEmpty: Boolean = entries.nonEmpty
  def contains(id: ComponentId): Boolean = entries.exists(_.id == id)

object FocusOrder:
  val empty: FocusOrder = FocusOrder(Vector.empty)

  /**
   * Test convenience: build a `FocusOrder` from a list of components,
   * collecting those with `focusable = true` and assigning placeholder
   * empty rects. Production code uses `LayoutManager.resolve`, which
   * supplies real rects from the layout walk.
   */
  def fromFocusables(components: Vector[Component]): FocusOrder =
    FocusOrder(components.collect { case c if c.focusable => FocusableEntry(c.id, Rect(0, 0, 0, 0)) })
