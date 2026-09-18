package io.github.wickedsik.wsconsole
package render

import buffer.Frame
import component.{Component, FocusSnapshot, RenderContext}
import event.{Event, EventResult}
import terminal.{ResizeSignal, Terminal, TerminalSize}

import zio.*
import zio.stream.ZStream

import java.io.IOException

/**
 * Frame-timing orchestrator for the Layer 6 pipeline.
 *
 * Owns:
 *   - a redraw signal queue — `requestRedraw` enqueues; multiple coalesce
 *   - a stop promise — `stop` completes it, halting the merged stream
 *   - a cached `TerminalSize` — poll-based resize detection
 *
 * Integration:
 *   - `Terminal.events` (Layer 5) supplies typed events
 *   - `EventDispatcher` routes them through the focused component
 *   - Components that return `EventResult.RequestRedraw` schedule a frame
 *   - Resize polling synthesises `Event.Resize` when `terminal.size` changes
 *
 * `RenderContext` is captured at two boundaries — before each render and
 * before each dispatch — pulling focus from `focusManager.focused` and
 * timestamp from `Clock.instant`. The single-fiber loop keeps the
 * snapshot stable for the duration of each render / dispatch.
 *
 * `onEvent` runs after dispatch. Returning `false` stops the loop.
 */
trait RenderLoop:
  def start(
    root: Component,
    onEvent: (Event, EventResult) => ZIO[Terminal & Frame, IOException, Boolean]
  ): ZIO[Terminal & Frame, IOException, Unit]

  def stop: UIO[Unit]
  def requestRedraw: UIO[Unit]

  /**
   * Wipe the diff baseline and emit `\e[2J\e[1;1H` before the next
   * redraw. Every cell of the current frame is re-emitted. Can flicker
   * — reserve for external corruption (subprocess output, manual
   * scrollback) that must be blanked outright; prefer [[requestRefresh]]
   * otherwise.
   */
  def requestFullRedraw: UIO[Unit]

  /**
   * Wipe the diff baseline before the next redraw so every non-empty
   * cell of `current` is re-emitted, without a screen-clear ANSI. No
   * flicker. Use after a layout-context change (panel swap, container
   * reflow) when the terminal display may have drifted from the buffer.
   */
  def requestRefresh: UIO[Unit]

  /** Access to the focus manager so applications can drive `Tab` bindings. */
  def focusManager: FocusManager

object RenderLoop:

  /**
   * Resize-detection tick with `SIGWINCH` available. Each tick reads an
   * atomic flag; the `Terminal.size` query only runs when the kernel
   * reports a resize.
   */
  val ResizePollInterval: Duration = Duration.fromMillis(100)

  /**
   * Resize-detection tick without `SIGWINCH`. Every tick calls
   * `Terminal.size`, which forks a subprocess on the ANSI backend —
   * cadence trades resize latency for not spawning ten processes a second.
   */
  val ResizeFallbackPollInterval: Duration = Duration.fromSeconds(1)

  /** Allocate a fresh loop. */
  def make(
    renderer: Renderer = Renderer.default,
    focusPolicy: FocusPolicy = FocusManager.DefaultPolicy
  ): UIO[RenderLoop] =
    for
      redrawQ     <- Queue.unbounded[Unit]
      stopPromise <- Promise.make[IOException, Unit]
      invalidate  <- Ref.make(false)
      refresh     <- Ref.make(false)
      // Focus mutations self-schedule a frame; without this wire
      // `focusNext` would change state with no visual update.
      focus <- FocusManager.make(focusPolicy, redrawQ.offer(()).unit)
    yield new LiveRenderLoop(renderer, redrawQ, stopPromise, focus, invalidate, refresh)

  // ===== Internal =====

  sealed private trait LoopSignal
  private object LoopSignal:
    final case class Incoming(event: Event) extends LoopSignal
    case object Redraw extends LoopSignal

  final private class LiveRenderLoop(
    renderer: Renderer,
    redrawQ: Queue[Unit],
    stopPromise: Promise[IOException, Unit],
    val focusManager: FocusManager,
    invalidateNext: Ref[Boolean],
    refreshNext: Ref[Boolean]
  ) extends RenderLoop:

    def stop: UIO[Unit] =
      stopPromise.succeed(()).unit

    def requestRedraw: UIO[Unit] =
      redrawQ.offer(()).unit

    def requestFullRedraw: UIO[Unit] =
      invalidateNext.set(true) *> redrawQ.offer(()).unit

    def requestRefresh: UIO[Unit] =
      refreshNext.set(true) *> redrawQ.offer(()).unit

    def start(
      root: Component,
      onEvent: (Event, EventResult) => ZIO[Terminal & Frame, IOException, Boolean]
    ): ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.serviceWithZIO[Terminal] { terminal =>
        for
          // Sync buffer to terminal size before first render, in case
          // it changed between Frame.live's construction and now.
          size0 <- terminal.size
          _ <- ZIO.serviceWithZIO[Frame] { frame =>
                 if frame.width != size0.cols || frame.height != size0.rows then
                   frame.resize(size0.cols, size0.rows)
                 else ZIO.unit
               }
          sizeRef <- Ref.make(size0)
          // Best-effort SIGWINCH install; falls back to direct polling.
          watcher <- ResizeSignal.install

          // Initial render fixes layout for the first dispatch. The
          // focus snapshot lets pre-seeded focus paint in frame 0; if
          // `setOrder` below is the manager's first tree, its `onChange`
          // enqueues a redraw so the corrected frame is the loop's first
          // action, not a state that can linger on screen.
          focused0 <- focusManager.focused
          now0     <- Clock.instant
          ctx0 = RenderContext(FocusSnapshot(focused0), now0)
          layout0   <- renderer.renderFull(root, ctx0)
          _         <- focusManager.setOrder(layout0.focusOrder)
          layoutRef <- Ref.make(layout0)

          dispatcher = EventDispatcher.make(focusManager)

          // Three signal sources merged: terminal events, resize
          // polling, internal redraw queue.
          eventStream = terminal.events.map(LoopSignal.Incoming(_))
          resizeStream = pollResize(terminal, sizeRef, watcher).map(LoopSignal.Incoming(_))
          redrawStream = ZStream.fromQueue(redrawQ).as(LoopSignal.Redraw)

          merged = eventStream
                     .merge(resizeStream)
                     .merge(redrawStream)
                     .haltWhen(stopPromise)

          _ <- merged.runForeach(processSignal(_, root, dispatcher, layoutRef, onEvent))
        yield ()
      }

    /**
     * Emit `Event.Resize` when dimensions change. `Terminal.size` forks
     * a subprocess on the ANSI backend; with `SIGWINCH` the fast tick
     * reads a flag and queries only on kernel notice, without it every
     * tick must query, so the cadence slows.
     */
    private def pollResize(
      terminal: Terminal,
      sizeRef: Ref[TerminalSize],
      watcher: ResizeSignal.Watcher
    ): ZStream[Any, IOException, Event.Resize] =
      val native = watcher.mode == ResizeSignal.Watcher.Mode.Native
      val tick = if native then ResizePollInterval else ResizeFallbackPollInterval

      val queryIfChanged: IO[IOException, Option[Event.Resize]] =
        terminal.size.flatMap { latest =>
          sizeRef.modify { cached =>
            if latest == cached then (None, cached)
            else (Some(Event.Resize(latest.cols, latest.rows)), latest)
          }
        }

      ZStream
        .repeatZIOWithSchedule(
          if native then
            watcher.pending.flatMap {
              case true  => queryIfChanged
              case false => ZIO.none
            }
          else queryIfChanged,
          Schedule.fixed(tick)
        )
        .collect { case Some(e) => e }

    private def processSignal(
      signal: LoopSignal,
      root: Component,
      dispatcher: EventDispatcher,
      layoutRef: Ref[LayoutResult],
      onEvent: (Event, EventResult) => ZIO[Terminal & Frame, IOException, Boolean]
    ): ZIO[Terminal & Frame, IOException, Unit] =
      signal match
        case LoopSignal.Incoming(event) =>
          for
            // Handle resize before dispatch/redraw; `onEvent` still
            // observes the event and may take further action.
            _ <- event match
                   case Event.Resize(w, h) =>
                     ZIO.serviceWithZIO[Frame](_.resize(w, h))
                   case _ => ZIO.unit
            layout  <- layoutRef.get
            focused <- focusManager.focused
            now     <- Clock.instant
            ctx = RenderContext(FocusSnapshot(focused), now)
            result <- dispatcher.dispatch(event, layout, root, ctx)
            // Perform runs before onEvent so the callback observes a
            // world where the component's action has taken place.
            _ <- result match
                   case EventResult.Perform(effect) => effect
                   case _                           => ZIO.unit
            keep <- onEvent(event, result)
            _ <- if !keep then stop
                 else
                   (event, result) match
                     // Resize always redraws — buffer is empty, screen cleared.
                     case (_: Event.Resize, _)           => redraw(root, layoutRef)
                     case (_, EventResult.RequestRedraw) => redraw(root, layoutRef)
                     case (_, _: EventResult.Perform)    => redraw(root, layoutRef)
                     case _                              => ZIO.unit
          yield ()
        case LoopSignal.Redraw =>
          redraw(root, layoutRef)

    private def redraw(
      root: Component,
      layoutRef: Ref[LayoutResult]
    ): ZIO[Frame, IOException, Unit] =
      for
        // Consume pending full-redraw: clear terminal + reset baseline.
        full <- invalidateNext.getAndSet(false)
        _    <- if full then buffer.Frame.clearScreen else ZIO.unit
        // Consume pending refresh: wipe baseline, no screen-clear ANSI.
        refresh <- refreshNext.getAndSet(false)
        _       <- if refresh && !full then buffer.Frame.invalidate else ZIO.unit
        focused <- focusManager.focused
        now     <- Clock.instant
        ctx = RenderContext(FocusSnapshot(focused), now)
        layout <- renderer.renderFull(root, ctx)
        _      <- focusManager.setOrder(layout.focusOrder)
        _      <- layoutRef.set(layout)
      yield ()
