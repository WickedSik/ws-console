package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Cell, Frame}
import component.{Component, RenderContext}
import geometry.Rect
import terminal.Terminal

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Layer 7 stack-based panel manager.
 *
 * Maintains a `Ref[List[Panel]]` (bottom-to-top order) and exposes a
 * single long-lived [[root]] [[Component]] passed once to
 * `RenderLoop.start`. The root walks the visible stack per render,
 * drawing each panel's `root` within its `bounds`; higher-z panels
 * overdraw lower ones via Layer 2's cell-overlap model.
 *
 * `push` / `pop` / `replace` mutate the stack and request a redraw.
 * Covered panels stay mounted; `onUnload` fires only on removal,
 * `onRemount` only when `pop` reveals a covered panel.
 *
 * Empty-stack `pop` fails with [[PanelHostError.EmptyStack]] on the
 * `IOException` channel, propagating through `Application.run` to end
 * the program. Consumers catch it explicitly for sub-host use.
 *
 * '''Fiber affinity.''' The stack ops are serialised by an internal
 * permit, so the stack cannot be corrupted by concurrent callers. That
 * is not the same as being safe from any fiber: a panel's `onUnload`
 * hook, or an [[Panel.clearBounds]] call inside one, writes cells
 * straight into the live canvas — running from a fiber other than the
 * render loop's races the render walk. Call from `onEvent`, which runs
 * on the loop fiber.
 */
trait PanelHost:
  /** Composite root passed once to `RenderLoop.start`. */
  def root: Component

  def push(panel: Panel): ZIO[Terminal & Frame, IOException, Unit]
  def pop: ZIO[Terminal & Frame, IOException, Unit]
  def replace(panel: Panel): ZIO[Terminal & Frame, IOException, Unit]

  /** Topmost panel, or `None` when the stack is empty. */
  def active: UIO[Option[Panel]]

  /** Full visible list bottom-to-top; topmost is last. */
  def visible: UIO[List[Panel]]

object PanelHost:

  /**
   * Build a host bound to a redraw signal. The signal is typically
   * `Application.requestRedraw`, but tests pass `ZIO.unit` for a
   * silent host.
   */
  def make(requestRedraw: UIO[Unit] = ZIO.unit): UIO[PanelHost] =
    Semaphore.make(1).map(new LivePanelHost(requestRedraw, _))

  // ===== Internal =====

  final private class LivePanelHost(requestRedraw: UIO[Unit], lock: Semaphore) extends PanelHost:

    /**
     * Atomic snapshot of the panel stack, read synchronously by the
     * composite root's `render` method. Mutated only by the ZIO-side
     * `push` / `pop` / `replace` operations, which then signal a
     * redraw — the next render reads the new state.
     */
    private val stackRef = new AtomicReference[List[Panel]](List.empty)

    val root: Component = new Component:
      override def childLayouts(area: Rect): Seq[(Component, Rect)] =
        stackRef.get().map(p => (p.root, p.bounds(area)))

      /**
       * For each panel bottom-to-top, fill its resolved rect with
       * `Cell.Empty` then render its root. The pre-fill guarantees no
       * lower-panel cell bleeds through unwritten cells of a higher
       * panel. Default panels resolve to the full `area`; overlays
       * resolve to their fixed sub-rect.
       */
      def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        val panels = stackRef.get()
        panels.foreach { panel =>
          val rect = panel.bounds(area)
          canvas.fillRect(rect, Cell.Empty)
          panel.root.render(rect, canvas, ctx)
        }

    def active: UIO[Option[Panel]] =
      ZIO.succeed(stackRef.get().lastOption)

    def visible: UIO[List[Panel]] =
      ZIO.succeed(stackRef.get())

    /**
     * Mount-and-append without taking the permit. Shared by `push` and
     * by `replace`'s empty-stack case — the permit is not reentrant, so
     * `replace` must not call `push` directly.
     */
    private def mountAndAppend(panel: Panel): ZIO[Terminal & Frame, IOException, Unit] =
      for
        _ <- panel.onMount
        _ <- ZIO.succeed(stackRef.updateAndGet(_ :+ panel))
        _ <- requestRedraw
      yield ()

    def push(panel: Panel): ZIO[Terminal & Frame, IOException, Unit] =
      lock.withPermit(mountAndAppend(panel))

    // The permit spans read → lifecycle → write. Guarding only the write
    // would leave the read stale across `onUnload`, so a concurrent
    // transition's stack change is silently discarded and its panel is
    // stranded — mounted, invisible, and never unloaded.
    def pop: ZIO[Terminal & Frame, IOException, Unit] =
      lock.withPermit {
        ZIO.suspendSucceed {
          stackRef.get().reverse match
            case Nil =>
              ZIO.fail(PanelHostError.EmptyStack)
            case top :: restReversed =>
              val newStack = restReversed.reverse
              for
                _ <- top.onUnload
                _ <- ZIO.succeed(stackRef.set(newStack))
                _ <- newStack.lastOption.fold(ZIO.unit: ZIO[Terminal & Frame, IOException, Unit])(_.onRemount)
                _ <- requestRedraw
              yield ()
        }
      }

    def replace(panel: Panel): ZIO[Terminal & Frame, IOException, Unit] =
      lock.withPermit {
        ZIO.suspendSucceed {
          stackRef.get().reverse match
            case Nil =>
              // No existing top — `replace` on an empty stack degenerates to `push`.
              mountAndAppend(panel)
            case top :: restReversed =>
              val below = restReversed.reverse
              for
                _ <- top.onUnload
                _ <- panel.onMount
                // Swap the top in a single write so the render walk never
                // sees the underlying panel exposed.
                _ <- ZIO.succeed(stackRef.set(below :+ panel))
                _ <- requestRedraw
              yield ()
        }
      }
