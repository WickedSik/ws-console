package io.github.wickedsik.wsconsole
package buffer

import geometry.{Rect, Sides}
import glyph.{Graphemes, Widths}

/**
 * High-level drawing primitives over a [[ScreenBuffer]].
 *
 * A Canvas carries two independent pieces of geometry:
 *
 *   - an '''origin''' — where canvas coordinate `(0, 0)` lands in the
 *     buffer. [[subCanvas]] moves it; [[clip]] does not.
 *   - a '''clip''' — the canvas cells that accept writes. Every derived
 *     canvas intersects its clip with its parent's, so a clip only ever
 *     shrinks. Writes outside it are silently discarded, providing
 *     component isolation without requiring callers to perform their own
 *     bounds checks.
 *
 * Keeping the two apart is what makes a viewport possible: a component
 * scrolled partly above its container draws at negative coordinates and
 * loses exactly the rows outside the clip, rather than being shifted
 * down into view.
 *
 * Drawing methods are direct mutation (no ZIO wrapping) — callers wrap a
 * drawing block in `ZIO.succeed` when composing with effects.
 */
trait Canvas:
  def width: Int
  def height: Int

  /** Place a single character at (x, y). */
  def putChar(x: Int, y: Int, char: Char, style: CellStyle = CellStyle.Empty): Unit

  /**
   * Place each grapheme of `text` starting at (x, y), advancing rightward
   * by its column width. A wide grapheme whose two cells are not both
   * inside the clip is dropped whole — half a glyph cannot be drawn, and
   * emitting the lead cell alone would spill it across the clip edge.
   */
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

  /**
   * Create a sub-canvas whose origin is `rect`'s top-left corner.
   * Coordinates on the result are relative to that corner.
   *
   * `width` / `height` of the result are `rect`'s — the logical extent the
   * caller asked for — even where `rect` extends past this canvas or has a
   * negative origin. Writes are clipped to the intersection of `rect` with
   * this canvas's clip, so a sub-canvas hanging off any edge loses the
   * overhanging cells and keeps the rest in place.
   */
  def subCanvas(rect: Rect): Canvas

  /**
   * Narrow the writable region to `rect` without moving the origin.
   * Coordinates on the result are those of this canvas; writes outside
   * `rect` (or outside this canvas's own clip) are discarded.
   *
   * This is the viewport primitive: a container renders children in its
   * own coordinate space through a clipped canvas, and anything they draw
   * past the viewport edges disappears.
   */
  def clip(rect: Rect): Canvas

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
    BufferCanvas(buffer, 0, 0, buffer.width, buffer.height, Rect(0, 0, buffer.width, buffer.height))

/**
 * `originX` / `originY` translate canvas coordinates to buffer
 * coordinates. `clipRect` is in canvas coordinates and always lies
 * inside the buffer, so every accepted write lands in bounds.
 */
final private class BufferCanvas(
  buffer: ScreenBuffer,
  originX: Int,
  originY: Int,
  val width: Int,
  val height: Int,
  clipRect: Rect
) extends Canvas:

  private val clipLeft = clipRect.x
  private val clipTop = clipRect.y
  private val clipRight = clipRect.x + math.max(0, clipRect.width)
  private val clipBottom = clipRect.y + math.max(0, clipRect.height)

  private inline def inClip(x: Int, y: Int): Boolean =
    x >= clipLeft && x < clipRight && y >= clipTop && y < clipBottom

  private inline def writeCell(x: Int, y: Int, cell: Cell): Unit =
    if inClip(x, y) then buffer.set(originX + x, originY + y, cell)

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
      if Widths.cellsFor(g) == 2 then
        if inClip(x + col, y) && inClip(x + col + 1, y) then
          writeCell(x + col, y, Cell(g, style))
          writeCell(x + col + 1, y, Cell("", style))
        col += 2
      else
        writeCell(x + col, y, Cell(g, style))
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

        putText(titleStart, yStart, truncated, style)
      }

  def fillRect(rect: Rect, cell: Cell): Unit =
    if rect.isEmpty then return
    val xStart = math.max(clipLeft, rect.x)
    val xEnd = math.min(clipRight, rect.x + rect.width)
    val yStart = math.max(clipTop, rect.y)
    val yEnd = math.min(clipBottom, rect.y + rect.height)
    var y = yStart
    while y < yEnd do
      var x = xStart
      while x < xEnd do
        buffer.set(originX + x, originY + y, cell)
        x += 1
      y += 1

  def subCanvas(rect: Rect): Canvas =
    val w = math.max(0, rect.width)
    val h = math.max(0, rect.height)
    // The parent clip re-expressed in the child's coordinates, narrowed to the child's extent.
    val childClip = intersect(
      Rect(clipLeft - rect.x, clipTop - rect.y, clipRight - clipLeft, clipBottom - clipTop),
      Rect(0, 0, w, h)
    )
    BufferCanvas(buffer, originX + rect.x, originY + rect.y, w, h, childClip)

  def clip(rect: Rect): Canvas =
    val narrowed = intersect(Rect(clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop), rect)
    BufferCanvas(buffer, originX, originY, width, height, narrowed)

  def scrollRegion(top: Int, bottom: Int): ScrollableCanvas =
    require(top >= 0, s"top must be >= 0, got $top")
    require(bottom < height, s"bottom must be < canvas height ($height), got $bottom")
    require(bottom >= top, s"bottom must be >= top, got top=$top bottom=$bottom")
    val region = ScrollRegion(originY + top, originY + bottom)
    buffer.setScrollRegion(region)
    BufferScrollableCanvas.of(buffer, region)

  /** Intersection of two rects; disjoint inputs yield a zero-size rect. */
  private def intersect(a: Rect, b: Rect): Rect =
    val left = math.max(a.x, b.x)
    val top = math.max(a.y, b.y)
    val right = math.min(a.x + math.max(0, a.width), b.x + math.max(0, b.width))
    val bottom = math.min(a.y + math.max(0, a.height), b.y + math.max(0, b.height))
    Rect(left, top, math.max(0, right - left), math.max(0, bottom - top))
