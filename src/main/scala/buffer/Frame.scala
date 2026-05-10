package io.github.wickedsik.wsconsole
package buffer

import component.Component
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
 * `Event.Resize` from its size-polling stream (Q4 ratified 2026-05-10).
 */
trait Frame:
  def width:  Int
  def height: Int

  /** A canvas backed by the current buffer. Re-fetch after each render. */
  def canvas: Canvas

  /** Compute diff, flush to terminal, swap buffers (clearing the new current). */
  def render: IO[IOException, Unit]

  /** Reset the current buffer to all-empty cells without affecting `previous`. */
  def clear: UIO[Unit]

  /**
   * Reconstruct the underlying [[BufferManager]] at the new dimensions
   * and emit a clear-screen ANSI. The next call to [[canvas]] returns a
   * fresh canvas at `width × height`. Existing canvas instances are
   * invalidated — fetch a new one after resize.
   *
   * Width / height clamp to a minimum of 1 to avoid zero-size buffers
   * (which would crash `ScreenBuffer.of`). Terminals that report 0×0
   * during a transient resize will not corrupt the buffer.
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

  def width: URIO[Frame, Int] =
    ZIO.serviceWith[Frame](_.width)

  def height: URIO[Frame, Int] =
    ZIO.serviceWith[Frame](_.height)

  def resize(width: Int, height: Int): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](_.resize(width, height))

  /**
   * Run a side-effecting drawing block against the current canvas. The
   * function receives the canvas and may freely call its methods.
   */
  def draw(f: Canvas => Unit): URIO[Frame, Unit] =
    ZIO.serviceWith[Frame](r => f(r.canvas))

  /** Draw, then immediately render. The common per-frame pattern. */
  def run(f: Canvas => Unit): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame](r => ZIO.succeed(f(r.canvas)) *> r.render)

  /**
   * Render a [[Component]] tree filling the frame's full canvas, then
   * immediately flush. The Layer 4 analogue of `run(f: Canvas => Unit)`.
   */
  def run(component: Component): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame] { r =>
      ZIO.succeed(component.render(Rect(0, 0, r.width, r.height), r.canvas)) *> r.render
    }

  // ===== ZLayer =====

  /**
   * Layer that constructs a Frame sized to the terminal's current
   * dimensions. Fails with IOException if size detection fails.
   */
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

  // Mutable so [[resize]] can swap in a fresh manager. All accessors read
  // the live ref; canvas instances are invalidated by a resize.
  private var manager: BufferManager = initialManager

  def width:  Int = manager.current.width
  def height: Int = manager.current.height

  def canvas: Canvas = Canvas(manager.current)

  def render: IO[IOException, Unit] =
    val ops = manager.diff()
    val flush =
      if ops.isEmpty then ZIO.unit
      else terminal.writeBuilder(BufferFlusher.toAnsi(ops))
    val mirror = ZIO.succeed:
      ops.foreach {
        case RenderOp.ScrollRegionLine(region, line) =>
          manager.previous.appendLineInRegion(region, line)
        case _ => ()
      }
    flush *> terminal.flush *> mirror *> ZIO.succeed(manager.swap())

  def clear: UIO[Unit] = ZIO.succeed(manager.current.clearCells())

  def resize(newWidth: Int, newHeight: Int): IO[IOException, Unit] =
    val w = math.max(1, newWidth)
    val h = math.max(1, newHeight)
    for
      _ <- ZIO.succeed { manager = BufferManager.of(w, h) }
      // Clear stale terminal content + reset cursor. Buffer is empty so
      // the next render's diff naturally repaints everything visible.
      _ <- terminal.writeBuilder(ansi.AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()
