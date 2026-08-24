package io.github.wickedsik.wsconsole
package buffer

import geometry.Rect

import scala.collection.mutable

/**
 * A 2D grid of [[Cell]]s representing screen state, plus an optional
 * scroll-region declaration and a side-band queue of pending scroll-line ops.
 *
 * Coordinates are 0-indexed (top-left origin). Out-of-bounds access on
 * `get` returns `None`; out-of-bounds writes via `set`/`fill` are silently
 * discarded (clipping at the buffer edge).
 *
 * Implementations are mutable internally for performance — a 80×24 buffer
 * is 1920 cells, and per-frame mutation through immutable structures would
 * be wasteful. The trait-level API does not expose this mutation through
 * ZIO effects; callers wrap drawing blocks in `ZIO.succeed` when needed.
 */
trait ScreenBuffer:
  def width: Int
  def height: Int

  /** Read the cell at (x, y). Returns `None` for out-of-bounds coordinates. */
  def get(x: Int, y: Int): Option[Cell]

  /** Write `cell` at (x, y). Out-of-bounds writes are silently discarded. */
  def set(x: Int, y: Int, cell: Cell): Unit

  /** Fill the area inside `rect` with `cell`. Clipped to buffer bounds. */
  def fill(rect: Rect, cell: Cell): Unit

  /** The active scroll-region declaration, if any. */
  def scrollRegion: Option[ScrollRegion]

  /**
   * Declare an active scroll region. Throws [[IllegalArgumentException]] if
   * `region.bottom` is outside the buffer's row range. Clears any leftover
   * pending scroll-line ops from a previous region.
   */
  def setScrollRegion(region: ScrollRegion): Unit

  /** Tear down the active scroll region and clear any pending scroll-line ops. */
  def clearScrollRegion(): Unit

  /**
   * Pure cell-shift over the supplied region: rows `region.top..region.bottom-1`
   * take the values of rows `region.top+1..region.bottom`, and row
   * `region.bottom` is overwritten with `line`'s cells.
   *
   * Throws [[IllegalArgumentException]] if `region.bottom >= height` or if
   * `line.width != width`.
   *
   * The supplied region need not match the buffer's stored `scrollRegion`;
   * this is a pure cell-grid operation parameterised by row range. Used by
   * `ScrollableCanvas.appendLine` for the `current`-buffer write and by the
   * [[Frame]] to mirror the post-scroll terminal state on `previous`.
   */
  def appendLineInRegion(region: ScrollRegion, line: Line): Unit

  /**
   * The queue of pending [[RenderOp.ScrollRegionLine]] ops that
   * [[BufferManager.diff]] will emit on the next call. Drained by
   * `clearPendingScrollLines`.
   */
  def pendingScrollLines: Seq[RenderOp.ScrollRegionLine]

  /**
   * Enqueue a [[RenderOp.ScrollRegionLine]] for emission on the next diff.
   * Throws [[IllegalArgumentException]] if `op.region.bottom >= height` or
   * `op.line.width != width`.
   */
  def enqueueScrollLine(op: RenderOp.ScrollRegionLine): Unit

  /** Drop all queued scroll-line ops without emitting them. */
  def clearPendingScrollLines(): Unit

  /** Reset every cell to [[Cell.Empty]]. Preserves the scroll-region declaration AND any pending ops. */
  def clearCells(): Unit

  /**
   * Reset cells outside the active scroll region to [[Cell.Empty]]; preserves
   * cells inside the active region (so the region's accumulated scrolled
   * history survives `swap`). When no region is active, this behaves like
   * [[clearCells]].
   */
  def clearOutsideRegion(): Unit

  /** Reset all buffer state — cells, scroll-region declaration, pending ops. */
  def reset(): Unit

  /**
   * Compute the minimal sequence of ops that transforms `previous` into this buffer.
   *
   * Cells where `previous` matches this buffer are omitted. Cells that exist in
   * `previous` but not in this buffer (size mismatch) are not represented in the
   * result — resize handling is the caller's responsibility.
   *
   * If pending scroll-line ops exist, the cell-diff skips rows inside the
   * active scroll region — those rows are handled by the
   * [[RenderOp.ScrollRegionLine]] ops emitted at the [[BufferManager]] level.
   * Region transitions and the scroll-line ops themselves are NOT emitted here;
   * they are layered on by [[BufferManager.diff]].
   */
  def diff(previous: ScreenBuffer): Seq[RenderOp]

  /**
   * Emit a [[RenderOp.Cell]] for every position of this buffer, with no
   * baseline to compare against — including positions holding
   * [[Cell.Empty]]. The scroll-region skip rule of [[diff]] still
   * applies: rows inside the active region are omitted when pending
   * scroll-line ops exist, because those rows are carried by the
   * [[RenderOp.ScrollRegionLine]] ops instead.
   *
   * Backs [[BufferManager.invalidatePrevious]]'s re-emit-everything
   * contract without materialising a sentinel-filled buffer.
   */
  def diffAll: Seq[RenderOp]

object ScreenBuffer:
  /** Construct an array-backed buffer of the given dimensions, filled with [[Cell.Empty]]. */
  def of(width: Int, height: Int): ScreenBuffer = ArrayScreenBuffer(width, height)

final private class ArrayScreenBuffer(val width: Int, val height: Int) extends ScreenBuffer:
  require(width > 0, s"width must be positive, got $width")
  require(height > 0, s"height must be positive, got $height")

  private val cells: mutable.ArraySeq[Cell] = mutable.ArraySeq.fill(width * height)(Cell.Empty)

  private var region: Option[ScrollRegion] = None

  private val pending: mutable.ArrayBuffer[RenderOp.ScrollRegionLine] =
    mutable.ArrayBuffer.empty

  private inline def index(x: Int, y: Int): Int = y * width + x

  private inline def inBounds(x: Int, y: Int): Boolean =
    x >= 0 && x < width && y >= 0 && y < height

  def get(x: Int, y: Int): Option[Cell] =
    if inBounds(x, y) then Some(cells(index(x, y))) else None

  def set(x: Int, y: Int, cell: Cell): Unit =
    if inBounds(x, y) then cells(index(x, y)) = cell

  def fill(rect: Rect, cell: Cell): Unit =
    if rect.isEmpty then return
    val xStart = math.max(0, rect.x)
    val xEnd = math.min(width, rect.x + rect.width)
    val yStart = math.max(0, rect.y)
    val yEnd = math.min(height, rect.y + rect.height)
    var y = yStart
    while y < yEnd do
      var x = xStart
      while x < xEnd do
        cells(index(x, y)) = cell
        x += 1
      y += 1

  def scrollRegion: Option[ScrollRegion] = region

  def setScrollRegion(r: ScrollRegion): Unit =
    require(
      r.bottom < height,
      s"region bottom (${r.bottom}) must be < buffer height ($height)"
    )
    val changed = !region.contains(r)
    region = Some(r)
    if changed then pending.clear()

  def clearScrollRegion(): Unit =
    region = None
    pending.clear()

  def appendLineInRegion(r: ScrollRegion, line: Line): Unit =
    require(
      r.bottom < height,
      s"region bottom (${r.bottom}) must be < buffer height ($height)"
    )
    require(
      line.width == width,
      s"line width (${line.width}) must equal buffer width ($width)"
    )
    var y = r.top
    while y < r.bottom do
      var x = 0
      while x < width do
        cells(index(x, y)) = cells(index(x, y + 1))
        x += 1
      y += 1
    var x = 0
    while x < width do
      cells(index(x, r.bottom)) = line.cells(x)
      x += 1

  def pendingScrollLines: Seq[RenderOp.ScrollRegionLine] = pending.toSeq

  def enqueueScrollLine(op: RenderOp.ScrollRegionLine): Unit =
    require(
      op.region.bottom < height,
      s"region bottom (${op.region.bottom}) must be < buffer height ($height)"
    )
    require(
      op.line.width == width,
      s"line width (${op.line.width}) must equal buffer width ($width)"
    )
    pending += op

  def clearPendingScrollLines(): Unit = pending.clear()

  def clearCells(): Unit =
    var i = 0
    val n = cells.length
    while i < n do
      cells(i) = Cell.Empty
      i += 1

  def clearOutsideRegion(): Unit =
    region match
      case None => clearCells()
      case Some(r) =>
        var y = 0
        while y < height do
          if y < r.top || y > r.bottom then
            var x = 0
            while x < width do
              cells(index(x, y)) = Cell.Empty
              x += 1
          y += 1

  def reset(): Unit =
    clearCells()
    region = None
    pending.clear()

  def diff(previous: ScreenBuffer): Seq[RenderOp] = collectOps(Some(previous))

  def diffAll: Seq[RenderOp] = collectOps(None)

  /**
   * Shared walk behind [[diff]] and [[diffAll]]. A `None` baseline means
   * "nothing on screen can be trusted" — every visited cell is emitted.
   */
  private def collectOps(previous: Option[ScreenBuffer]): Seq[RenderOp] =
    val builder = Seq.newBuilder[RenderOp]
    val skipRegion = pending.nonEmpty
    val active = region
    var y = 0
    while y < height do
      val inRegion = skipRegion && active.exists(r => y >= r.top && y <= r.bottom)
      if !inRegion then
        var x = 0
        while x < width do
          val current = cells(index(x, y))
          val unchanged = previous.exists(_.get(x, y).contains(current))
          if !unchanged then
            builder += RenderOp.Cell(x, y, current)
          x += 1
      y += 1
    builder.result()
