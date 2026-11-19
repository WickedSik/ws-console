package io.github.wickedsik.wsconsole
package raster

/**
 * Bitmap to 8-dot Braille, one terminal cell per 2×4 block of dots.
 *
 * The target size in cells is the input: a `cols × rows` block holds a
 * `(2 · cols) × (4 · rows)` dot grid, and the whole bitmap is
 * area-averaged onto it, so no edge pixel is cropped. Each dot's mean
 * [[Polarity.ink]] over its share of the bitmap is then quantised to lit or
 * unlit by a [[Dither]] mode: a plain threshold cut, or a dither that keeps
 * tone.
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
  ): (cols: Int, rows: Int) =
    if width <= 0 || height <= 0 then (0, 0)
    else
      val rows = rowsFor(rasterizedBitmap, width, cellAspect)
      if rows <= height then (width, rows)
      else
        val cols =
          math.min(width, math.floor(height * rasterizedBitmap.width * cellAspect / rasterizedBitmap.height).toInt)
        if cols < 1 then (0, 0) else (cols, math.min(height, rowsFor(rasterizedBitmap, cols, cellAspect)))

  /**
   * Encode `bitmap` into exactly `rows` strings of exactly `cols` glyphs,
   * cutting each dot at `threshold` ([[Dither.None]]). Empty for
   * `cols <= 0` or `rows <= 0`.
   */
  def encode(
    rasterizedBitmap: RasterizedBitmap,
    cols: Int,
    rows: Int,
    polarity: Polarity,
    threshold: Double
  ): IndexedSeq[String] =
    encode(rasterizedBitmap, cols, rows, polarity, threshold, Dither.None)

  /**
   * Encode `bitmap` into exactly `rows` strings of exactly `cols` glyphs,
   * each dot lit or unlit by `dither`. Empty for `cols <= 0` or `rows <= 0`.
   *
   * `threshold` applies only under [[Dither.None]]; the dithering modes
   * take each dot's ink as its tone.
   */
  def encode(
    rasterizedBitmap: RasterizedBitmap,
    cols: Int,
    rows: Int,
    polarity: Polarity,
    threshold: Double,
    dither: Dither
  ): IndexedSeq[String] =
    if cols <= 0 || rows <= 0 then IndexedSeq.empty
    else
      val dotsWide = cols * DotsWide
      val dotsHigh = rows * DotsHigh
      val lit = dither.quantise(ink(rasterizedBitmap, dotsWide, dotsHigh, polarity), dotsWide, dotsHigh, threshold)
      (0 until rows).map { r =>
        val line = StringBuilder(cols)
        for c <- 0 until cols do
          var mask = 0
          for dy <- 0 until DotsHigh; dx <- 0 until DotsWide do
            if lit((r * DotsHigh + dy) * dotsWide + c * DotsWide + dx) then mask |= bit(dx, dy)
          line += glyph(mask)
        line.result()
      }

  /**
   * The colour of each cell of a `cols × rows` block over `bitmap`, packed
   * `0xRRGGBB`: exactly `rows` rows of exactly `cols` colours. Empty for
   * `cols <= 0` or `rows <= 0`.
   *
   * Each pixel counts by its coverage of the cell times its ink under
   * `polarity`, so the colour is that of what the lit dots draw rather than
   * of the background between them: a cell half pink hair, half black sky
   * is pink under `BrightInk`, not a dark rose. A cell with no ink at all
   * takes the plain coverage mean. Channels are averaged as stored (sRGB).
   */
  def colors(rasterizedBitmap: RasterizedBitmap, cols: Int, rows: Int, polarity: Polarity): IndexedSeq[IndexedSeq[Int]] =
    if cols <= 0 || rows <= 0 then IndexedSeq.empty
    else
      val xs = spans(rasterizedBitmap.width, cols)
      val ys = spans(rasterizedBitmap.height, rows)
      (0 until rows).map { r =>
        (0 until cols).map { c =>
          val sx = xs(c)
          val sy = ys(r)
          // Sums of coverage-and-ink weights, then of coverage alone, as (weight, red, green, blue).
          val inked = Array.ofDim[Double](4)
          val plain = Array.ofDim[Double](4)
          for yi <- sy.weights.indices; xi <- sx.weights.indices do
            val x = sx.start + xi
            val y = sy.start + yi
            val cover = sx.weights(xi) * sy.weights(yi)
            val ink = cover * polarity.ink(rasterizedBitmap.luminance(x, y), rasterizedBitmap.alpha(x, y))
            val rgb = rasterizedBitmap.rgb(x, y)
            accumulate(inked, ink, rgb)
            accumulate(plain, cover, rgb)
          pack(if inked(0) > 0.0 then inked else plain)
        }
      }

  private def accumulate(sums: Array[Double], weight: Double, rgb: Int): Unit =
    sums(0) += weight
    sums(1) += weight * ((rgb >>> 16) & 0xFF)
    sums(2) += weight * ((rgb >>> 8) & 0xFF)
    sums(3) += weight * (rgb & 0xFF)

  private def pack(sums: Array[Double]): Int =
    def channel(i: Int): Int = math.min(255, math.round(sums(i) / sums(0)).toInt)
    (channel(1) << 16) | (channel(2) << 8) | channel(3)

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

  /** Row-major mean ink for a `dotsWide × dotsHigh` dot grid. */
  private def ink(
    rasterizedBitmap: RasterizedBitmap,
    dotsWide: Int,
    dotsHigh: Int,
    polarity: Polarity
  ): Array[Double] =
    val xs = spans(rasterizedBitmap.width, dotsWide)
    val ys = spans(rasterizedBitmap.height, dotsHigh)
    val mean = Array.ofDim[Double](dotsWide * dotsHigh)
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
      mean(j * dotsWide + i) = sum / (sx.total * sy.total)
    mean
