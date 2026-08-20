package io.github.wickedsik.wsconsole
package app

import buffer.Frame
import component.Component
import event.{Event, EventResult, KeyEvent, KeyModifier}
import event.KeyEvent.CharKey
import render.{FocusManager, RenderLoop}
import terminal.Terminal

import zio.*

import java.io.IOException

/**
 * Layer 7 application envelope — composes Layer 1's terminal lifecycle
 * (alt buffer, hidden cursor, raw mode) with Layer 6's `RenderLoop`,
 * absorbing the three manual `ZIO.acquireRelease` blocks at
 * `DemoApp.scala:117–119`.
 *
 * Acquisition order on entry: alt buffer → hidden cursor → raw mode.
 * Release order is the reverse on every exit path, including
 * interruption — `ZIO.scoped` + `acquireRelease` enforces both.
 *
 * Termination paths:
 *   - The render loop's event stream ends naturally.
 *   - `quit` is called (stops the underlying loop).
 *   - An unhandled error propagates from dispatch or render.
 *   - A key in `quitOn` bubbles to `Ignored` (no component claimed it)
 *     and the consumer's `onEvent` returns without vetoing — the quit
 *     rule fires *after* `onEvent`, only on an `Ignored` result.
 *
 * Error channel: `IOException` for terminal I/O failures. "User
 * requested exit" is a clean termination, not an error.
 */
trait Application:

  /**
   * Run the application body. Acquires the terminal lifecycle, then
   * delegates to the internal `RenderLoop`. `run` keeps `Terminal` for
   * itself — the callback does not receive it.
   *
   * `onEvent` runs *before* the framework applies `quitOn` — nothing
   * is hidden from the consumer, and returning `false` stops the loop.
   * Defaults to "continue forever."
   *
   * `quitOn` fires only when the dispatch result is `Ignored`. A
   * focused component's `Perform` / `RequestRedraw` / `Consumed` answer
   * vetoes the framework's quit, so a text field can bind `Ctrl+C` to
   * copy without losing the key to the exit binding.
   *
   * The `onEvent` effect is typed `ZIO[Frame, IOException, Boolean]`
   * so a callback can describe frame-level work and nothing wider.
   * `Terminal` appears in no consumer signature; the framework alone
   * writes to it.
   */
  def run(
    root:    Component,
    onEvent: (Event, EventResult) => ZIO[Frame, IOException, Boolean] = Application.continueForever
  ): ZIO[Terminal & Frame, IOException, Unit]

  /** Signal a clean termination — stops the underlying render loop. */
  def quit: UIO[Unit]

  /** Request a redraw on the next loop tick. Exposed for `PanelHost` and consumers. */
  def requestRedraw: UIO[Unit]

  /**
   * Request a *full* redraw on the next loop tick — every cell of the
   * current frame is re-emitted to the terminal, ignoring the diff
   * cache. Emits a synchronous `\e[2J` clear-screen as part of the
   * reset, producing a visible flicker. Prefer [[requestRefresh]] for
   * panel swaps and other layout-context transitions; reserve this
   * for cases where the terminal display is known to be externally
   * corrupted (subprocess ANSI emission, manual scrollback).
   */
  def requestFullRedraw: UIO[Unit]

  /**
   * Request a *refresh* on the next loop tick — the diff baseline is
   * wiped before the next render, so every non-empty cell of the
   * current frame is emitted in a single writeBuilder. No screen-clear
   * ANSI is emitted, so there is no flicker.
   *
   * Use after a layout-context change (panel swap, container reflow)
   * when the terminal display may have drifted from the buffer model.
   */
  def requestRefresh: UIO[Unit]

  /** Access to the underlying focus manager for Tab-cycle bindings. */
  def focusManager: FocusManager

object Application:

  /** Default consumer-side `onEvent`: keep looping. */
  val continueForever: (Event, EventResult) => ZIO[Frame, IOException, Boolean] =
    (_, _) => ZIO.succeed(true)

  /**
   * Default key that triggers automatic `quit`:
   *   - `Ctrl+C` — in raw mode this arrives as a parsed event, not SIGINT
   *
   * Consumers wanting additional exit shortcuts (e.g. `q`, `Esc`) pass
   * them via [[make(quitOn)]]. A shortcut sitting in `quitOn` still
   * loses to any focused component that answers non-`Ignored`, per the
   * §6.3 rule; this is deliberate — it keeps text fields usable.
   */
  val defaultQuitOn: Set[KeyEvent] = Set(
    CharKey('c', Set(KeyModifier.Ctrl))
  )

  /** Build with the default `quitOn` set. */
  def make: UIO[Application] = make(defaultQuitOn)

  /**
   * Build with a custom `quitOn` set. An empty set disables the
   * automatic quit binding — the consumer must call `app.quit`
   * explicitly from `onEvent` to terminate.
   */
  def make(quitOn: Set[KeyEvent]): UIO[Application] =
    RenderLoop.make().map(loop => new LiveApplication(loop, quitOn))

  /** Alias for `make` — matches the architecture-doc sketch. */
  val default: UIO[Application] = make

  // ===== Internal =====

  private final class LiveApplication(
    loop:   RenderLoop,
    quitOn: Set[KeyEvent]
  ) extends Application:

    def quit:              UIO[Unit]    = loop.stop
    def requestRedraw:     UIO[Unit]    = loop.requestRedraw
    def requestFullRedraw: UIO[Unit]    = loop.requestFullRedraw
    def requestRefresh:    UIO[Unit]    = loop.requestRefresh
    def focusManager:      FocusManager = loop.focusManager

    def run(
      root:    Component,
      onEvent: (Event, EventResult) => ZIO[Frame, IOException, Boolean] = continueForever
    ): ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.scoped {
        // Wraps the consumer's `onEvent` with the framework's `quitOn`
        // rule (§6.3): applied *after* onEvent, and only when the
        // dispatch result is `Ignored`. A focused component's
        // non-`Ignored` answer vetoes the framework's quit for that
        // keystroke.
        val wrappedOnEvent: (Event, EventResult) => ZIO[Terminal & Frame, IOException, Boolean] =
          (event, result) =>
            onEvent(event, result).map { consumerKeep =>
              val quit = result == EventResult.Ignored && (event match
                case k: KeyEvent => quitOn.contains(k)
                case _           => false)
              consumerKeep && !quit
            }

        for
          _ <- ZIO.acquireRelease(Terminal.enterAlternateBuffer)(_ => Terminal.exitAlternateBuffer.ignore)
          _ <- ZIO.acquireRelease(Terminal.disableLineWrap)(_ => Terminal.enableLineWrap.ignore)
          _ <- ZIO.acquireRelease(Terminal.hideCursor)(_ => Terminal.showCursor.ignore)
          _ <- ZIO.acquireRelease(Terminal.enterRawMode)(_ => Terminal.exitRawMode.ignore)
          _ <- loop.start(root, wrappedOnEvent)
        yield ()
      }
