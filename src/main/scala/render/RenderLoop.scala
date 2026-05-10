package io.github.wickedsik.wsconsole
package render

import buffer.Frame
import component.Component
import event.{Event, EventResult}
import terminal.{Terminal, TerminalSize}

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
  def setFrameRate(fps: Int): UIO[Unit]

  /** Access to the focus manager so applications can drive `Tab` bindings. */
  def focusManager: FocusManager

object RenderLoop:

  /** Default poll cadence for resize detection. */
  val ResizePollInterval: Duration = Duration.fromMillis(100)

  /**
   * Allocate a fresh loop. `renderer` defaults to `Renderer.default`;
   * inject a custom one to swap layout strategies.
   */
  def make(
    renderer: Renderer = Renderer.default
  ): UIO[RenderLoop] =
    for
      redrawQ      <- Queue.unbounded[Unit]
      stopPromise  <- Promise.make[IOException, Unit]
      fpsRef       <- Ref.make(60)
      focus        <- FocusManager.make
    yield new LiveRenderLoop(renderer, redrawQ, stopPromise, fpsRef, focus)

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
    val focusManager: FocusManager
  ) extends RenderLoop:

    def stop: UIO[Unit] =
      stopPromise.succeed(()).unit

    def requestRedraw: UIO[Unit] =
      redrawQ.offer(()).unit

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

          // Initial render fixes the layout for dispatch's first event.
          layout0   <- renderer.renderFull(root)
          _         <- focusManager.updateFocusables(layout0.order)
          layoutRef <- Ref.make(layout0)

          dispatcher = EventDispatcher.make(focusManager)

          // Three signal sources merge into a single stream:
          //   1. Terminal.events (Layer 5)
          //   2. Resize polling (Q4)
          //   3. Internal redraw queue
          eventStream  = terminal.events.map(LoopSignal.Incoming(_))
          resizeStream = pollResize(terminal, sizeRef).map(LoopSignal.Incoming(_))
          redrawStream = ZStream.fromQueue(redrawQ).as(LoopSignal.Redraw)

          merged = eventStream
                     .merge(resizeStream)
                     .merge(redrawStream)
                     .haltWhen(stopPromise)

          _ <- merged.runForeach(processSignal(_, root, dispatcher, layoutRef, onEvent))
        yield ()
      }

    private def pollResize(
      terminal: Terminal,
      sizeRef:  Ref[TerminalSize]
    ): ZStream[Any, IOException, Event.Resize] =
      ZStream
        .repeatZIOWithSchedule(
          terminal.size.flatMap { latest =>
            sizeRef.modify { cached =>
              if latest == cached then (None, cached)
              else (Some(Event.Resize(latest.cols, latest.rows)), latest)
            }
          },
          Schedule.fixed(ResizePollInterval)
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
            _      <- event match
                        case Event.Resize(w, h) =>
                          ZIO.serviceWithZIO[Frame](_.resize(w, h))
                        case _ => ZIO.unit
            layout <- layoutRef.get
            result <- dispatcher.dispatch(event, layout, root)
            keep   <- onEvent(event, result)
            _      <- if !keep then stop
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
        layout <- renderer.renderFull(root)
        _      <- focusManager.updateFocusables(layout.order)
        _      <- layoutRef.set(layout)
      yield ()
