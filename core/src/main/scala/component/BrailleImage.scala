package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle}
import geometry.Rect
import raster.{Braille, Dither, Polarity, RasterizedBitmap}

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * A picture drawn in Braille, fitted to whatever area the layout assigns,
 * in one foreground colour or in the picture's own.
 *
 * The layout decides the size; the image never stretches. It takes the
 * largest undistorted block that fits its area (see [[raster.Braille.fit]])
 * and centres it. `preferredHeight` answers the undistorted height for a
 * width, so under `Constraint.Fit` the image fills the width exactly:
 *
 * {{{
 *   aquila <- ImageLoader.loadResource("aquila.png")
 *   image  <- BrailleImage.make(aquila, green, Polarity.BrightInk)
 *   tree    = VBox(
 *               Constraint.Fixed(1) -> Text("+++ Transmission +++"),
 *               Constraint.Fit      -> image
 *             )
 * }}}
 *
 * Encoding reads every pixel of the bitmap, so the glyphs and cell styles
 * for the last fitted size are kept: a frame at the same size redraws the same rows
 * without re-encoding, and a resize encodes once.
 *
 * `coloring` picks the colour of each cell: `Coloring.Uniform` draws every
 * glyph in `style`; `Coloring.Source` gives each cell the colour of its
 * region of the bitmap, in truecolor (see [[Coloring]]). A cell with no lit
 * dot is a space in `style` under either.
 *
 * `dither` picks how each dot's ink becomes lit or unlit: `Dither.None`
 * cuts at `threshold`, which suits line art; the dithering modes keep the
 * tones of a painting or photo (see [[raster.Dither]]).
 *
 * With `trim`, a margin that cannot draw (blank under `polarity` and the
 * lowest ink `dither` can light, see [[raster.Dither.minInk]] and
 * [[raster.RasterizedBitmap.trim]]) is cropped once at construction, so the
 * picture itself is fitted and centred rather than the picture plus its
 * border.
 *
 * Not focusable; it never handles events.
 *
 * @param rasterizedBitmap     the picture as given, before any trim
 * @param polarity   which pixels draw, dark or bright
 * @param threshold  mean ink in `[0, 1]` at which a dot lights under `Dither.None`; lower keeps thin strokes
 * @param dither     how a dot's ink becomes lit or unlit
 * @param coloring   one colour for every cell, or each cell's own
 * @param cellAspect terminal cell height divided by width
 * @param trim       crop the blank margin before fitting
 */
final class BrailleImage private (
  val rasterizedBitmap: RasterizedBitmap,
  val style: CellStyle,
  val polarity: Polarity,
  val threshold: Double,
  val dither: Dither,
  val coloring: Coloring,
  val cellAspect: Double,
  val trim: Boolean
) extends Component:

  /** The bitmap that is fitted and drawn: `bitmap`, trimmed if asked. */
  private val drawn: RasterizedBitmap = if trim then rasterizedBitmap.trim(polarity, dither.minInk(threshold)) else rasterizedBitmap

  private val memo = AtomicReference[Option[BrailleImage.Encoded]](None)

  override def preferredHeight(width: Int): Option[Int] = Some(Braille.rowsFor(drawn, width, cellAspect))

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val (cols, rows) = Braille.fit(drawn, area.width, area.height, cellAspect)
    if cols >= 1 && rows >= 1 then
      val x = area.x + (area.width - cols) / 2
      val y = area.y + (area.height - rows) / 2
      val e = encoded(cols, rows)
      for r <- 0 until rows; c <- 0 until cols do
        val glyph = e.lines(r)(c)
        canvas.putChar(x + c, y + r, glyph, if glyph == ' ' then style else e.styles(r)(c))

  /** The glyphs and cell styles for a `cols × rows` block, encoded once per size. */
  private[component] def encoded(cols: Int, rows: Int): BrailleImage.Encoded =
    memo.get.filter(e => e.cols == cols && e.rows == rows).getOrElse {
      val e = BrailleImage.Encoded(
        cols,
        rows,
        Braille.encode(drawn, cols, rows, polarity, threshold, dither),
        coloring.styles(drawn, cols, rows, polarity, style)
      )
      memo.set(Some(e))
      e
    }

object BrailleImage:

  /** Default dot threshold; low enough that thin strokes survive a strong downscale. */
  val DefaultThreshold: Double = 0.3

  final private[component] case class Encoded(
    cols: Int,
    rows: Int,
    lines: IndexedSeq[String],
    styles: IndexedSeq[IndexedSeq[CellStyle]]
  )

  /** Construct an image of `bitmap` drawn in `style`, or in its own colours under `Coloring.Source`. */
  def make(
    rasterizedBitmap: RasterizedBitmap,
    style: CellStyle = CellStyle.Empty,
    polarity: Polarity = Polarity.DarkInk,
    threshold: Double = DefaultThreshold,
    cellAspect: Double = Braille.DefaultCellAspect,
    trim: Boolean = false,
    dither: Dither = Dither.None,
    coloring: Coloring = Coloring.Uniform
  ): UIO[BrailleImage] =
    require(threshold >= 0.0 && threshold <= 1.0, s"threshold must be in [0, 1], got $threshold")
    require(cellAspect > 0.0, s"cellAspect must be positive, got $cellAspect")
    ZIO.succeed(BrailleImage(rasterizedBitmap, style, polarity, threshold, dither, coloring, cellAspect, trim))

  /**
   * The 0.2.0 shape of [[make]], with every argument given: `Dither.None`
   * and `Coloring.Uniform`. Kept so code compiled against 0.2.0 still links.
   */
  def make(
    rasterizedBitmap: RasterizedBitmap,
    style: CellStyle,
    polarity: Polarity,
    threshold: Double,
    cellAspect: Double,
    trim: Boolean
  ): UIO[BrailleImage] =
    make(rasterizedBitmap, style, polarity, threshold, cellAspect, trim, Dither.None, Coloring.Uniform)
