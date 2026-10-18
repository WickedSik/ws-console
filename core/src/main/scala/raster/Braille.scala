package io.github.wickedsik.wsconsole
package raster

/**
 * Bitmap to 8-dot Braille, one terminal cell per 2×4 block of dots.
 *
 * The target size in cells is the input: a `cols × rows` block holds a
 * `(2 · cols) × (4 · rows)` dot grid, and the whole bitmap is
 * area-averaged onto it, so no edge pixel is cropped. A dot is lit when
 * the mean [[Polarity.ink]] over its share of the bitmap reaches the
 * threshold.
 *
 * Dot-to-bit map, as Unicode's Braille Patterns block defines it:
 *
 * {{{
 *   (0,0) 0x01   (1,0) 0x08
 *   (0,1) 0x02   (1,1) 0x10
 *   (0,2) 0x04   (1,2) 0x20
 *   (0,3) 0x40   (1,3) 0x80
 * }}}
 *
 * A cell with no lit dot is a space, not U+2800: a space diffs cheaper
 * against a blank buffer, and some fonts draw U+2800 visibly.
 *
 * Everything here is pure and deterministic: the same bitmap and options
 * always produce the same rows.
 */
object Braille:

  /** Dots across one cell. */
  val DotsWide: Int = 2

  /** Dots down one cell. */
  val DotsHigh: Int = 4

  /** Default cell aspect, `cellHeight / cellWidth`; at 2.0 each dot is square. */
  val DefaultCellAspect: Double = 2.0

  private val Base = 0x2800

  // Bits(dy)(dx), per the table above.
  private val Bits: IArray[IArray[Int]] = IArray(
    IArray(0x01, 0x08),
    IArray(0x02, 0x10),
    IArray(0x04, 0x20),
    IArray(0x40, 0x80)
  )

  /** The bit for the dot at column `dx` (0–1) and row `dy` (0–3) of a cell. */
  def bit(dx: Int, dy: Int): Int = Bits(dy)(dx)

  /** The glyph for a dot `mask` (0–255): U+2800 + mask, or a space for an empty mask. */
  def glyph(mask: Int): Char =
    require(mask >= 0 && mask <= 0xFF, s"mask must be in 0..255, got $mask")
    if mask == 0 then ' ' else (Base + mask).toChar

  /**
   * Rows that show `bitmap` undistorted at `cols` columns:
   * `ceil(cols · height / (width · cellAspect))`. Zero for `cols <= 0`.
   */
  def rowsFor(rasterizedBitmap: RasterizedBitmap, cols: Int, cellAspect: Double = DefaultCellAspect): Int =
    if cols <= 0 then 0
    else math.ceil(cols.toDouble * rasterizedBitmap.height / (rasterizedBitmap.width * cellAspect)).toInt

  /**
   * The largest undistorted `(cols, rows)` that fits `bitmap` inside a
   * `width × height` cell area, or `(0, 0)` when nothing fits.
   *
   * Width-bound first: the full width with [[rowsFor]] rows. If that is
   * too tall, the full height bounds it instead, and the columns shrink
   * to keep the aspect.
   */
  def fit(
    rasterizedBitmap: RasterizedBitmap,
    width: Int,
    height: Int,
    cellAspect: Double = DefaultCellAspect
  ): (Int, Int) =
    if width <= 0 || height <= 0 then (0, 0)
    else
      val rows = rowsFor(rasterizedBitmap, width, cellAspect)
      if rows <= height then (width, rows)
      else
        val cols =
          math.min(width, math.floor(height * rasterizedBitmap.width * cellAspect / rasterizedBitmap.height).toInt)
        if cols < 1 then (0, 0) else (cols, math.min(height, rowsFor(rasterizedBitmap, cols, cellAspect)))

  /**
   * Encode `bitmap` into exactly `rows` strings of exactly `cols` glyphs.
   * Empty for `cols <= 0` or `rows <= 0`.
   */
  def encode(
    rasterizedBitmap: RasterizedBitmap,
    cols: Int,
    rows: Int,
    polarity: Polarity,
    threshold: Double
  ): IndexedSeq[String] =
    if cols <= 0 || rows <= 0 then IndexedSeq.empty
    else
      val lit = dots(rasterizedBitmap, cols * DotsWide, rows * DotsHigh, polarity, threshold)
      val dotsWide = cols * DotsWide
      (0 until rows).map { r =>
        val line = new StringBuilder(cols)
        for c <- 0 until cols do
          var mask = 0
          for dy <- 0 until DotsHigh; dx <- 0 until DotsWide do
            if lit((r * DotsHigh + dy) * dotsWide + c * DotsWide + dx) then mask |= bit(dx, dy)
          line += glyph(mask)
        line.result()
      }

  /** The source pixels one destination dot covers along an axis, with their coverage weights. */
  final private class Span(val start: Int, val weights: Array[Double]):
    val total: Double = weights.sum

  /**
   * Destination cell `d` of `dst` covers source range `[d · src / dst, (d + 1) · src / dst)`.
   * Each source pixel overlapping it contributes the length of the overlap.
   */
  private def spans(src: Int, dst: Int): Array[Span] =
    Array.tabulate(dst) { d =>
      val lo = d.toDouble * src / dst
      val hi = (d + 1).toDouble * src / dst
      val first = math.floor(lo).toInt
      val last = math.min(src, math.ceil(hi).toInt)
      val weights = Array.tabulate(last - first) { i =>
        val x = first + i
        math.min(hi, x + 1.0) - math.max(lo, x.toDouble)
      }
      Span(first, weights)
    }

  /** Row-major lit flags for a `dotsWide × dotsHigh` dot grid. */
  private def dots(
    rasterizedBitmap: RasterizedBitmap,
    dotsWide: Int,
    dotsHigh: Int,
    polarity: Polarity,
    threshold: Double
  ): Array[Boolean] =
    val xs = spans(rasterizedBitmap.width, dotsWide)
    val ys = spans(rasterizedBitmap.height, dotsHigh)
    val lit = new Array[Boolean](dotsWide * dotsHigh)
    for j <- 0 until dotsHigh; i <- 0 until dotsWide do
      val sx = xs(i)
      val sy = ys(j)
      var sum = 0.0
      var yi = 0
      while yi < sy.weights.length do
        val y = sy.start + yi
        var row = 0.0
        var xi = 0
        while xi < sx.weights.length do
          val x = sx.start + xi
          row += sx.weights(xi) * polarity.ink(rasterizedBitmap.luminance(x, y), rasterizedBitmap.alpha(x, y))
          xi += 1
        sum += sy.weights(yi) * row
        yi += 1
      lit(j * dotsWide + i) = sum / (sx.total * sy.total) >= threshold
    lit
