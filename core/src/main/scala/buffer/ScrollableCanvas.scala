package io.github.wickedsik.wsconsole
package buffer

/**
 * A leaf-only handle that drives a hardware scroll region (DECSTBM).
 *
 * Returned from `Canvas.scrollRegion(top, bottom)`. Each `appendLine` call
 * shifts the region's rows up by one and writes the supplied line into the
 * bottom row; the actual hardware scroll happens at the terminal when the
 * resulting `RenderOp.ScrollRegionLine` is flushed. Cells outside the
 * region remain governed by the parent [[Canvas]].
 *
 * Leaf-only: `ScrollableCanvas` does NOT expose `subCanvas`. The surrounding
 * UI uses the parent Canvas; the scrollable handle is for `appendLine` and
 * the optional `clear` only. Nesting scroll regions is undefined and out of
 * scope (DECSTBM is screen-global; only one region can be active at a time).
 *
 * Scroll regions are always full-width — DECSLRM column margins are out of
 * scope. The line supplied to `appendLine` is padded with `Cell.Empty` or
 * truncated to match the buffer's width before submission.
 */
trait ScrollableCanvas:

  /**
   * Append `line` at the bottom of the region; existing rows shift up by
   * one. The line is padded or truncated to the buffer's full width before
   * submission, so the caller need not match width exactly.
   */
  def appendLine(line: Line): Unit

  /**
   * Tear down the active scroll region and fill its cells with [[Cell.Empty]].
   * Subsequent `appendLine` calls on this handle will fail (the region is
   * gone). Use this for clean teardown when the panel is finished.
   */
  def clear(): Unit

final private class BufferScrollableCanvas(
  buffer: ScreenBuffer,
  region: ScrollRegion
) extends ScrollableCanvas:

  def appendLine(line: Line): Unit =
    val normalized = normalizeWidth(line, buffer.width)
    buffer.appendLineInRegion(region, normalized)
    buffer.enqueueScrollLine(RenderOp.ScrollRegionLine(region, normalized))

  def clear(): Unit =
    var y = region.top
    while y <= region.bottom do
      var x = 0
      while x < buffer.width do
        buffer.set(x, y, Cell.Empty)
        x += 1
      y += 1
    buffer.clearScrollRegion()

  private def normalizeWidth(line: Line, target: Int): Line =
    if line.width == target then line
    else if line.width > target then Line(line.cells.take(target))
    else Line(line.cells ++ Seq.fill(target - line.width)(Cell.Empty))

private[buffer] object BufferScrollableCanvas:
  def of(buffer: ScreenBuffer, region: ScrollRegion): ScrollableCanvas =
    BufferScrollableCanvas(buffer, region)
