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
 *   - A key in `quitOn` reaches the dispatch layer — handled before
 *     the consumer's `onEvent` callback fires.
 *
 * Error channel: `IOException` for terminal I/O failures. "User
 * requested exit" is a clean termination, not an error.
 */
trait Application:

  /**
   * Run the application body. Acquires the terminal lifecycle, then
   * delegates to the internal `RenderLoop`.
   *
   * `onEvent` runs *after* the framework recognises `quitOn` keys; it
   * receives both the event and the dispatcher's result. Returning
   * `false` stops the loop. Defaults to "continue forever."
   */
  def run(
    root:    Component,
    onEvent: (Event, EventResult) => UIO[Boolean] = Application.continueForever
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
  val continueForever: (Event, EventResult) => UIO[Boolean] =
    (_, _) => ZIO.succeed(true)

  /**
   * Default keys that trigger automatic `quit`:
   *   - `q`      — graceful quit
   *   - `Ctrl+C` — in raw mode this arrives as a parsed event, not SIGINT
   */
  val defaultQuitOn: Set[KeyEvent] = Set(
    CharKey('q', Set.empty),
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
      onEvent: (Event, EventResult) => UIO[Boolean] = continueForever
    ): ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.scoped {
        val wrappedOnEvent: (Event, EventResult) => UIO[Boolean] =
          (event, result) =>
            event match
              case k: KeyEvent if quitOn.contains(k) => ZIO.succeed(false)
              case _                                 => onEvent(event, result)

        for
          _ <- ZIO.acquireRelease(Terminal.enterAlternateBuffer)(_ => Terminal.exitAlternateBuffer.ignore)
          _ <- ZIO.acquireRelease(Terminal.disableLineWrap)(_ => Terminal.enableLineWrap.ignore)
          _ <- ZIO.acquireRelease(Terminal.hideCursor)(_ => Terminal.showCursor.ignore)
          _ <- ZIO.acquireRelease(Terminal.enterRawMode)(_ => Terminal.exitRawMode.ignore)
          _ <- loop.start(root, wrappedOnEvent)
        yield ()
      }
