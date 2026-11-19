package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId, Scrollable}
import geometry.Rect

/**
 * Collects, places and lays out the [[component.Popup]]s of a frame
 * (ADR-007).
 *
 * Runs after layout and any reveal relayout, before drawing:
 *
 *   1. Only the focus chain is asked — the focused component and its
 *      ancestors, outermost first. A popup is shown only while its owner
 *      holds focus or contains it.
 *   2. Each anchor is narrowed to every `Scrollable` ancestor's rect,
 *      innermost first. A popup whose anchor is scrolled out of view is
 *      dropped for this frame.
 *   3. The popup is placed within `bounds` by [[place]].
 *   4. Its content is laid out at the placed rect and merged into the
 *      result, parented to the owner so events and future hit-testing
 *      bubble through it. Focusables inside popups are not added to the
 *      focus order: a popup is never a focus stop.
 *
 * Popup content is not asked for popups of its own.
 */
object PopupPass:

  /**
   * `layout` extended with the popups of this frame. Returns `layout`
   * unchanged when nothing is focused or no owner on the focus chain has
   * a popup to show.
   */
  def apply(
    layout: LayoutResult,
    bounds: Rect,
    focused: Option[ComponentId],
    layoutManager: LayoutManager
  ): LayoutResult =
    focused match
      case None         => layout
      case Some(target) =>
        // The focused component and its ancestors, outermost first.
        val chain = layout.selfAndAncestors(target).toVector.reverse
        val placed = chain.flatMap { owner =>
          layout.rects.get(owner.id).filterNot(_.isEmpty).toSeq.flatMap { area =>
            owner.popups(area).flatMap { popup =>
              visibleAnchor(owner, popup.anchor, layout)
                .map(anchor => place(anchor, popup.width, popup.height, bounds))
                .filterNot(_.isEmpty)
                .map(rect => (owner, popup.content, rect))
            }
          }
        }
        if placed.isEmpty then layout
        else
          placed.foldLeft(layout) { case (acc, (owner, content, rect)) =>
            val sub = layoutManager.resolve(content, rect)
            acc.copy(
              rects = acc.rects ++ sub.rects,
              parents = acc.parents ++ sub.parents + (content.id -> owner.id),
              order = acc.order ++ sub.order,
              popups = acc.popups :+ (content -> rect)
            )
          }

  /**
   * Where a `width × height` popup attached to `anchor` lands within
   * `bounds`.
   *
   * The width is clamped to `bounds`; the left edge starts at the
   * anchor's and moves left until the popup fits. The popup goes below
   * the anchor when its height fits there, else above when it fits
   * there, else on the side with more rows (below on a tie), cut to the
   * rows that side has. The result may be empty when neither side has
   * room.
   */
  def place(anchor: Rect, width: Int, height: Int, bounds: Rect): Rect =
    val w = math.max(0, math.min(width, bounds.width))
    val h = math.max(0, height)
    val x = math.max(bounds.x, math.min(anchor.x, bounds.x + bounds.width - w))
    val anchorBottom = anchor.y + anchor.height
    val below = math.max(0, bounds.y + bounds.height - anchorBottom)
    val above = math.max(0, anchor.y - bounds.y)
    if h <= below then Rect(x, anchorBottom, w, h)
    else if h <= above then Rect(x, anchor.y - h, w, h)
    else if below >= above then Rect(x, anchorBottom, w, below)
    else Rect(x, anchor.y - above, w, above)

  /**
   * `anchor` narrowed to the rect of every `Scrollable` ancestor of
   * `owner`, innermost first, or `None` once nothing of it remains.
   */
  private def visibleAnchor(owner: Component, anchor: Rect, layout: LayoutResult): Option[Rect] =
    val visible = layout
      .ancestorsOf(owner.id)
      .collect { case s: Scrollable => s }
      .flatMap(s => layout.rects.get(s.id))
      .foldLeft(anchor)(intersect)
    Option.when(visible.nonEmpty)(visible)

  /** Intersection of two rects; disjoint inputs yield a zero-size rect. */
  private def intersect(a: Rect, b: Rect): Rect =
    val left = math.max(a.x, b.x)
    val top = math.max(a.y, b.y)
    val right = math.min(a.x + math.max(0, a.width), b.x + math.max(0, b.width))
    val bottom = math.min(a.y + math.max(0, a.height), b.y + math.max(0, b.height))
    Rect(left, top, math.max(0, right - left), math.max(0, bottom - top))
