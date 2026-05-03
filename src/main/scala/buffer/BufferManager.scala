package io.github.wickedsik.wsconsole
package buffer

/**
 * Coordinates double-buffering for flicker-free rendering.
 *
 * Holds two equally-sized buffers: `current` (where components draw this
 * frame) and `previous` (the state already on screen). After flushing the
 * diff, `swap` rotates the buffers and clears the new current — which
 * matches the architecture's immediate-mode rendering policy: every frame
 * fully repaints, the diff engine ensures only changed cells reach the
 * terminal.
 */
trait BufferManager:
  def current:  ScreenBuffer
  def previous: ScreenBuffer

  /**
   * Rotate buffers: the current buffer becomes the previous one, and the
   * previous buffer becomes the new current after being cleared.
   */
  def swap(): Unit

  /** Compute the diff of current against previous. */
  def diff(): List[CellUpdate]

object BufferManager:
  def of(width: Int, height: Int): BufferManager = MutableBufferManager(width, height)

private final class MutableBufferManager(width: Int, height: Int) extends BufferManager:
  private var currentBuf:  ScreenBuffer = ScreenBuffer.of(width, height)
  private var previousBuf: ScreenBuffer = ScreenBuffer.of(width, height)

  def current:  ScreenBuffer = currentBuf
  def previous: ScreenBuffer = previousBuf

  def swap(): Unit =
    val tmp = previousBuf
    previousBuf = currentBuf
    currentBuf  = tmp
    currentBuf.clear()

  def diff(): List[CellUpdate] = currentBuf.diff(previousBuf)
