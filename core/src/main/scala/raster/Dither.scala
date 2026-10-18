package io.github.wickedsik.wsconsole
package raster

/**
 * How [[Braille]] turns each dot's mean ink into lit or unlit.
 *
 * [[None]] cuts at a threshold, which suits line art: a stroke is either
 * there or not. The other modes spread tone across neighbouring dots, so a
 * painting or photo keeps its gradients instead of collapsing into solid
 * blobs. Under them the ink is the tone and the threshold plays no part.
 *
 * Every mode is deterministic: the same ink grid always lights the same dots.
 */
enum Dither:

  /** A dot lights when its mean ink reaches the threshold. */
  case None

  /**
   * A 4×4 Bayer matrix tiled over the dot grid. Sixteen tone levels in a
   * regular texture; a dot depends only on its own ink, so re-fitting the
   * image never makes the pattern crawl.
   */
  case Ordered

  /**
   * Floyd–Steinberg error diffusion, scanning rows in alternating direction.
   * Carries all of each dot's rounding error to its neighbours: the finest
   * tonal detail, with an irregular grain.
   */
  case FloydSteinberg

  /**
   * Atkinson error diffusion, scanning rows in alternating direction.
   * Carries only three quarters of the error, so near-black and near-white
   * areas stay clean: higher contrast than [[FloydSteinberg]], at the cost of
   * shadow and highlight detail. Tones near the ends clip; a flat field of
   * ink 1/8 lights no dot and one of 7/8 lights every dot.
   */
  case Atkinson

  /**
   * The lowest pixel ink that counts as picture under this mode, given
   * `threshold`; [[RasterizedBitmap.trim]] crops the margin below it.
   *
   * Under [[None]] and [[Ordered]] a dot depends only on its own ink, so the
   * cropped margin could not have drawn. Error diffusion can light a dot from
   * any positive ink once enough error accumulates, so under those modes only
   * an inkless pixel is margin. Error spilled from the picture can still
   * light a dot just inside an inkless margin; trimming drops those strays.
   */
  def minInk(threshold: Double): Double = this match
    case None                      => threshold
    case Ordered                   => Dither.BayerMinInk
    case FloydSteinberg | Atkinson => Double.MinPositiveValue

  /**
   * Row-major lit flags for a `dotsWide × dotsHigh` grid of mean `ink`,
   * which is left untouched. `threshold` applies only under [[None]].
   */
  private[raster] def quantise(ink: Array[Double], dotsWide: Int, dotsHigh: Int, threshold: Double): Array[Boolean] =
    this match
      case None    => ink.map(_ >= threshold)
      case Ordered => Array.tabulate(ink.length)(k => ink(k) >= Dither.bayerCut(k % dotsWide, k / dotsWide))
      case FloydSteinberg => Dither.diffuse(ink, dotsWide, dotsHigh, Dither.FloydSteinbergKernel)
      case Atkinson       => Dither.diffuse(ink, dotsWide, dotsHigh, Dither.AtkinsonKernel)

object Dither:

  // The classic 4×4 Bayer index matrix, Bayer(y)(x), levels 0–15.
  private val Bayer: IArray[IArray[Int]] = IArray(
    IArray(0, 8, 2, 10),
    IArray(12, 4, 14, 6),
    IArray(3, 11, 1, 9),
    IArray(15, 7, 13, 5)
  )

  // The lowest of the Bayer cut-offs: (0 + 0.5) / 16.
  private val BayerMinInk: Double = 1.0 / 32

  /** The ink at which the dot at (`x`, `y`) lights under [[Ordered]]: centred on its level. */
  private def bayerCut(x: Int, y: Int): Double = (Bayer(y % 4)(x % 4) + 0.5) / 16

  /** Where one dot's rounding error goes: `(dx, dy, share)` for a left-to-right scan. */
  final private case class Spread(dx: Int, dy: Int, share: Double)

  private val FloydSteinbergKernel: IArray[Spread] = IArray(
    Spread(1, 0, 7.0 / 16),
    Spread(-1, 1, 3.0 / 16),
    Spread(0, 1, 5.0 / 16),
    Spread(1, 1, 1.0 / 16)
  )

  // Six eighths of the error; the remaining quarter is dropped on purpose.
  private val AtkinsonKernel: IArray[Spread] = IArray(
    Spread(1, 0, 1.0 / 8),
    Spread(2, 0, 1.0 / 8),
    Spread(-1, 1, 1.0 / 8),
    Spread(0, 1, 1.0 / 8),
    Spread(1, 1, 1.0 / 8),
    Spread(0, 2, 1.0 / 8)
  )

  /**
   * Error diffusion over `ink`, which is left untouched. Rows scan in
   * alternating direction (serpentine), with `kernel` mirrored on the
   * right-to-left rows, so the error does not drift in one direction.
   * A dot lights at half ink; error pushed past the grid's edge is lost.
   */
  private def diffuse(ink: Array[Double], dotsWide: Int, dotsHigh: Int, kernel: IArray[Spread]): Array[Boolean] =
    val tone = ink.clone()
    val lit = new Array[Boolean](tone.length)
    for y <- 0 until dotsHigh do
      val leftToRight = y % 2 == 0
      val dir = if leftToRight then 1 else -1
      var step = 0
      while step < dotsWide do
        val x = if leftToRight then step else dotsWide - 1 - step
        val k = y * dotsWide + x
        val on = tone(k) >= 0.5
        lit(k) = on
        val error = tone(k) - (if on then 1.0 else 0.0)
        for s <- kernel do
          val nx = x + s.dx * dir
          val ny = y + s.dy
          if nx >= 0 && nx < dotsWide && ny < dotsHigh then tone(ny * dotsWide + nx) += error * s.share
        step += 1
    lit
