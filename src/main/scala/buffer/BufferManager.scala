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
 *      pending scroll-line ops exist), or from `current.diffAll` when
 *      `invalidatePrevious()` has armed a full re-emit for this frame
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
   *
   * Cell-coverage invariant: after `swap()` returns, every cell in
   * `current` outside any active scroll region is `Cell.Empty`. This
   * is the load-bearing guarantee for the [[component.Component.render]]
   * contract — components may leave cells unwritten because the
   * framework owns blank cells, and the diff against `previous` will
   * correctly emit erasures for cells that transition from styled to
   * empty between frames.
   */
  def swap(): Unit

  /** Compute the full op stream for this frame. Pure read of buffer state. */
  def diff(): Seq[RenderOp]

  /**
   * Arm a full re-emit: the next `diff()` emits a Cell op for every
   * position of `current` — including positions where `current` is
   * `Cell.Empty` — so the terminal receives a complete frame snapshot
   * regardless of whether the prior frame matched cell-for-cell. The
   * arming is consumed by the next `swap()`.
   *
   * Implemented as a flag rather than by filling `previous` with a
   * sentinel cell. `swap()` recycles the `previous` buffer into
   * `current`, and `clearOutsideRegion` deliberately preserves rows
   * inside an active scroll region — so sentinel cells written into
   * `previous` survive into `current` and are subsequently diffed and
   * flushed to the terminal as literal glyphs. The flag expresses the
   * same intent with no buffer to launder.
   *
   * Does NOT touch the terminal — pair with a normal redraw to push
   * the resulting full-frame bytes downstream.
   *
   * Use after a layout-context change (panel swap, container reflow)
   * when the terminal display may have drifted from the buffer model
   * and the diff's "unchanged cells stayed on screen" assumption can
   * no longer be trusted. Distinct from `Frame.clearScreen`, which
   * additionally emits an explicit screen-clear ANSI and would produce
   * a visible flicker.
   */
  def invalidatePrevious(): Unit

object BufferManager:
  def of(width: Int, height: Int): BufferManager = MutableBufferManager(width, height)

private final class MutableBufferManager(width: Int, height: Int) extends BufferManager:
  private var currentBuf:  ScreenBuffer = ScreenBuffer.of(width, height)
  private var previousBuf: ScreenBuffer = ScreenBuffer.of(width, height)

  // Armed by `invalidatePrevious`, read by `diff`, cleared by `swap` —
  // so the full re-emit covers exactly the frame it was requested for.
  private var forceFullDiff: Boolean = false

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
    forceFullDiff = false

  def diff(): Seq[RenderOp] =
    val builder = Seq.newBuilder[RenderOp]

    if currentBuf.scrollRegion != previousBuf.scrollRegion then
      currentBuf.scrollRegion match
        case Some(r) => builder += RenderOp.SetScrollRegion(r)
        case None    => builder += RenderOp.ResetScrollRegion

    builder ++= currentBuf.pendingScrollLines

    builder ++= (if forceFullDiff then currentBuf.diffAll else currentBuf.diff(previousBuf))

    builder.result()

  def invalidatePrevious(): Unit =
    forceFullDiff = true
