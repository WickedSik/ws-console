package io.github.wickedsik.wsconsole
package buffer

import terminal.Terminal
import zio.*

import java.io.IOException

/**
 * Layer 2 rendering service.
 *
 * Owns a [[BufferManager]] sized to the terminal at construction time.
 * Components fetch the current frame's [[Canvas]], draw into it, and call
 * [[render]] to flush the diff and rotate buffers.
 *
 * Lifecycle of a Canvas instance: valid from the moment it is fetched
 * until the next [[render]] call. After rendering, fetch a fresh canvas
 * for the next frame.
 *
 * Resize handling is deferred — the buffer is sized once when the layer
 * is constructed.
 */
trait Renderer:
  def width:  Int
  def height: Int

  /** A canvas backed by the current buffer. Re-fetch after each render. */
  def canvas: Canvas

  /** Compute diff, flush to terminal, swap buffers (clearing the new current). */
  def render: IO[IOException, Unit]

  /** Reset the current buffer to all-empty cells without affecting `previous`. */
  def clear: UIO[Unit]

object Renderer:

  // ===== Service Accessors =====

  def canvas: URIO[Renderer, Canvas] =
    ZIO.serviceWith[Renderer](_.canvas)

  def render: ZIO[Renderer, IOException, Unit] =
    ZIO.serviceWithZIO[Renderer](_.render)

  def clear: URIO[Renderer, Unit] =
    ZIO.serviceWithZIO[Renderer](_.clear)

  def width: URIO[Renderer, Int] =
    ZIO.serviceWith[Renderer](_.width)

  def height: URIO[Renderer, Int] =
    ZIO.serviceWith[Renderer](_.height)

  /**
   * Run a side-effecting drawing block against the current canvas. The
   * function receives the canvas and may freely call its methods; the
   * result is wrapped as a ZIO effect.
   */
  def draw[A](f: Canvas => A): URIO[Renderer, A] =
    ZIO.serviceWith[Renderer](r => f(r.canvas))

  /** Draw, then immediately render. The common per-frame pattern. */
  def frame[A](f: Canvas => A): ZIO[Renderer, IOException, A] =
    ZIO.serviceWithZIO[Renderer](r => ZIO.succeed(f(r.canvas)) <* r.render)

  // ===== ZLayer =====

  /**
   * Layer that constructs a Renderer sized to the terminal's current
   * dimensions. Fails with IOException if size detection fails.
   */
  val live: ZLayer[Terminal, IOException, Renderer] =
    ZLayer.fromZIO(
      for
        terminal <- ZIO.service[Terminal]
        size     <- terminal.size
      yield BufferRenderer(BufferManager.of(size.cols, size.rows), terminal)
    )

private final class BufferRenderer(
  manager:  BufferManager,
  terminal: Terminal
) extends Renderer:

  val width:  Int = manager.current.width
  val height: Int = manager.current.height

  def canvas: Canvas = Canvas(manager.current)

  def render: IO[IOException, Unit] =
    val updates = manager.diff()
    val flush =
      if updates.isEmpty then ZIO.unit
      else terminal.writeBuilder(BufferFlusher.toAnsi(updates))
    flush *> terminal.flush *> ZIO.succeed(manager.swap())

  def clear: UIO[Unit] = ZIO.succeed(manager.current.clear())
