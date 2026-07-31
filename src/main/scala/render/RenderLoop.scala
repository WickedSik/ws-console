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
 *   - a frame-rate `Ref[Int]` — `setFrameRate` updates the upper bound
 *   - a cached `TerminalSize` — poll-based resize detection (Q4 ratified)
 *
 * Integration:
 *   - `Terminal.events` (Layer 5) supplies typed events
 *   - `EventDispatcher` routes them through the focused component
 *   - Components that return `EventResult.RequestRedraw` schedule a frame
 *   - Resize polling synthesises `Event.Resize` when `terminal.size` changes
 *
 * `RenderContext` is captured by the loop at two boundaries:
 *   - Before each frame's render (from `focusManager.focused`), then
 *     threaded through `renderer.renderFull(root, ctx)`.
 *   - Before each event's dispatch (also from `focusManager.focused`),
 *     then threaded through `dispatcher.dispatch(event, layout, root, ctx)`.
 *
 * The single-fiber loop guarantees the snapshot is stable for the
 * duration of each render / dispatch — focus does not flip mid-frame.
 *
 * The application-level callback `onEvent` runs *after* dispatch, with
 * the dispatcher's result in hand. Returning `false` stops the loop —
 * the canonical `q` / `Ctrl+C` exit pattern lives there.
 */
trait RenderLoop:
  def start(
    root:    Component,
    onEvent: (Event, EventResult) => UIO[Boolean]
  ): ZIO[Terminal & Frame, IOException, Unit]

  def stop:                   UIO[Unit]
  def requestRedraw:          UIO[Unit]

  /**
   * Force a full repaint on the next redraw — the buffer's `previous`
   * state is wiped before the diff so every cell of the current frame
   * is emitted to the terminal. Necessary after a layout-context change
   * (e.g. swapping the active panel) when the terminal display may no
   * longer be in lockstep with the buffer state.
   *
   * Emits a synchronous `\e[2J\e[1;1H` clear-screen as part of the
   * reset, which can produce a visible flicker. Prefer
   * [[requestRefresh]] when the goal is "re-emit the full frame without
   * trusting the terminal kept cells fresh"; reserve this primitive for
   * cases where the terminal display is known to contain external
   * corruption (subprocess output, manual scrollback) that must be
   * blanked outright.
   */
  def requestFullRedraw:      UIO[Unit]

  /**
   * Invalidate the diff baseline on the next redraw — the buffer's
   * `previous` is reset to empty before the diff so every non-empty
   * cell of `current` is re-emitted in one writeBuilder. No `\e[2J`
   * is emitted, so there is no flicker.
   *
   * Use after a layout-context change (panel swap, container reflow)
   * when the terminal display may have drifted from the buffer model.
   * The diff's "unchanged cells stayed on screen" assumption breaks
   * for transitions that should re-establish the entire frame; this
   * primitive enforces the re-establishment cheaply.
   */
  def requestRefresh:         UIO[Unit]

  def setFrameRate(fps: Int): UIO[Unit]

  /** Access to the focus manager so applications can drive `Tab` bindings. */
  def focusManager: FocusManager

object RenderLoop:

  /**
   * Tick cadence for resize detection when `SIGWINCH` is available. Each
   * tick is an atomic flag read; the expensive `Terminal.size` query runs
   * only on ticks where the kernel actually reported a resize.
   */
  val ResizePollInterval: Duration = Duration.fromMillis(100)

  /**
   * Tick cadence when `SIGWINCH` could not be installed. Every tick calls
   * `Terminal.size`, which forks a subprocess on the ANSI backend, so the
   * cadence trades resize latency for not spawning ten processes a second.
   */
  val ResizeFallbackPollInterval: Duration = Duration.fromSeconds(1)

  /**
   * Allocate a fresh loop. `renderer` defaults to `Renderer.default`;
   * inject a custom one to swap layout strategies. `focusPolicy`
   * defaults to `FocusManager.DefaultPolicy` (`MoveToFirstOnRemoval`);
   * pass an explicit policy to override.
   */
  def make(
    renderer:    Renderer    = Renderer.default,
    focusPolicy: FocusPolicy = FocusManager.DefaultPolicy
  ): UIO[RenderLoop] =
    for
      redrawQ      <- Queue.unbounded[Unit]
      stopPromise  <- Promise.make[IOException, Unit]
      fpsRef       <- Ref.make(60)
      invalidate   <- Ref.make(false)
      refresh      <- Ref.make(false)
      // Focus mutations self-schedule a frame by enqueueing on the
      // redraw queue. Without this wire, `focusNext` would silently
      // change state with no visual update — the only redraw paths
      // are `EventResult.RequestRedraw` from dispatch and explicit
      // `request[Full]Redraw` calls.
      focus        <- FocusManager.make(focusPolicy, redrawQ.offer(()).unit)
    yield new LiveRenderLoop(renderer, redrawQ, stopPromise, fpsRef, focus, invalidate, refresh)

  // ===== Internal =====

  private sealed trait LoopSignal
  private object LoopSignal:
    final case class Incoming(event: Event) extends LoopSignal
    case object Redraw                       extends LoopSignal

  private final class LiveRenderLoop(
    renderer:     Renderer,
    redrawQ:      Queue[Unit],
    stopPromise:  Promise[IOException, Unit],
    fpsRef:       Ref[Int],
    val focusManager:  FocusManager,
    invalidateNext:    Ref[Boolean],
    refreshNext:       Ref[Boolean]
  ) extends RenderLoop:

    def stop: UIO[Unit] =
      stopPromise.succeed(()).unit

    def requestRedraw: UIO[Unit] =
      redrawQ.offer(()).unit

    def requestFullRedraw: UIO[Unit] =
      invalidateNext.set(true) *> redrawQ.offer(()).unit

    def requestRefresh: UIO[Unit] =
      refreshNext.set(true) *> redrawQ.offer(()).unit

    def setFrameRate(fps: Int): UIO[Unit] =
      fpsRef.set(math.max(0, fps))

    def start(
      root:    Component,
      onEvent: (Event, EventResult) => UIO[Boolean]
    ): ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.serviceWithZIO[Terminal] { terminal =>
        for
          // Sync the buffer to the terminal's current size *before* the
          // first render, in case the terminal was resized between
          // Frame.live's construction and now.
          size0   <- terminal.size
          _       <- ZIO.serviceWithZIO[Frame] { frame =>
                       if frame.width != size0.cols || frame.height != size0.rows then
                         frame.resize(size0.cols, size0.rows)
                       else ZIO.unit
                     }
          sizeRef <- Ref.make(size0)
          // Best-effort SIGWINCH install; falls back to direct polling.
          watcher <- ResizeSignal.install

          // Initial render fixes the layout for dispatch's first event.
          // Capture the focus snapshot from the FocusManager so any focus
          // the application seeded *before* calling run (via setOrder +
          // focus(id)) is visible in the very first frame — components
          // that read ctx.focus.isFocused will draw their focused style
          // on startup, not one frame later after the first event.
          //
          // The application has not always seeded an order, though: when
          // `setOrder` below is the first the manager hears of the tree,
          // the policy may auto-focus an entry that `layout0` was already
          // drawn without. That `setOrder` fires `onChange`, which enqueues
          // a redraw before the stream starts — so the corrected frame is
          // the loop's first action rather than a state the screen can
          // linger in.
          focused0  <- focusManager.focused
          ctx0       = RenderContext(FocusSnapshot(focused0))
          layout0   <- renderer.renderFull(root, ctx0)
          _         <- focusManager.setOrder(layout0.focusOrder)
          layoutRef <- Ref.make(layout0)

          dispatcher = EventDispatcher.make(focusManager)

          // Three signal sources merge into a single stream:
          //   1. Terminal.events (Layer 5)
          //   2. Resize polling (Q4)
          //   3. Internal redraw queue
          eventStream  = terminal.events.map(LoopSignal.Incoming(_))
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
     * Emit `Event.Resize` when the terminal's dimensions change.
     *
     * `Terminal.size` forks a subprocess on the ANSI backend, so it is
     * called as rarely as correctness allows. With a live `SIGWINCH`
     * handler the fast tick only reads an atomic flag and the query runs
     * solely when the kernel reported a resize; without one, every tick
     * must query, so the tick slows down to match.
     */
    private def pollResize(
      terminal: Terminal,
      sizeRef:  Ref[TerminalSize],
      watcher:  ResizeSignal.Watcher
    ): ZStream[Any, IOException, Event.Resize] =
      val native = watcher.mode == ResizeSignal.Watcher.Mode.Native
      val tick   = if native then ResizePollInterval else ResizeFallbackPollInterval

      val queryIfChanged: IO[IOException, Option[Event.Resize]] =
        terminal.size.flatMap { latest =>
          sizeRef.modify { cached =>
            if latest == cached then (None, cached)
            else (Some(Event.Resize(latest.cols, latest.rows)), latest)
          }
        }

      ZStream
        .repeatZIOWithSchedule(
          if native then watcher.pending.flatMap {
            case true  => queryIfChanged
            case false => ZIO.none
          }
          else queryIfChanged,
          Schedule.fixed(tick)
        )
        .collect { case Some(e) => e }

    private def processSignal(
      signal:     LoopSignal,
      root:       Component,
      dispatcher: EventDispatcher,
      layoutRef:  Ref[LayoutResult],
      onEvent:    (Event, EventResult) => UIO[Boolean]
    ): ZIO[Frame, IOException, Unit] =
      signal match
        case LoopSignal.Incoming(event) =>
          for
            // Framework-level handling of resize: reconstruct the buffer
            // and clear the terminal *before* dispatch / redraw. The
            // application's `onEvent` callback still sees the event and
            // may take additional action.
            _       <- event match
                         case Event.Resize(w, h) =>
                           ZIO.serviceWithZIO[Frame](_.resize(w, h))
                         case _ => ZIO.unit
            layout  <- layoutRef.get
            // Capture the focus snapshot at the dispatch boundary —
            // mirrors the per-frame snapshot the loop captures before
            // rendering. Stable for the duration of this dispatch.
            focused <- focusManager.focused
            ctx      = RenderContext(FocusSnapshot(focused))
            result  <- dispatcher.dispatch(event, layout, root, ctx)
            keep    <- onEvent(event, result)
            _       <- if !keep then stop
                       else event match
                         // Resize always triggers a redraw; the buffer is
                         // freshly empty and the screen has been cleared.
                         case _: Event.Resize =>
                           redraw(root, layoutRef)
                         case _ if result == EventResult.RequestRedraw =>
                           redraw(root, layoutRef)
                         case _ =>
                           ZIO.unit
          yield ()
        case LoopSignal.Redraw =>
          redraw(root, layoutRef)

    private def redraw(
      root:      Component,
      layoutRef: Ref[LayoutResult]
    ): ZIO[Frame, IOException, Unit] =
      for
        // Consume any pending "full redraw" request: clear the
        // terminal display and reset the diff's buffer baseline so
        // every cell of the current frame is re-emitted from scratch.
        full    <- invalidateNext.getAndSet(false)
        _       <- if full then buffer.Frame.clearScreen else ZIO.unit
        // Consume any pending "refresh" request: wipe the diff baseline
        // so every non-empty cell of the new frame is emitted, but
        // without a screen-clear ANSI. No flicker; useful at layout-
        // context transitions where the terminal display may have
        // drifted from the buffer model.
        refresh <- refreshNext.getAndSet(false)
        _       <- if refresh && !full then buffer.Frame.invalidate else ZIO.unit
        // Capture the per-frame snapshot of framework state. The
        // single-fiber loop guarantees this is stable for the
        // duration of the render walk (React's "props don't change
        // during render" guarantee).
        focused <- focusManager.focused
        ctx      = RenderContext(FocusSnapshot(focused))
        layout  <- renderer.renderFull(root, ctx)
        // Install the new frame's focus order. The configured
        // FocusPolicy reconciles current focus against the new order
        // (drop, move-to-first, or custom).
        _       <- focusManager.setOrder(layout.focusOrder)
        _       <- layoutRef.set(layout)
      yield ()
