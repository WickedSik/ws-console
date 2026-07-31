package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Cell, Frame}
import component.{Component, RenderContext}
import event.Event
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
 *
 * '''Fiber affinity.''' `push` / `pop` / `replace` are serialised
 * against each other by an internal permit, so the stack itself can
 * never be corrupted by concurrent callers. That is not the same as
 * being safe to call from any fiber: [[Panel.onUnload]] defaults to
 * [[Panel.clearBounds]], which writes cells straight into the live
 * canvas. Running that from a fiber other than the render loop's races
 * the render walk, and no amount of stack-level locking fixes it.
 *
 * Call these from `onEvent`, which runs on the loop fiber. They become
 * genuinely any-fiber once the default `onUnload` stops writing
 * directly (see ADR-003 Q6). The permit is here because the signatures
 * hand consumers a `ZIO` and should not lie about the stack.
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

  /**
   * Bridges [[Panel.onRawEvent]] to [[Application.run]]'s `onRawEvent`
   * parameter. Reads the active (topmost) panel on each event and
   * delegates to its `onRawEvent` if present. Returns `true` when no
   * panel is active or the active panel has no tap — the framework
   * proceeds with normal dispatch.
   *
   * Consumer pattern:
   * {{{
   *   host <- PanelHost.make(app.requestRedraw)
   *   ...
   *   _ <- app.run(root, onEvent, onRawEvent = host.rawEventTap)
   * }}}
   */
  def rawEventTap: Event => ZIO[Terminal & Frame, IOException, Boolean] =
    event => active.flatMap {
      case Some(panel) => panel.onRawEvent.fold(ZIO.succeed(true))(fn => fn(event))
      case None        => ZIO.succeed(true)
    }

object PanelHost:

  /**
   * Build a host bound to a redraw signal. The signal is typically
   * `Application.requestRedraw`, but tests pass `ZIO.unit` for a
   * silent host.
   */
  def make(requestRedraw: UIO[Unit] = ZIO.unit): UIO[PanelHost] =
    Semaphore.make(1).map(new LivePanelHost(requestRedraw, _))

  // ===== Internal =====

  private final class LivePanelHost(requestRedraw: UIO[Unit], lock: Semaphore) extends PanelHost:

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

      /**
       * Composite render: for each panel bottom-to-top, first fill its
       * bounds with `Cell.Empty` (opacity contract — Q1 ratified
       * 2026-07-31), then render the panel's root into those bounds.
       *
       * The pre-fill guarantees no cell of a lower panel bleeds through
       * a higher panel's unwritten cells, regardless of what the higher
       * panel's root writes. Matches `component.Panel:41–45`'s
       * discipline, lifted to the composing layer so an arbitrary
       * `Component` can be a panel root without carrying its own
       * opacity obligation.
       */
      def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        val panels = stackRef.get()
        panels.foreach { panel =>
          canvas.fillRect(panel.bounds, Cell.Empty)
          panel.root.render(panel.bounds, canvas, ctx)
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
