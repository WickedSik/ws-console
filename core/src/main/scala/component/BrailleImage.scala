package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle}
import geometry.Rect
import raster.{RasterizedBitmap, Braille, Polarity}

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * A picture drawn in Braille, one foreground colour, fitted to whatever
 * area the layout assigns.
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
 * Encoding reads every pixel of the bitmap, so the result for the last
 * fitted size is kept: a frame at the same size redraws the same rows
 * without re-encoding, and a resize encodes once.
 *
 * With `trim`, a margin that cannot draw (blank under `polarity` and
 * `threshold`, see [[raster.RasterizedBitmap.trim]]) is cropped once at construction,
 * so the picture itself is fitted and centred rather than the picture plus
 * its border.
 *
 * Not focusable; it never handles events.
 *
 * @param rasterizedBitmap     the picture as given, before any trim
 * @param polarity   which pixels draw, dark or bright
 * @param threshold  mean ink in `[0, 1]` at which a dot lights; lower keeps thin strokes
 * @param cellAspect terminal cell height divided by width
 * @param trim       crop the blank margin before fitting
 */
final class BrailleImage private (
  val rasterizedBitmap: RasterizedBitmap,
  val style: CellStyle,
  val polarity: Polarity,
  val threshold: Double,
  val cellAspect: Double,
  val trim: Boolean
) extends Component:

  /** The bitmap that is fitted and drawn: `bitmap`, trimmed if asked. */
  private val drawn: RasterizedBitmap = if trim then rasterizedBitmap.trim(polarity, threshold) else rasterizedBitmap

  private val memo = new AtomicReference[Option[BrailleImage.Encoded]](None)

  override def preferredHeight(width: Int): Option[Int] = Some(Braille.rowsFor(drawn, width, cellAspect))

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val (cols, rows) = Braille.fit(drawn, area.width, area.height, cellAspect)
    if cols < 1 || rows < 1 then return
    val x = area.x + (area.width - cols) / 2
    val y = area.y + (area.height - rows) / 2
    encoded(cols, rows).zipWithIndex.foreach((line, i) => canvas.putText(x, y + i, line, style))

  /** The rows for a `cols × rows` block, encoded once per size. */
  private[component] def encoded(cols: Int, rows: Int): IndexedSeq[String] =
    memo.get match
      case Some(e) if e.cols == cols && e.rows == rows => e.lines
      case _ =>
        val lines = Braille.encode(drawn, cols, rows, polarity, threshold)
        memo.set(Some(BrailleImage.Encoded(cols, rows, lines)))
        lines

object BrailleImage:

  /** Default dot threshold; low enough that thin strokes survive a strong downscale. */
  val DefaultThreshold: Double = 0.3

  final private case class Encoded(cols: Int, rows: Int, lines: IndexedSeq[String])

  /** Construct an image of `bitmap` drawn in `style`. */
  def make(
    rasterizedBitmap: RasterizedBitmap,
    style: CellStyle = CellStyle.Empty,
    polarity: Polarity = Polarity.DarkInk,
    threshold: Double = DefaultThreshold,
    cellAspect: Double = Braille.DefaultCellAspect,
    trim: Boolean = false
  ): UIO[BrailleImage] =
    require(threshold >= 0.0 && threshold <= 1.0, s"threshold must be in [0, 1], got $threshold")
    require(cellAspect > 0.0, s"cellAspect must be positive, got $cellAspect")
    ZIO.succeed(new BrailleImage(rasterizedBitmap, style, polarity, threshold, cellAspect, trim))
