package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId}
import geometry.Rect

/**
 * Walks a [[Component]] tree and resolves a [[Rect]] for every node.
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
 *     walk, in pre-order. `FocusManager` filters this for `focusable`
 *     components when computing focus cycles.
 */
trait LayoutManager:
  def resolve(root: Component, area: Rect): LayoutResult

object LayoutManager:

  val default: LayoutManager = new LayoutManager:
    def resolve(root: Component, area: Rect): LayoutResult =
      val rects   = scala.collection.mutable.Map.empty[ComponentId, Rect]
      val parents = scala.collection.mutable.Map.empty[ComponentId, ComponentId]
      val order   = Vector.newBuilder[Component]

      def walk(c: Component, r: Rect, parent: Option[ComponentId]): Unit =
        rects(c.id) = r
        parent.foreach(pid => parents(c.id) = pid)
        order += c
        c.childLayouts(r).foreach { case (child, childRect) =>
          walk(child, childRect, Some(c.id))
        }

      walk(root, area, None)
      LayoutResult(rects.toMap, parents.toMap, order.result())
