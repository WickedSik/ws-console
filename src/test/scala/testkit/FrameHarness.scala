package io.github.wickedsik.wsconsole
package testkit

import ansi.AnsiBuilder
import buffer.{BufferFlusher, BufferManager, Canvas, Frame, RenderOp, ScreenBuffer}
import component.{Component, RenderContext}
import terminal.TerminalSize

import zio.*

import java.io.IOException

/**
 * A leaked-manager [[buffer.Frame]] that runs the REAL diff → flush → swap
 * pipeline over a [[CaptureTerminal]]. Reproduces `BufferFrame.render`
 * (`buffer/Frame.scala:175-186`) faithfully — the production frame hides its
 * `BufferManager`, so the harness re-implements the render step to expose the
 * manager for inspection.
 *
 * '''The swap trap.''' After `render`, `manager.swap()` rotates the just-drawn
 * `current` into `previous` and blanks the new `current`. The drawn frame is
 * therefore read from [[FrameHarness.drawnBuffer]] (== `manager.previous`),
 * never `current`.
 */
private final class HarnessFrame(initial: BufferManager, terminal: CaptureTerminal) extends Frame:

  // Mutable so resize / clearScreen can swap in a fresh manager, mirroring
  // the production BufferFrame. The FrameHarness reads the live ref.
  var manager: BufferManager = initial

  def width:  Int    = manager.current.width
  def height: Int    = manager.current.height
  def canvas: Canvas = Canvas(manager.current)

  def render: IO[IOException, Unit] =
    val ops = manager.diff()
    val flush =
      if ops.isEmpty then ZIO.unit
      else terminal.writeBuilder(BufferFlusher.toAnsi(ops))
    val mirror = ZIO.succeed(
      ops.foreach {
        case RenderOp.ScrollRegionLine(region, line) =>
          manager.previous.appendLineInRegion(region, line)
        case _ => ()
      }
    )
    flush *> terminal.flush *> mirror *> ZIO.succeed(manager.swap())

  def clear: UIO[Unit] = ZIO.succeed(manager.current.clearCells())

  def invalidate: UIO[Unit] = ZIO.succeed(manager.invalidatePrevious())

  def clearScreen: IO[IOException, Unit] =
    for
      _ <- ZIO.succeed { manager = BufferManager.of(width, height) }
      _ <- terminal.writeBuilder(AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()

  def resize(newWidth: Int, newHeight: Int): IO[IOException, Unit] =
    val w = math.max(1, newWidth)
    val h = math.max(1, newHeight)
    for
      _ <- ZIO.succeed { manager = BufferManager.of(w, h) }
      _ <- terminal.writeBuilder(AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()

/**
 * Integration render harness. Drives a real [[buffer.Frame]] (the full
 * diff → `BufferFlusher` → `CaptureTerminal.writeBuilder` → swap path) and
 * exposes both the emitted ANSI and the drawn buffer, so wire-level and
 * cell-level assertions can be made about the same frame.
 */
final class FrameHarness private (frameImpl: HarnessFrame, val terminal: CaptureTerminal):

  /** The `Frame` service, for `Frame.run` / manual provision. */
  def frame: Frame = frameImpl

  /** A layer providing this harness's `Frame`. */
  def frameLayer: ULayer[Frame] = ZLayer.succeed[Frame](frameImpl)

  /**
   * The buffer holding the most-recently drawn-and-swapped frame. After a
   * `render`, the drawn frame is rotated into `previous` (the swap trap), so
   * THIS — not `currentBuffer` — is the frame that was just emitted.
   */
  def drawnBuffer: ScreenBuffer = frameImpl.manager.previous

  /** The in-progress buffer the next frame draws into (blank after a render). */
  def currentBuffer: ScreenBuffer = frameImpl.manager.current

  /** The concatenated wire bytes emitted so far. */
  def captured: UIO[String] = terminal.captured

  /** The individual `writeBuilder(_).build` writes emitted so far. */
  def capturedWrites: UIO[Chunk[String]] = terminal.capturedWrites

  /** Reset the wire capture between frames. */
  def clearCaptured: UIO[Unit] = terminal.clearCaptured

  /** Draw `component` into the current frame and flush it (real diff → swap). */
  def run(component: Component, ctx: RenderContext = RenderContext.empty): IO[IOException, Unit] =
    Frame.run(component, ctx).provideLayer(frameLayer)

object FrameHarness:

  /** Build a harness over a `width × height` frame backed by a CaptureTerminal. */
  def make(width: Int, height: Int): UIO[FrameHarness] =
    CaptureTerminal
      .make(size = TerminalSize(rows = height, cols = width))
      .map(term => new FrameHarness(new HarnessFrame(BufferManager.of(width, height), term), term))

  /**
   * Single-shot convenience: render `component` once through a real frame and
   * return `(emittedAnsi, drawnBuffer)`. The `CaptureTerminal` never fails, so
   * the render's `IOException` channel is discharged with `orDie`.
   */
  def renderFrame(component: Component, width: Int, height: Int): UIO[(String, ScreenBuffer)] =
    for
      harness <- make(width, height)
      _       <- harness.run(component).orDie
      ansi    <- harness.captured
    yield (ansi, harness.drawnBuffer)
