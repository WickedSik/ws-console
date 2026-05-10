package io.github.wickedsik.wsconsole
package render

import component.Component
import event.{Event, EventResult, KeyEvent, MouseEvent}

import zio.{UIO, ZIO}

/**
 * Routes a parsed [[Event]] to the appropriate component(s).
 *
 * Routing rules:
 *   - **`KeyEvent`** → focused component (via `FocusManager.focused`);
 *     bubbles up the parent chain on `Ignored`. If no component is
 *     focused, falls through to the root.
 *   - **`MouseEvent`** *(reserved; not yet emitted by Layer 5)* →
 *     component whose layout rect contains the cursor position; bubbles
 *     up the parent chain on `Ignored`.
 *   - **`Resize`** → application-level handler (the render loop
 *     re-computes layout for the next frame); not delivered to
 *     individual components.
 *
 * **Single return value per dispatch (Q3 resolved 2026-05-10).** With
 * `EventFilter`/`EventListener` deferred, no composition rule is needed
 * — bubbling is the only result-folding at this iteration.
 */
trait EventDispatcher:
  def dispatch(event: Event, layout: LayoutResult, root: Component): UIO[EventResult]

object EventDispatcher:

  /**
   * The default dispatcher — backed by a `FocusManager`. Stateless
   * itself; all focus state lives in the `FocusManager`.
   */
  def make(focusManager: FocusManager): EventDispatcher =
    new EventDispatcher:
      def dispatch(event: Event, layout: LayoutResult, root: Component): UIO[EventResult] =
        event match
          case _: KeyEvent =>
            focusManager.focused.map {
              case Some(id) => deliverWithBubbling(event, layout, id)
              case None     => deliverWithBubbling(event, layout, root.id)
            }
          case _: MouseEvent =>
            // Reserved: Layer 5 does not yet emit mouse events. The
            // routing logic below is the contract for when emission lands.
            ZIO.succeed(EventResult.Ignored)
          case _: Event.Resize =>
            // Resize is handled at the application/render-loop layer; not
            // delivered to components.
            ZIO.succeed(EventResult.Ignored)

      private def deliverWithBubbling(
        event:  Event,
        layout: LayoutResult,
        target: component.ComponentId
      ): EventResult =
        val componentById = layout.order.iterator.map(c => c.id -> c).toMap
        var current       = componentById.get(target)
        while current.isDefined do
          val c   = current.get
          val res = c.handleEvent(event)
          if res != EventResult.Ignored then return res
          current = layout.parents.get(c.id).flatMap(componentById.get)
        EventResult.Ignored
