package io.github.wickedsik.wsconsole
package render

import component.ComponentId
import geometry.Rect

import zio.{Chunk, Ref, UIO, ZIO}

/**
 * Tracks dirty regions to minimise per-frame work.
 *
 * **Q2 resolved 2026-05-10: ship the trait + `alwaysDirty` default.**
 *
 * Selective dirty-region tracking is *not* in scope this iteration —
 * the trait is recorded so future consumers can swap in a smarter
 * implementation without breaking the pipeline contract. The default
 * implementation reports every component as dirty every frame; the
 * Layer 2 diff phase already minimises terminal I/O, so the optimizer
 * only matters when component-tree walks themselves become a hot path.
 */
trait RenderOptimizer:
  def shouldRedraw(component: ComponentId): UIO[Boolean]
  def dirtyRegions:                         UIO[Chunk[Rect]]
  def markDirty(rect: Rect):                UIO[Unit]
  def clearDirty():                         UIO[Unit]

object RenderOptimizer:

  /**
   * The default optimizer — every component is dirty every frame.
   * Cheap and simple; correct for full-frame redraw semantics.
   */
  val alwaysDirty: RenderOptimizer = new RenderOptimizer:
    def shouldRedraw(component: ComponentId): UIO[Boolean] = ZIO.succeed(true)
    def dirtyRegions:                         UIO[Chunk[Rect]] = ZIO.succeed(Chunk.empty)
    def markDirty(rect: Rect):                UIO[Unit] = ZIO.unit
    def clearDirty():                         UIO[Unit] = ZIO.unit

  /**
   * A region-tracking optimizer skeleton — accumulates dirty rects in a
   * `Ref`. `shouldRedraw` returns `true` whenever any region is
   * recorded; finer-grained "is this component's rect inside a dirty
   * region" lookup is a follow-up. Reserved for future consumers that
   * drive the requirement.
   */
  def regionTracking: UIO[RenderOptimizer] =
    Ref.make(Chunk.empty[Rect]).map { ref =>
      new RenderOptimizer:
        def shouldRedraw(component: ComponentId): UIO[Boolean] =
          ref.get.map(_.nonEmpty)
        def dirtyRegions: UIO[Chunk[Rect]] =
          ref.get
        def markDirty(rect: Rect): UIO[Unit] =
          ref.update(_ :+ rect)
        def clearDirty(): UIO[Unit] =
          ref.set(Chunk.empty)
    }
