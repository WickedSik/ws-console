package io.github.wickedsik.wsconsole
package render

import buffer.{Canvas, Frame}
import component.{Component, ComponentId, RenderContext}
import geometry.Rect

import zio.ZIO

import java.io.IOException

/**
 * The four-phase rendering pipeline as composable steps.
 *
 *   1. **Layout** — walk the component tree, produce a `LayoutResult`
 *      keyed by `ComponentId`, including the frame's `FocusOrder`. When
 *      `reveal` names a component, [[RevealPass]] scrolls its
 *      `Scrollable` ancestors to show it, and the tree is laid out again
 *      if anything moved — so the reveal lands on this frame.
 *   2. **Draw** — invoke `Component.render(area, canvas, ctx)` to write
 *      cells into the current buffer.
 *   3. **Diff** — the Layer 2 `BufferManager` computes the diff against
 *      `previous` (delegated, no Layer 6 implementation).
 *   4. **Flush** — Layer 2's `BufferFlusher` emits ANSI; the buffer
 *      swap rotates `current` into `previous`.
 *
 * Steps 3 + 4 are encapsulated by `Frame.render` (the per-frame
 * primitive). The pipeline's load-bearing job is *coordination* —
 * sequencing the phases and threading the `LayoutResult` into dispatch.
 *
 * Returns the `LayoutResult` so the caller (typically `RenderLoop`) can
 * pass it into `EventDispatcher` and update the `FocusManager`.
 */
trait RenderPipeline:
  def runFrame(
    root: Component,
    area: Rect,
    ctx: RenderContext,
    reveal: Option[ComponentId] = None
  ): ZIO[Frame, IOException, LayoutResult]

object RenderPipeline:

  /** The default pipeline composing `LayoutManager.default` with `Frame.render`. */
  val default: RenderPipeline = make(LayoutManager.default)

  def make(layoutManager: LayoutManager): RenderPipeline =
    new RenderPipeline:
      def runFrame(
        root: Component,
        area: Rect,
        ctx: RenderContext,
        reveal: Option[ComponentId]
      ): ZIO[Frame, IOException, LayoutResult] =
        ZIO.serviceWithZIO[Frame] { frame =>
          val first = layoutManager.resolve(root, area)
          val layout =
            if reveal.exists(RevealPass(first, _)) then layoutManager.resolve(root, area)
            else first
          val canvas = frame.canvas
          ZIO.succeed(drawPhase(root, area, canvas, ctx)) *>
            frame.render.as(layout)
        }

      private def drawPhase(root: Component, area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        if !area.isEmpty then root.render(area, canvas, ctx)
