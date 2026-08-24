package io.github.wickedsik.wsconsole
package testkit

import ansi.AnsiBuilder
import event.Event
import terminal.{ColorSupport, RawInput, Terminal, TerminalCapabilities, TerminalSize}

import zio.*
import zio.stream.ZStream

import java.io.IOException

/**
 * One configurable in-memory [[terminal.Terminal]] that subsumes the six
 * hand-rolled doubles across the suite (`ApplicationSpec`, `PanelHostSpec`,
 * `PanelSpec`, `TerminalResourceSpec`, `FrameRefreshSpec`, `FrameResizeSpec`).
 *
 * It captures on two axes:
 *   - '''ops''' — the ordered names of every mutating/lifecycle method invoked
 *     ([[capturedOps]]). Query methods (`size`, `capabilities`, `readRaw`,
 *     `events`) are not recorded, matching the existing recorders.
 *   - '''writes''' — every `write(text)` and every `writeBuilder(_).build`, in
 *     call order ([[capturedWrites]] / [[captured]]).
 *
 * Three configuration knobs collapse the six doubles into constructor variation:
 *   - `size` (default 24×80) — reported by `size` and used for `capabilities`.
 *   - `events` — inject a deterministic event stream (else the idle default).
 *   - `signals` — fire the mapped `Promise` when the named op is first recorded,
 *     replacing the bespoke acquire-Promise wiring in `ApplicationSpec` /
 *     `TerminalResourceSpec`. `succeed` is idempotent, so several op-names may
 *     map to the same `Promise`.
 *
 * '''KI-001.''' `CellStyle.toAnsi` emits its `Set[Attribute]` in non-deterministic
 * order (`docs/known-issues.md`). The write capture is therefore safe for
 * '''position''' and '''glyph''' assertions but must NOT back an exact-byte
 * styled-cell snapshot. Assert style structurally via `Cell`/`CellStyle`
 * equality (see [[GridAssertions]] / [[AnsiGrid]]).
 */
final class CaptureTerminal private (
  opsRef: Ref[Chunk[String]],
  writesRef: Ref[Chunk[String]],
  termSize: TerminalSize,
  caps: TerminalCapabilities,
  eventSource: Option[ZStream[Any, IOException, Event]],
  signals: Map[String, Promise[Nothing, Unit]]
) extends Terminal:

  /** Record an op by name, then fire any signal registered for that op. */
  private def op(name: String): IO[IOException, Unit] =
    opsRef.update(_ :+ name) *>
      signals.get(name).fold[IO[IOException, Unit]](ZIO.unit)(_.succeed(()).unit)

  def enterRawMode: IO[IOException, Unit] = op("enterRawMode")
  def exitRawMode: IO[IOException, Unit] = op("exitRawMode")
  def enterAlternateBuffer: IO[IOException, Unit] = op("enterAlternateBuffer")
  def exitAlternateBuffer: IO[IOException, Unit] = op("exitAlternateBuffer")
  def disableLineWrap: IO[IOException, Unit] = op("disableLineWrap")
  def enableLineWrap: IO[IOException, Unit] = op("enableLineWrap")
  def moveCursor(row: Int, col: Int): IO[IOException, Unit] = op("moveCursor")
  def hideCursor: IO[IOException, Unit] = op("hideCursor")
  def showCursor: IO[IOException, Unit] = op("showCursor")
  def saveCursor: IO[IOException, Unit] = op("saveCursor")
  def restoreCursor: IO[IOException, Unit] = op("restoreCursor")
  def clearScreen: IO[IOException, Unit] = op("clearScreen")
  def clearLine: IO[IOException, Unit] = op("clearLine")

  def write(text: String): IO[IOException, Unit] =
    op("write") *> writesRef.update(_ :+ text)

  def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit] =
    op("writeBuilder") *> writesRef.update(_ :+ builder.build)

  def flush: IO[IOException, Unit] = op("flush")

  def readRaw(timeout: Duration): IO[IOException, RawInput] =
    ZIO.succeed(RawInput.Timeout)

  def size: IO[IOException, TerminalSize] = ZIO.succeed(termSize)

  def capabilities: IO[IOException, TerminalCapabilities] = ZIO.succeed(caps)

  override def events: ZStream[Any, IOException, Event] =
    eventSource.getOrElse(super.events)

  // ===== Capture readers =====

  /** Ordered names of every mutating/lifecycle method invoked so far. */
  def capturedOps: UIO[Chunk[String]] = opsRef.get

  /** Every write in call order: each `write(text)` and each `writeBuilder(_).build`. */
  def capturedWrites: UIO[Chunk[String]] = writesRef.get

  /** All captured writes concatenated — the emitted wire byte stream. */
  def captured: UIO[String] = writesRef.get.map(_.mkString)

  /** Reset the write capture (mirrors the `log.set(Vector.empty)` reset pattern). */
  def clearCaptured: UIO[Unit] = writesRef.set(Chunk.empty)

  /** Reset the op capture. */
  def clearOps: UIO[Unit] = opsRef.set(Chunk.empty)

object CaptureTerminal:

  private def defaultCaps(size: TerminalSize): TerminalCapabilities =
    TerminalCapabilities(ColorSupport.TrueColor, true, true, true, true, size)

  /**
   * Build a fresh CaptureTerminal, allocating its capture `Ref`s.
   *
   * @param size    reported by `size` and used as the capabilities' size
   * @param caps    override the default TrueColor capabilities
   * @param events  inject a deterministic event stream (else the idle default)
   * @param signals fire the mapped Promise when the named op is first recorded
   */
  def make(
    size: TerminalSize = TerminalSize(24, 80),
    caps: Option[TerminalCapabilities] = None,
    events: Option[ZStream[Any, IOException, Event]] = None,
    signals: Map[String, Promise[Nothing, Unit]] = Map.empty
  ): UIO[CaptureTerminal] =
    for
      ops <- Ref.make(Chunk.empty[String])
      writes <- Ref.make(Chunk.empty[String])
    yield new CaptureTerminal(ops, writes, size, caps.getOrElse(defaultCaps(size)), events, signals)

  /**
   * Convenience layer for the pure-dependency case, where a test only needs a
   * `Terminal` in scope and never inspects the capture (the `PanelHostSpec` /
   * `PanelSpec` dummies).
   */
  def layer(
    size: TerminalSize = TerminalSize(24, 80),
    caps: Option[TerminalCapabilities] = None,
    events: Option[ZStream[Any, IOException, Event]] = None,
    signals: Map[String, Promise[Nothing, Unit]] = Map.empty
  ): ZLayer[Any, Nothing, Terminal] =
    ZLayer.fromZIO(make(size, caps, events, signals))
