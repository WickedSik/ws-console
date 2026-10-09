package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId}
import geometry.Rect

/**
 * Output of [[LayoutManager.resolve]] — the resolved rect for every
 * component reachable from the root, plus parent-chain information used
 * by [[EventDispatcher]] for bubbling and a [[FocusOrder]] used by
 * [[FocusManager]] for Tab cycling.
 *
 * The `parents` map answers "what is the parent of component X?" without
 * a second tree walk during dispatch. Built in the same pass as `rects`.
 *
 * The `focusOrder` field collects every focusable component encountered
 * during the walk, in render order, with its assigned rect. Components
 * in collapsed branches (zero-size rect) are excluded — "what you can
 * Tab to is exactly what is rendered, focusable, and visible right now."
 *
 * Components that resolve to a zero-size rect still appear in `rects`
 * (with `width=0` or `height=0`); the draw phase short-circuits on
 * zero-size rather than skipping the entry.
 *
 * `popups` holds the placed popup content of this frame in draw order,
 * filled by [[PopupPass]] (ADR-007). Popup subtrees also appear in
 * `rects`, `parents` (the content root's parent is the popup's owner)
 * and `order`, but never in `focusOrder`.
 */
final case class LayoutResult(
  rects: Map[ComponentId, Rect],
  parents: Map[ComponentId, ComponentId],
  order: Vector[Component],
  focusOrder: FocusOrder,
  popups: Vector[(Component, Rect)] = Vector.empty
):
  def rectOf(component: Component): Option[Rect] =
    rects.get(component.id)

  def parentOf(component: Component): Option[Component] =
    parents.get(component.id).flatMap(pid => order.find(_.id == pid))

object LayoutResult:
  val empty: LayoutResult = LayoutResult(Map.empty, Map.empty, Vector.empty, FocusOrder.empty)
