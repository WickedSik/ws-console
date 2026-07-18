package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId}
import geometry.Rect

/**
 * Walks a [[Component]] tree and resolves a [[Rect]] for every node,
 * plus a [[FocusOrder]] of focusable components visible in this frame.
 *
 * Bridges Layer 3's layout engine with Layer 4's component tree shape
 * via the `Component.childLayouts` accessor. Pure: no I/O, no ZIO
 * effects.
 *
 * Contract:
 *   - Every component reachable from `root` (via `childLayouts`) appears
 *     as a key in `LayoutResult.rects`.
 *   - Each component's rect is the rect supplied by its parent's
 *     `childLayouts(parentArea)`.
 *   - The parent map is built in the same pass; `EventDispatcher` reads
 *     it for bubbling.
 *   - The `order` field carries every component encountered during the
 *     walk, in pre-order.
 *   - The `focusOrder` field collects components with `focusable = true`
 *     AND a non-empty rect, in the same pre-order. `FocusManager`
 *     consumes this directly via `setOrder` — no second tree walk and
 *     no per-component registration.
 */
trait LayoutManager:
  def resolve(root: Component, area: Rect): LayoutResult

object LayoutManager:

  val default: LayoutManager = new LayoutManager:
    def resolve(root: Component, area: Rect): LayoutResult =
      val rects      = scala.collection.mutable.Map.empty[ComponentId, Rect]
      val parents    = scala.collection.mutable.Map.empty[ComponentId, ComponentId]
      val order      = Vector.newBuilder[Component]
      val focusables = Vector.newBuilder[FocusableEntry]

      def walk(c: Component, r: Rect, parent: Option[ComponentId]): Unit =
        rects(c.id) = r
        parent.foreach(pid => parents(c.id) = pid)
        order += c
        if c.focusable && !r.isEmpty then
          focusables += FocusableEntry(c.id, r)
        c.childLayouts(r).foreach { case (child, childRect) =>
          walk(child, childRect, Some(c.id))
        }

      walk(root, area, None)
      LayoutResult(rects.toMap, parents.toMap, order.result(), FocusOrder(focusables.result()))
