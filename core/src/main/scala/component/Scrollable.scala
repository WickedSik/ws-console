package io.github.wickedsik.wsconsole
package component

import geometry.Rect

/**
 * Capability of a component that shows a movable window onto content
 * larger than itself.
 *
 * The render pipeline uses it to bring a newly focused descendant into
 * view (ADR-006): after layout, it walks from the focused component up
 * through its ancestors and asks each `Scrollable` one to reveal the
 * focused rect. Keeping the request outside `render` and `childLayouts`
 * keeps both pure of focus, as the [[Component]] contract requires.
 */
trait Scrollable:

  /**
   * Scroll the minimum distance that shows `target`, and return how far
   * the content moved up (negative when it moved down, `0` when nothing
   * changed). A target taller than the viewport aligns its top edge.
   *
   * `target` is the descendant's rect from the current layout and
   * `viewport` is this component's own rect from the same layout, both
   * in canvas coordinates. The caller shifts `target` by the returned
   * amount before asking the next ancestor, so nested scrollables
   * compose.
   */
  def reveal(target: Rect, viewport: Rect): Int
