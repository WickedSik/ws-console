package io.github.wickedsik.wsconsole
package render

import component.{Component, ComponentId, RenderContext}
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
 * The `RenderContext` is captured by the caller just before dispatch
 * and threaded through every `Component.handleEvent` invocation along
 * the bubble path. Components may guard handlers on
 * `ctx.focus.isFocused(this.id)` rather than caching focus locally.
 *
 * Dispatch returns a single [[EventResult]] — bubbling is the only
 * result-folding, no composition rule is needed.
 */
trait EventDispatcher:
  def dispatch(
    event:  Event,
    layout: LayoutResult,
    root:   Component,
    ctx:    RenderContext
  ): UIO[EventResult]

object EventDispatcher:

  /**
   * The default dispatcher — backed by a `FocusManager`. Stateless
   * itself; all focus state lives in the `FocusManager`.
   */
  def make(focusManager: FocusManager): EventDispatcher =
    new EventDispatcher:
      def dispatch(
        event:  Event,
        layout: LayoutResult,
        root:   Component,
        ctx:    RenderContext
      ): UIO[EventResult] =
        event match
          case _: KeyEvent =>
            focusManager.focused.map {
              case Some(id) => deliverWithBubbling(event, layout, id, ctx)
              case None     => deliverWithBubbling(event, layout, root.id, ctx)
            }
          case _: MouseEvent =>
            // Reserved: Layer 5 does not yet emit mouse events.
            ZIO.succeed(EventResult.Ignored)
          case _: Event.Resize =>
            // Resize is handled at the render-loop layer.
            ZIO.succeed(EventResult.Ignored)

      private def deliverWithBubbling(
        event:  Event,
        layout: LayoutResult,
        target: ComponentId,
        ctx:    RenderContext
      ): EventResult =
        val componentById = layout.order.iterator.map(c => c.id -> c).toMap
        var current       = componentById.get(target)
        while current.isDefined do
          val c   = current.get
          val res = c.handleEvent(event, ctx)
          if res != EventResult.Ignored then return res
          current = layout.parents.get(c.id).flatMap(componentById.get)
        EventResult.Ignored
