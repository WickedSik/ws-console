package io.github.wickedsik.wsconsole
package buffer

import geometry.Rect

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
  def width:  Int
  def height: Int

  /** Place a single character at (x, y). */
  def putChar(x: Int, y: Int, char: Char, style: CellStyle = CellStyle.Empty): Unit

  /** Place each character of `text` starting at (x, y), advancing rightward. */
  def putText(x: Int, y: Int, text: String, style: CellStyle = CellStyle.Empty): Unit

  /**
   * Draw a rectangular border of the given style. If `title` is given, it
   * appears on the top edge starting at column x+2, truncated if it would
   * exceed the box width.
   */
  def drawBox(
    rect:     Rect,
    boxStyle: BoxStyle,
    title:    Option[String] = None,
    style:    CellStyle      = CellStyle.Empty
  ): Unit

  /** Fill a rectangular region with `cell`. */
  def fillRect(rect: Rect, cell: Cell): Unit

  /** Create a sub-canvas constrained to `rect`. Coordinates are relative to the sub-canvas. */
  def subCanvas(rect: Rect): Canvas

  /**
   * Declare a hardware scroll region on the underlying buffer and return a
   * leaf-only [[ScrollableCanvas]] handle for appending lines.
   *
   * Row coordinates are canvas-local (0-indexed, inclusive). Scroll regions
   * are always full-width; column extent of this canvas is ignored — see
   * decision 6 in the task scroll for the rationale.
   *
   * Throws [[IllegalArgumentException]] if `top < 0`, `bottom >= height`, or
   * `bottom < top`.
   */
  def scrollRegion(top: Int, bottom: Int): ScrollableCanvas

object Canvas:
  /** Create a Canvas that draws into the entire `buffer`. */
  def apply(buffer: ScreenBuffer): Canvas =
    BufferCanvas(buffer, 0, 0, buffer.width, buffer.height)

private final class BufferCanvas(
  buffer:        ScreenBuffer,
  offsetX:       Int,
  offsetY:       Int,
  val width:     Int,
  val height:    Int
) extends Canvas:

  private inline def writeCell(x: Int, y: Int, cell: Cell): Unit =
    if x >= 0 && x < width && y >= 0 && y < height then
      buffer.set(offsetX + x, offsetY + y, cell)

  def putChar(x: Int, y: Int, char: Char, style: CellStyle): Unit =
    writeCell(x, y, Cell(char, style))

  def putText(x: Int, y: Int, text: String, style: CellStyle): Unit =
    var i = 0
    val n = text.length
    while i < n do
      writeCell(x + i, y, Cell(text.charAt(i), style))
      i += 1

  def drawBox(rect: Rect, boxStyle: BoxStyle, title: Option[String], style: CellStyle): Unit =
    if rect.isEmpty || rect.width < 2 || rect.height < 2 then return

    val xStart = rect.x
    val yStart = rect.y
    val xEnd   = rect.x + rect.width  - 1
    val yEnd   = rect.y + rect.height - 1

    writeCell(xStart, yStart, Cell(boxStyle.topLeft,     style))
    writeCell(xEnd,   yStart, Cell(boxStyle.topRight,    style))
    writeCell(xStart, yEnd,   Cell(boxStyle.bottomLeft,  style))
    writeCell(xEnd,   yEnd,   Cell(boxStyle.bottomRight, style))

    var x = xStart + 1
    while x < xEnd do
      writeCell(x, yStart, Cell(boxStyle.horizontal, style))
      writeCell(x, yEnd,   Cell(boxStyle.horizontal, style))
      x += 1

    var y = yStart + 1
    while y < yEnd do
      writeCell(xStart, y, Cell(boxStyle.vertical, style))
      writeCell(xEnd,   y, Cell(boxStyle.vertical, style))
      y += 1

    title.foreach { t =>
      val maxTitleLen = math.max(0, rect.width - 4)
      val truncated   = if t.length > maxTitleLen then t.take(maxTitleLen) else t
      val titleStart  = xStart + 2
      var i = 0
      while i < truncated.length do
        writeCell(titleStart + i, yStart, Cell(truncated.charAt(i), style))
        i += 1
    }

  def fillRect(rect: Rect, cell: Cell): Unit =
    if rect.isEmpty then return
    val xStart = math.max(0, rect.x)
    val xEnd   = math.min(width, rect.x + rect.width)
    val yStart = math.max(0, rect.y)
    val yEnd   = math.min(height, rect.y + rect.height)
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
    val clippedW = math.max(0, math.min(rect.width,  width  - clippedX))
    val clippedH = math.max(0, math.min(rect.height, height - clippedY))
    BufferCanvas(buffer, offsetX + clippedX, offsetY + clippedY, clippedW, clippedH)

  def scrollRegion(top: Int, bottom: Int): ScrollableCanvas =
    require(top    >= 0,      s"top must be >= 0, got $top")
    require(bottom <  height, s"bottom must be < canvas height ($height), got $bottom")
    require(bottom >= top,    s"bottom must be >= top, got top=$top bottom=$bottom")
    val region = ScrollRegion(offsetY + top, offsetY + bottom)
    buffer.setScrollRegion(region)
    BufferScrollableCanvas.of(buffer, region)
