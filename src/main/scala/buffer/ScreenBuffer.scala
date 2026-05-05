package io.github.wickedsik.wsconsole
package buffer

import geometry.Rect

import scala.collection.mutable

/**
 * A 2D grid of [[Cell]]s representing screen state.
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
  def width:  Int
  def height: Int

  /** Read the cell at (x, y). Returns `None` for out-of-bounds coordinates. */
  def get(x: Int, y: Int): Option[Cell]

  /** Write `cell` at (x, y). Out-of-bounds writes are silently discarded. */
  def set(x: Int, y: Int, cell: Cell): Unit

  /** Fill the area inside `rect` with `cell`. Clipped to buffer bounds. */
  def fill(rect: Rect, cell: Cell): Unit

  /** Reset every cell to [[Cell.Empty]]. */
  def clear(): Unit

  /**
   * Compute the minimal sequence of updates that transforms `previous` into this buffer.
   *
   * Cells where `previous` matches this buffer are omitted. Cells that exist in
   * `previous` but not in this buffer (size mismatch) are not represented in the
   * result — resize handling is the caller's responsibility.
   */
  def diff(previous: ScreenBuffer): Seq[CellUpdate]

object ScreenBuffer:
  /** Construct an array-backed buffer of the given dimensions, filled with [[Cell.Empty]]. */
  def of(width: Int, height: Int): ScreenBuffer = ArrayScreenBuffer(width, height)

private final class ArrayScreenBuffer(val width: Int, val height: Int) extends ScreenBuffer:
  require(width  > 0, s"width must be positive, got $width")
  require(height > 0, s"height must be positive, got $height")

  private val cells: mutable.ArraySeq[Cell] = mutable.ArraySeq.fill(width * height)(Cell.Empty)

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
    val xEnd   = math.min(width, rect.x + rect.width)
    val yStart = math.max(0, rect.y)
    val yEnd   = math.min(height, rect.y + rect.height)
    var y = yStart
    while y < yEnd do
      var x = xStart
      while x < xEnd do
        cells(index(x, y)) = cell
        x += 1
      y += 1

  def clear(): Unit =
    var i = 0
    val n = cells.length
    while i < n do
      cells(i) = Cell.Empty
      i += 1

  def diff(previous: ScreenBuffer): Seq[CellUpdate] =
    val builder = Seq.newBuilder[CellUpdate]
    var y = 0
    while y < height do
      var x = 0
      while x < width do
        val current = cells(index(x, y))
        val before  = previous.get(x, y)
        if !before.contains(current) then
          builder += CellUpdate(x, y, current)
        x += 1
      y += 1
    builder.result()
