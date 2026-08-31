package io.github.wickedsik.wsconsole
package buffer

import geometry.{Rect, Sides}

/**
 * High-level drawing primitives over a [[ScreenBuffer]].
 *
 * A Canvas writes into a region of a buffer. Sub-canvases produced via
 * [[subCanvas]] are clipped to a sub-rectangle: writes that fall outside
 * the sub-canvas's bounds are silently discarded, providing component
 * isolation without requiring callers to perform their own bounds checks.
 *
 * Drawing methods are direct mutation (no ZIO wrapping) — callers wrap a
 * drawing block in `ZIO.succeed` when composing with effects.
 */
trait Canvas:
  def width: Int
  def height: Int

  /** Place a single character at (x, y). */
  def putChar(x: Int, y: Int, char: Char, style: CellStyle = CellStyle.Empty): Unit

  /** Place each character of `text` starting at (x, y), advancing rightward. */
  def putText(x: Int, y: Int, text: String, style: CellStyle = CellStyle.Empty): Unit

  /**
   * Draw a rectangular border on the 9-grid described by `boxStyle`,
   * restricted to the edges enabled in `sides`. A corner glyph is written
   * only when both of its adjacent edges are visible; a suppressed corner
   * cell is filled by whichever adjacent edge still runs through it, or
   * left untouched when both are absent. If `title` is given and
   * `sides.top` is true, it appears on the top edge starting at column
   * `x + 2`, truncated to fit `width - 4`.
   */
  def drawBox(
    rect: Rect,
    boxStyle: BoxStyle,
    sides: Sides = Sides.all,
    title: Option[String] = None,
    style: CellStyle = CellStyle.Empty
  ): Unit

  /** Fill a rectangular region with `cell`. */
  def fillRect(rect: Rect, cell: Cell): Unit

  /** Create a sub-canvas constrained to `rect`. Coordinates are relative to the sub-canvas. */
  def subCanvas(rect: Rect): Canvas

  /**
   * Declare a hardware scroll region on the underlying buffer and
   * return a leaf-only [[ScrollableCanvas]] for appending lines.
   *
   * Row coordinates are canvas-local (0-indexed, inclusive). Scroll
   * regions are always full-width; this canvas's column extent is ignored.
   *
   * Throws [[IllegalArgumentException]] if `top < 0`, `bottom >= height`,
   * or `bottom < top`.
   */
  def scrollRegion(top: Int, bottom: Int): ScrollableCanvas

object Canvas:
  /** Create a Canvas that draws into the entire `buffer`. */
  def apply(buffer: ScreenBuffer): Canvas =
    BufferCanvas(buffer, 0, 0, buffer.width, buffer.height)

final private class BufferCanvas(
  buffer: ScreenBuffer,
  offsetX: Int,
  offsetY: Int,
  val width: Int,
  val height: Int
) extends Canvas:

  private inline def writeCell(x: Int, y: Int, cell: Cell): Unit =
    if x >= 0 && x < width && y >= 0 && y < height then
      buffer.set(offsetX + x, offsetY + y, cell)

  def putChar(x: Int, y: Int, char: Char, style: CellStyle): Unit =
    writeCell(x, y, Cell(char, style))

  def putText(x: Int, y: Int, text: String, style: CellStyle): Unit =
    // Iterate grapheme clusters (not `Char`s) so a surrogate pair, a base +
    // variation-selector sequence, or a ZWJ sequence lands in a SINGLE cell —
    // otherwise the flusher emits half-codepoints and terminals show `??`.
    // A wide grapheme (CJK, most emoji) additionally reserves the next cell
    // as a continuation marker (empty text) so the flusher does not emit
    // anything at that column and the terminal cursor advances match up.
    // See buffer/Graphemes.scala and buffer/Widths.scala.
    var col = 0
    Graphemes.foreach(text) { g =>
      writeCell(x + col, y, Cell(g, style))
      if Widths.cellsFor(g) == 2 then
        writeCell(x + col + 1, y, Cell("", style))
        col += 2
      else
        col += 1
    }

  def drawBox(
    rect: Rect,
    boxStyle: BoxStyle,
    sides: Sides,
    title: Option[String],
    style: CellStyle
  ): Unit =
    if sides.isEmpty || rect.isEmpty then return
    val insets = sides.toInsets
    if rect.width < insets.left + insets.right then return
    if rect.height < insets.top + insets.bottom then return

    val xStart = rect.x
    val yStart = rect.y
    val xEnd = rect.x + rect.width - 1
    val yEnd = rect.y + rect.height - 1

    if sides.top then
      var x = xStart
      while x <= xEnd do
        val glyph =
          if x == xStart && sides.left then boxStyle.topLeft
          else if x == xEnd && sides.right then boxStyle.topRight
          else boxStyle.topCenter
        writeCell(x, yStart, Cell(glyph, style))
        x += 1

    if sides.bottom && (yEnd != yStart || !sides.top) then
      var x = xStart
      while x <= xEnd do
        val glyph =
          if x == xStart && sides.left then boxStyle.bottomLeft
          else if x == xEnd && sides.right then boxStyle.bottomRight
          else boxStyle.bottomCenter
        writeCell(x, yEnd, Cell(glyph, style))
        x += 1

    if sides.left then
      val yFirst = if sides.top then yStart + 1 else yStart
      val yLast = if sides.bottom then yEnd - 1 else yEnd
      var y = yFirst
      while y <= yLast do
        writeCell(xStart, y, Cell(boxStyle.midLeft, style))
        y += 1

    if sides.right && (xEnd != xStart || !sides.left) then
      val yFirst = if sides.top then yStart + 1 else yStart
      val yLast = if sides.bottom then yEnd - 1 else yEnd
      var y = yFirst
      while y <= yLast do
        writeCell(xEnd, y, Cell(boxStyle.midRight, style))
        y += 1

    if sides.top then
      title.foreach { t =>
        val maxTitleLen = math.max(0, rect.width - 4)
        val truncated = if t.length > maxTitleLen then t.take(maxTitleLen) else t
        val titleStart = xStart + 2
        var col = 0
        Graphemes.foreach(truncated) { g =>
          writeCell(titleStart + col, yStart, Cell(g, style))
          if Widths.cellsFor(g) == 2 then
            writeCell(titleStart + col + 1, yStart, Cell("", style))
            col += 2
          else
            col += 1
        }
      }

  def fillRect(rect: Rect, cell: Cell): Unit =
    if rect.isEmpty then return
    val xStart = math.max(0, rect.x)
    val xEnd = math.min(width, rect.x + rect.width)
    val yStart = math.max(0, rect.y)
    val yEnd = math.min(height, rect.y + rect.height)
    var y = yStart
    while y < yEnd do
      var x = xStart
      while x < xEnd do
        writeCell(x, y, cell)
        x += 1
      y += 1

  def subCanvas(rect: Rect): Canvas =
    val clippedX = math.max(0, rect.x)
    val clippedY = math.max(0, rect.y)
    val clippedW = math.max(0, math.min(rect.width, width - clippedX))
    val clippedH = math.max(0, math.min(rect.height, height - clippedY))
    BufferCanvas(buffer, offsetX + clippedX, offsetY + clippedY, clippedW, clippedH)

  def scrollRegion(top: Int, bottom: Int): ScrollableCanvas =
    require(top >= 0, s"top must be >= 0, got $top")
    require(bottom < height, s"bottom must be < canvas height ($height), got $bottom")
    require(bottom >= top, s"bottom must be >= top, got top=$top bottom=$bottom")
    val region = ScrollRegion(offsetY + top, offsetY + bottom)
    buffer.setScrollRegion(region)
    BufferScrollableCanvas.of(buffer, region)
