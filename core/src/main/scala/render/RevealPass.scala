package io.github.wickedsik.wsconsole
package render

import component.{ComponentId, Scrollable}
import geometry.Rect

/**
 * Brings a component into view by asking its [[component.Scrollable]]
 * ancestors to scroll (ADR-006).
 *
 * Runs between two layout passes of the same frame: the first layout
 * gives the target's current rect, the pass moves scroll offsets, and the
 * pipeline lays out again before drawing. The reveal therefore lands on
 * the very frame that follows the focus change.
 *
 * Ancestors are visited innermost first. After each one scrolls, the
 * target rect is shifted by the distance the content moved and narrowed
 * to that ancestor's viewport, so the next ancestor out reveals the part
 * that is actually visible — nested scrollables compose without an
 * intermediate layout.
 */
object RevealPass:

  /**
   * Reveal `target` within `layout`. Returns `true` when any ancestor
   * scrolled, meaning `layout` is stale and must be resolved again.
   * A target absent from the layout, or with no scrollable ancestor, is
   * a no-op.
   */
  def apply(layout: LayoutResult, target: ComponentId): Boolean =
    layout.rects.get(target) match
      case None => false
      case Some(rect) =>
        val scrollables = layout
          .ancestorsOf(target)
          .collect { case s: Scrollable => s }
          .flatMap(s => layout.rects.get(s.id).map(viewport => (s, viewport)))
        val revealed = scrollables.foldLeft((rect = rect, moved = false)) { case (acc, (s, viewport)) =>
          val shift = s.reveal(acc.rect, viewport)
          (rect = visiblePart(acc.rect.copy(y = acc.rect.y - shift), viewport), moved = acc.moved || shift != 0)
        }
        revealed.moved

  /**
   * The rows of `target` inside `viewport`. When none are (a zero-height
   * target), the target itself is kept so outer ancestors still see its
   * position.
   */
  private def visiblePart(target: Rect, viewport: Rect): Rect =
    val top = math.max(target.y, viewport.y)
    val bottom = math.min(target.y + target.height, viewport.y + viewport.height)
    if bottom <= top then target
    else target.copy(y = top, height = bottom - top)
