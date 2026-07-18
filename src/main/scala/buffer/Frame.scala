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
   * Clear the terminal display AND reset both buffers, so the next
   * [[render]] emits every cell of the current frame against a clean
   * slate.
   *
   * Layered above `Terminal.clearScreen`: that primitive emits the
   * ANSI but leaves the buffer's diff cache thinking the screen still
   * holds the previous frame's content. Calling `Terminal.clearScreen`
   * directly while a `Frame` is active is a sync hazard — the diff
   * would skip emitting cells it believes are unchanged, leaving
   * ghosts. `Frame.clearScreen` is the only correct way to clear the
   * screen when a Frame is in use: it emits the ANSI *and* invalidates
   * the buffer baseline atomically.
   *
   * Use after a layout-context change (panel swap, container reflow,
   * external display corruption) when the buffer's `previous` can no
   * longer be trusted to match the terminal. Emits `\e[2J\e[1;1H`
   * synchronously and reconstructs the underlying `BufferManager` at
   * the current dimensions.
   */
  def clearScreen: IO[IOException, Unit]

  /**
   * Reset the `previous` buffer to all-empty without touching the
   * terminal. The next [[render]] sees every non-empty cell of `current`
   * as a fresh write and emits them all in a single batch — no `\e[2J`,
   * no flicker.
   *
   * Use after a layout-context change (panel swap, container reflow)
   * when the terminal display may have drifted from the buffer model.
   * Cheaper and flicker-free compared to [[clearScreen]], which emits
   * an explicit screen-clear ANSI.
   */
  def invalidate: UIO[Unit]

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
   * Render a [[Component]] tree filling the frame's full canvas with an
   * empty [[RenderContext]], then immediately flush. The Layer 4
   * analogue of `run(f: Canvas => Unit)`.
   *
   * Tests and consumers that do not need framework-state snapshots use
   * this overload. Consumers driving their own render loops with focus
   * state use the explicit-ctx overload.
   */
  def run(component: Component): ZIO[Frame, IOException, Unit] =
    run(component, RenderContext.empty)

  /**
   * Render a [[Component]] tree filling the frame's full canvas with
   * the supplied context, then immediately flush.
   */
  def run(component: Component, ctx: RenderContext): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWithZIO[Frame] { r =>
      ZIO.succeed(component.render(Rect(0, 0, r.width, r.height), r.canvas, ctx)) *> r.render
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

  def invalidate: UIO[Unit] = ZIO.succeed(manager.invalidatePrevious())

  def clearScreen: IO[IOException, Unit] =
    for
      // Reconstruct both buffers so the next render's diff compares
      // against a guaranteed-empty baseline.
      _ <- ZIO.succeed { manager = BufferManager.of(width, height) }
      // Blank the actual terminal so cells no longer rendered by the
      // new tree don't linger as ghost content.
      _ <- terminal.writeBuilder(ansi.AnsiBuilder().clearScreen.moveTo(1, 1))
      _ <- terminal.flush
    yield ()

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
