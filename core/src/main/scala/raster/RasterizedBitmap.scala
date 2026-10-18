package io.github.wickedsik.wsconsole
package raster

/**
 * An immutable `width × height` grid of pixels, each reduced to a
 * luminance and an alpha in `[0, 1]`.
 *
 * Independent of any image file format: [[ImageLoader]] builds one from a
 * file, and a consumer that draws its own picture (a plot, a sparkline)
 * builds one with [[RasterizedBitmap.tabulate]]. Colour is not kept — the
 * [[Braille]] encoder needs only how much ink each pixel carries, which
 * [[Polarity]] derives from luminance and alpha.
 */
final class RasterizedBitmap private (val width: Int, val height: Int, luma: Array[Float], opacity: Array[Float]):

  /** Luminance of the pixel at (`x`, `y`): `0` is black, `1` is white. */
  def luminance(x: Int, y: Int): Float = luma(y * width + x)

  /** Alpha of the pixel at (`x`, `y`): `0` is transparent, `1` is opaque. */
  def alpha(x: Int, y: Int): Float = opacity(y * width + x)

  /** The `w × h` region whose top-left pixel is (`x`, `y`), copied. */
  def crop(x: Int, y: Int, w: Int, h: Int): RasterizedBitmap =
    require(
      x >= 0 && y >= 0 && w >= 1 && h >= 1 && x + w <= width && y + h <= height,
      s"crop $w×$h at ($x, $y) does not fit a $width×$height bitmap"
    )
    val l = new Array[Float](w * h)
    val o = new Array[Float](w * h)
    for row <- 0 until h do
      System.arraycopy(luma, (y + row) * width + x, l, row * w, w)
      System.arraycopy(opacity, (y + row) * width + x, o, row * w, w)
    new RasterizedBitmap(w, h, l, o)

  /**
   * This bitmap cropped to the bounding box of the pixels whose ink under
   * `polarity` reaches `threshold`, or this bitmap unchanged when no pixel
   * does.
   *
   * Removes only margin that cannot draw: a [[Braille]] dot lights when the
   * mean ink over its area reaches the threshold, and a dot outside the box
   * averages only pixels below it.
   */
  def trim(polarity: Polarity, threshold: Double): RasterizedBitmap =
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for y <- 0 until height; x <- 0 until width do
      if polarity.ink(luminance(x, y), alpha(x, y)) >= threshold then
        if x < minX then minX = x
        if x > maxX then maxX = x
        if y < minY then minY = y
        if y > maxY then maxY = y
    if maxX < 0 then this
    else if minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1 then this
    else crop(minX, minY, maxX - minX + 1, maxY - minY + 1)

object RasterizedBitmap:

  /** One pixel: luminance and alpha, each in `[0, 1]`. */
  final case class Pixel(luminance: Double, alpha: Double = 1.0)

  object Pixel:

    /**
     * A pixel from a packed `0xAARRGGBB` value, with luminance weighted
     * `0.299 R + 0.587 G + 0.114 B` (ITU-R BT.601).
     */
    def fromArgb(argb: Int): Pixel =
      val a = (argb >>> 24) & 0xFF
      val r = (argb >>> 16) & 0xFF
      val g = (argb >>> 8) & 0xFF
      val b = argb & 0xFF
      Pixel((0.299 * r + 0.587 * g + 0.114 * b) / 255.0, a / 255.0)

  /** A bitmap whose pixel at (`x`, `y`) is `pixel(x, y)`; values are clamped to `[0, 1]`. */
  def tabulate(width: Int, height: Int)(pixel: (Int, Int) => Pixel): RasterizedBitmap =
    require(width >= 1 && height >= 1, s"bitmap must be at least 1×1, got $width×$height")
    val luma = new Array[Float](width * height)
    val opacity = new Array[Float](width * height)
    for y <- 0 until height; x <- 0 until width do
      val p = pixel(x, y)
      luma(y * width + x) = clamp(p.luminance)
      opacity(y * width + x) = clamp(p.alpha)
    new RasterizedBitmap(width, height, luma, opacity)

  /** A bitmap from row-major packed `0xAARRGGBB` values, as `BufferedImage.getRGB` returns them. */
  def fromArgb(width: Int, height: Int, argb: Array[Int]): RasterizedBitmap =
    require(argb.length == width * height, s"expected ${width * height} pixels, got ${argb.length}")
    tabulate(width, height)((x, y) => Pixel.fromArgb(argb(y * width + x)))

  private def clamp(v: Double): Float = math.min(1.0, math.max(0.0, v)).toFloat
