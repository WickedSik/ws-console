package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Frame}
import component.Component
import geometry.Rect
import terminal.Terminal

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Layer 7 stack-based panel manager (Q7 ratified — Option E).
 *
 * Maintains a `Ref[List[Panel]]` (bottom-to-top order) and exposes a
 * single long-lived [[root]] [[Component]] that is passed once to
 * `RenderLoop.start`. The root component walks the visible stack on
 * every render call, drawing each panel's `root` within its `bounds`;
 * higher-z panels overdraw lower ones via Layer 2's existing cell-
 * overlap model. No per-panel buffers, no compositor, no transparency.
 *
 * `push` / `pop` / `replace` mutate the internal stack and request a
 * redraw — they never restart the render loop. Lifecycle is tied to
 * stack membership (covered panels stay mounted and continue
 * rendering); `onUnload` fires only when a panel is removed from the
 * stack, `onRemount` only when a `pop` reveals a previously-covered
 * panel.
 *
 * Empty-stack `pop` fails with [[PanelHostError.EmptyStack]] on the
 * `IOException` channel — by design, the failure propagates through
 * `Application.run` and ends the program. Consumers catch it
 * explicitly for sub-host (modal) use cases.
 */
trait PanelHost:
  /** Composite root passed once to `RenderLoop.start`. */
  def root: Component

  def push   (panel: Panel): ZIO[Terminal & Frame, IOException, Unit]
  def pop:                   ZIO[Terminal & Frame, IOException, Unit]
  def replace(panel: Panel): ZIO[Terminal & Frame, IOException, Unit]

  /** Topmost panel, or `None` when the stack is empty. */
  def active:  UIO[Option[Panel]]

  /** Full visible list bottom-to-top; topmost is last. */
  def visible: UIO[List[Panel]]

object PanelHost:

  /**
   * Build a host bound to a redraw signal. The signal is typically
   * `Application.requestRedraw`, but tests pass `ZIO.unit` for a
   * silent host.
   */
  def make(requestRedraw: UIO[Unit] = ZIO.unit): UIO[PanelHost] =
    ZIO.succeed(new LivePanelHost(requestRedraw))

  // ===== Internal =====

  private final class LivePanelHost(requestRedraw: UIO[Unit]) extends PanelHost:

    /**
     * Atomic snapshot of the panel stack, read synchronously by the
     * composite root's `render` method. Mutated only by the ZIO-side
     * `push` / `pop` / `replace` operations, which then signal a
     * redraw — the next render reads the new state.
     */
    private val stackRef = new AtomicReference[List[Panel]](List.empty)

    val root: Component = new Component:
      override def childLayouts(area: Rect): Seq[(Component, Rect)] =
        stackRef.get().map(p => (p.root, p.bounds))

      def render(area: Rect, canvas: Canvas): Unit =
        val panels = stackRef.get()
        panels.foreach { panel =>
          panel.root.render(panel.bounds, canvas)
        }

    def active: UIO[Option[Panel]] =
      ZIO.succeed(stackRef.get().lastOption)

    def visible: UIO[List[Panel]] =
      ZIO.succeed(stackRef.get())

    def push(panel: Panel): ZIO[Terminal & Frame, IOException, Unit] =
      for
        _ <- panel.onMount
        _ <- ZIO.succeed(stackRef.updateAndGet(_ :+ panel))
        _ <- requestRedraw
      yield ()

    def pop: ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.suspendSucceed {
        val current = stackRef.get()
        current.reverse match
          case Nil =>
            ZIO.fail(PanelHostError.EmptyStack)
          case top :: restReversed =>
            val belowReversed = restReversed
            val newStack      = belowReversed.reverse
            for
              _ <- top.onUnload
              _ <- ZIO.succeed(stackRef.set(newStack))
              _ <- newStack.lastOption.fold(ZIO.unit: ZIO[Terminal & Frame, IOException, Unit])(_.onRemount)
              _ <- requestRedraw
            yield ()
      }

    def replace(panel: Panel): ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.suspendSucceed {
        val current = stackRef.get()
        current.reverse match
          case Nil =>
            // No existing top — `replace` on an empty stack degenerates to `push`.
            push(panel)
          case top :: restReversed =>
            val below = restReversed.reverse
            for
              _ <- top.onUnload
              _ <- panel.onMount
              // Atomically swap the top in a single state transition so the
              // render walk never sees the underlying panel exposed.
              _ <- ZIO.succeed(stackRef.set(below :+ panel))
              _ <- requestRedraw
            yield ()
      }
