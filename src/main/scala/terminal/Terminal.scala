package io.github.wickedsik.wsconsole
package terminal

import ansi.AnsiBuilder
import event.{Event, TerminalEvents}
import zio.*
import zio.stream.ZStream

import java.io.IOException

/**
 * Layer 1 Terminal abstraction - the foundation for all higher layers.
 *
 * Provides raw terminal I/O operations: cursor control, screen manipulation,
 * text output, and raw input reading. This is deliberately low-level.
 * Higher layers (Buffer, Layout, Components) build upon this contract.
 *
 * All operations use IOException as the error channel, consistent with
 * the project's error handling convention.
 */
trait Terminal:

  // ===== Lifecycle =====

  /** Enter raw mode - disables line buffering, echo, and canonical processing */
  def enterRawMode: IO[IOException, Unit]

  /** Exit raw mode - restores previous terminal settings */
  def exitRawMode: IO[IOException, Unit]

  /** Switch to alternate screen buffer (preserves main screen content) */
  def enterAlternateBuffer: IO[IOException, Unit]

  /** Return to main screen buffer */
  def exitAlternateBuffer: IO[IOException, Unit]

  /**
   * Disable automatic line wrapping (DECAWM `?7`).
   *
   * Required when the consumer positions every cell explicitly and must
   * not have the terminal wrap content under the cursor. Writing the
   * rightmost column of a row with auto-wrap enabled leaves the terminal
   * in pending-wrap state; at the bottom-right corner of the alternate
   * buffer this can cause subsequent cell writes to be silently dropped
   * on some terminals.
   */
  def disableLineWrap: IO[IOException, Unit]

  /** Re-enable automatic line wrapping (DECAWM `?7`). */
  def enableLineWrap: IO[IOException, Unit]

  // ===== Cursor =====

  /** Move cursor to absolute position (1-indexed) */
  def moveCursor(row: Int, col: Int): IO[IOException, Unit]

  /** Hide the cursor */
  def hideCursor: IO[IOException, Unit]

  /** Show the cursor */
  def showCursor: IO[IOException, Unit]

  /** Save current cursor position */
  def saveCursor: IO[IOException, Unit]

  /** Restore previously saved cursor position */
  def restoreCursor: IO[IOException, Unit]

  // ===== Screen =====

  /** Clear entire screen */
  def clearScreen: IO[IOException, Unit]

  /** Clear current line */
  def clearLine: IO[IOException, Unit]

  // ===== Scroll =====

  /** Set scrolling region to specific line range (1-indexed, inclusive) */
  def setScrollRegion(top: Int, bottom: Int): IO[IOException, Unit]

  /** Reset scrolling region to full screen */
  def resetScrollRegion: IO[IOException, Unit]

  // ===== Output =====

  /** Write text to terminal output (does not flush) */
  def write(text: String): IO[IOException, Unit]

  /** Write an AnsiBuilder's content to terminal output and flush */
  def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit]

  /** Flush terminal output */
  def flush: IO[IOException, Unit]

  // ===== Input =====

  /** Read raw input bytes from terminal, with timeout */
  def readRaw(timeout: Duration): IO[IOException, RawInput]

  /**
   * Stream of typed events parsed from raw terminal input.
   *
   * Default implementation drives the Layer 5 [[event.EventParser]] over
   * `readRaw`. Implementations are free to override (e.g. a test stub may
   * supply a deterministic event sequence).
   *
   * The stream terminates on end-of-input. Lone `ESC` is disambiguated from
   * alt-prefix sequences via a 50 ms timeout - see
   * [[event.TerminalEvents.LoneEscTimeout]].
   */
  def events: ZStream[Any, IOException, Event] =
    TerminalEvents.events(this)

  // ===== Info =====

  /** Query current terminal dimensions */
  def size: IO[IOException, TerminalSize]

  /** Get detected terminal capabilities */
  def capabilities: IO[IOException, TerminalCapabilities]

object Terminal:

  // ===== ZIO Service Accessors =====

  def enterRawMode: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.enterRawMode)

  def exitRawMode: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.exitRawMode)

  def enterAlternateBuffer: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.enterAlternateBuffer)

  def exitAlternateBuffer: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.exitAlternateBuffer)

  def disableLineWrap: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.disableLineWrap)

  def enableLineWrap: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.enableLineWrap)

  def moveCursor(row: Int, col: Int): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.moveCursor(row, col))

  def hideCursor: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.hideCursor)

  def showCursor: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.showCursor)

  def saveCursor: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.saveCursor)

  def restoreCursor: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.restoreCursor)

  def clearScreen: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.clearScreen)

  def clearLine: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.clearLine)

  def setScrollRegion(top: Int, bottom: Int): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.setScrollRegion(top, bottom))

  def resetScrollRegion: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.resetScrollRegion)

  def write(text: String): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.write(text))

  def writeBuilder(builder: AnsiBuilder): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.writeBuilder(builder))

  def flush: ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.flush)

  def readRaw(timeout: Duration): ZIO[Terminal, IOException, RawInput] =
    ZIO.serviceWithZIO[Terminal](_.readRaw(timeout))

  /** Service-style accessor: stream typed events from the Terminal in scope. */
  def events: ZStream[Terminal, IOException, Event] =
    ZStream.serviceWithStream[Terminal](_.events)

  def size: ZIO[Terminal, IOException, TerminalSize] =
    ZIO.serviceWithZIO[Terminal](_.size)

  def capabilities: ZIO[Terminal, IOException, TerminalCapabilities] =
    ZIO.serviceWithZIO[Terminal](_.capabilities)

  // ===== Resource Management Helpers =====

  /**
   * Execute an effect within the alternate screen buffer.
   * Buffer is entered before the effect and exited on completion or interruption.
   */
  def withAlternateBuffer[R <: Terminal, E >: IOException, A](
    effect: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(enterAlternateBuffer)(_ => exitAlternateBuffer.ignore)(
      _ => effect
    )

  /**
   * Execute an effect with the cursor hidden.
   * Cursor is hidden before the effect and shown on completion or interruption.
   */
  def withHiddenCursor[R <: Terminal, E >: IOException, A](
    effect: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(hideCursor)(_ => showCursor.ignore)(
      _ => effect
    )

  /**
   * Execute an effect in raw mode.
   * Raw mode is entered before the effect and exited on completion or interruption.
   */
  def withRawMode[R <: Terminal, E >: IOException, A](
    effect: ZIO[R, E, A]
  ): ZIO[R, E, A] =
    ZIO.acquireReleaseWith(enterRawMode)(_ => exitRawMode.ignore)(
      _ => effect
    )
