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
 *
 * `diff()` orchestrates the full op stream:
 *
 *   1. Region transition op (`SetScrollRegion` or `ResetScrollRegion`) when
 *      `current.scrollRegion` differs from `previous.scrollRegion`
 *   2. `ScrollRegionLine` ops drained from `current.pendingScrollLines`
 *   3. Cell ops from `current.diff(previous)` (which skips region rows when
 *      pending scroll-line ops exist)
 *
 * `swap()` propagates the scroll-region declaration from the outgoing
 * `current` to the new `current` so panels don't re-declare every frame.
 */
trait BufferManager:
  def current:  ScreenBuffer
  def previous: ScreenBuffer

  /**
   * Rotate buffers: the current buffer becomes the previous one, and the
   * previous buffer becomes the new current after being cleared. The
   * scroll-region declaration is propagated to the new current.
   */
  def swap(): Unit

  /** Compute the full op stream for this frame. Pure read of buffer state. */
  def diff(): Seq[RenderOp]

object BufferManager:
  def of(width: Int, height: Int): BufferManager = MutableBufferManager(width, height)

private final class MutableBufferManager(width: Int, height: Int) extends BufferManager:
  private var currentBuf:  ScreenBuffer = ScreenBuffer.of(width, height)
  private var previousBuf: ScreenBuffer = ScreenBuffer.of(width, height)

  def current:  ScreenBuffer = currentBuf
  def previous: ScreenBuffer = previousBuf

  def swap(): Unit =
    val outgoingRegion = currentBuf.scrollRegion
    val tmp = previousBuf
    previousBuf = currentBuf
    currentBuf  = tmp
    outgoingRegion match
      case Some(r) => currentBuf.setScrollRegion(r)
      case None    => currentBuf.clearScrollRegion()
    previousBuf.clearPendingScrollLines()
    // Preserve cells inside the active region: the mirror just populated them
    // with the post-scroll state, and per-frame `appendLineInRegion` shifts
    // that history up to accumulate. Cells outside the region get wiped so
    // the panel's per-frame draw starts fresh for static UI.
    currentBuf.clearOutsideRegion()

  def diff(): Seq[RenderOp] =
    val builder = Seq.newBuilder[RenderOp]

    if currentBuf.scrollRegion != previousBuf.scrollRegion then
      currentBuf.scrollRegion match
        case Some(r) => builder += RenderOp.SetScrollRegion(r)
        case None    => builder += RenderOp.ResetScrollRegion

    builder ++= currentBuf.pendingScrollLines

    builder ++= currentBuf.diff(previousBuf)

    builder.result()
