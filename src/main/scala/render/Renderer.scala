package io.github.wickedsik.wsconsole
package render

import buffer.Frame
import component.Component
import geometry.Rect

import zio.ZIO

import java.io.IOException

/**
 * The Layer 6 orchestrator — drives a single frame end-to-end.
 *
 * Composes the four-phase [[RenderPipeline]] with the Layer 2 [[Frame]]
 * primitive. Returns the resolved `LayoutResult` so that callers
 * (typically the render loop) can drive event dispatch off the same
 * tree walk.
 *
 * Naming disambiguation (Q1 resolved 2026-05-10): the Layer 2
 * `buffer.Renderer` was renamed to `buffer.Frame`; the Layer 6
 * orchestrator owns the unqualified `Renderer` name in package
 * `render`.
 */
trait Renderer:
  /** Render `root` into `area`. Returns the resolved layout for dispatch. */
  def render(root: Component, area: Rect): ZIO[Frame, IOException, LayoutResult]

  /** Render `root` filling the full frame area. */
  def renderFull(root: Component): ZIO[Frame, IOException, LayoutResult]

object Renderer:

  /** The default orchestrator backed by `RenderPipeline.default`. */
  val default: Renderer = make(RenderPipeline.default)

  def make(pipeline: RenderPipeline): Renderer =
    new Renderer:
      def render(root: Component, area: Rect): ZIO[Frame, IOException, LayoutResult] =
        pipeline.runFrame(root, area)

      def renderFull(root: Component): ZIO[Frame, IOException, LayoutResult] =
        ZIO.serviceWithZIO[Frame] { frame =>
          pipeline.runFrame(root, Rect(0, 0, frame.width, frame.height))
        }
