package io.github.wickedsik.wsconsole
package buffer

import component.{Component, RenderContext}
import geometry.Rect
import terminal.Terminal
import zio.*

import java.io.IOException

/**
 * Layer 2 frame primitive.
 *
 * Owns a [[BufferManager]] sized to the terminal. Producers fetch the
 * current frame's [[Canvas]], draw into it, and call [[render]] to flush
 * the diff and rotate buffers.
 *
 * Lifecycle of a Canvas instance: valid from the moment it is fetched
 * until the next [[render]] call. After rendering, fetch a fresh canvas
 * for the next frame.
 *
 * Resize: [[resize]] reconstructs the underlying [[BufferManager]] at
 * new dimensions and emits a clear-screen ANSI so the terminal does not
 * retain stale content. Layer 6's `RenderLoop` calls this on every
 * `Event.Resize` from its size-polling stream.
 */
trait Frame:
  def width:  Int
  def height: Int

  /** A canvas backed by the current buffer. Re-fetch after each render. */
  def canvas: Canvas

  /**
   * Compute diff, flush to terminal, swap buffers.
   *
   * The emitted batch ends with the cursor parked at the frame's
   * bottom-right corner (see `BufferFlusher.toAnsi`). A frame that
   * emits no ops writes no bytes at all.
   */
  def render: IO[IOException, Unit]

  /** Reset the current buffer to all-empty cells without affecting `previous`. */
  def clear: UIO[Unit]

  /**
   * Clear the terminal AND reset both buffers, so the next [[render]]
   * emits every cell of the current frame against a clean slate.
   *
   * This is the only correct way to clear the screen while a `Frame` is
   * active — it emits the ANSI *and* invalidates the buffer baseline
   * atomically. Calling `Terminal.clearScreen` directly would leave the
   * diff believing the previous frame is still on screen, so cells it
   * skips as "unchanged" would linger as ghosts.
   */
  def clearScreen: IO[IOException, Unit]

  /**
   * Reset the `previous` buffer to empty without touching the terminal.
   * The next [[render]] emits every non-empty cell of `current` in one
   * batch — no `\e[2J`, no flicker.
   */
  def invalidate: UIO[Unit]

  /**
   * Reconstruct the [[BufferManager]] at new dimensions and emit a
   * clear-screen ANSI. Existing canvas instances are invalidated.
   *
   * Width/height clamp to a minimum of 1 so transient 0×0 reports
   * during a resize cannot corrupt the buffer.
   */
  def resize(width: Int, height: Int): IO[IOException, Unit]

object Frame:

  // ===== Service Accessors =====

  def canvas: URIO[Frame, Canvas] =
    ZIO.serviceWith[Frame](_.canvas)

  def render: ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](_.render)

  def clear: URIO[Frame, Unit] =
    ZIO.serviceWithZIO[Frame](_.clear)

  def clearScreen: ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](_.clearScreen)

  def invalidate: URIO[Frame, Unit] =
    ZIO.serviceWithZIO[Frame](_.invalidate)

  def width: URIO[Frame, Int] =
    ZIO.serviceWith[Frame](_.width)

  def height: URIO[Frame, Int] =
    ZIO.serviceWith[Frame](_.height)

  def resize(width: Int, height: Int): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](_.resize(width, height))

  /** Run a drawing block against the current canvas. */
  def draw(f: Canvas => Unit): URIO[Frame, Unit] =
    ZIO.serviceWith[Frame](r => f(r.canvas))

  /** Draw, then immediately render. */
  def run(f: Canvas => Unit): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](r => ZIO.succeed(f(r.canvas)) *> r.render)

  /** Render a [[Component]] tree into the full canvas with empty context, then flush. */
  def run(component: Component): ZIO[Frame, IOException, Unit] =
    run(component, RenderContext.empty)

  /** Render a [[Component]] tree into the full canvas with the given context, then flush. */
  def run(component: Component, ctx: RenderContext): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame] { r =>
      ZIO.succeed(component.render(Rect(0, 0, r.width, r.height), r.canvas, ctx)) *> r.render
    }

  // ===== ZLayer =====

  /** Constructs a Frame sized to the terminal's current dimensions. */
  val live: ZLayer[Terminal, IOException, Frame] =
    ZLayer.fromZIO(
      for
        terminal <- ZIO.service[Terminal]
        size     <- terminal.size
      yield BufferFrame(BufferManager.of(size.cols, size.rows), terminal)
    )

private final class BufferFrame(
  initialManager: BufferManager,
  terminal:       Terminal
) extends Frame:

  // Mutable so [[resize]] can swap in a fresh manager.
  private var manager: BufferManager = initialManager

  def width:  Int = manager.current.width
  def height: Int = manager.current.height

  def canvas: Canvas = Canvas(manager.current)

  def render: IO[IOException, Unit] =
    val ops = manager.diff()
    val flush =
      if ops.isEmpty then ZIO.unit
      else
        // Park the cursor at the bottom-right corner — see BufferFlusher.toAnsi.
        terminal.writeBuilder(BufferFlusher.toAnsi(ops, parkAt = Some((width - 1, height - 1))))
    val mirror = ZIO.succeed:
      ops.foreach {
        case RenderOp.ScrollRegionLine(region, line) =>
          manager.previous.appendLineInRegion(region, line)
        case _ => ()
      }
    flush *> terminal.flush *> mirror *> ZIO.succeed(manager.swap())

  def clear: UIO[Unit] = ZIO.succeed(manager.current.clearCells())

  def invalidate: UIO[Unit] = ZIO.succeed(manager.invalidatePrevious())

  def clearScreen: IO[IOException, Unit] =
    for
      _ <- ZIO.succeed { manager = BufferManager.of(width, height) }
      _ <- terminal.writeBuilder(ansi.AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()

  def resize(newWidth: Int, newHeight: Int): IO[IOException, Unit] =
    val w = math.max(1, newWidth)
    val h = math.max(1, newHeight)
    for
      _ <- ZIO.succeed { manager = BufferManager.of(w, h) }
      _ <- terminal.writeBuilder(ansi.AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()
