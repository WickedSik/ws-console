package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{BoxStyle, Canvas, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.Rect
import glyph.Widths
import layout.Constraint
import raster.{Dither, ImageLoader, Polarity}

import zio.{IO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for [[component.BrailleImage]] on a photo: the same
 * picture under every [[raster.Dither]] mode, in one colour or its own.
 *
 * `smiley-xp.png` is a photo with three broad tones — a pale face, a blue
 * sky, green grass. Under `Dither.None` the threshold cut lights face and
 * sky alike and the picture collapses into blobs; the dithering modes keep
 * the three apart. `Coloring.Source` then paints each cell in its region's
 * colour. Tab to a button and press Enter or Space: one cycles the dither
 * mode, the other toggles the colouring.
 *
 * `BrailleImage` is immutable, so the panel builds one image per pair of
 * dither mode and colouring and draws whichever is selected.
 */
final class BrailleImagePanel private (
  val panel: AppPanel,
  val ditherButton: Button,
  val coloringButton: Button,
  ditherRef: AtomicReference[Dither],
  coloringRef: AtomicReference[Coloring]
):

  /** The dither mode on screen. */
  def dither: Dither = ditherRef.get

  /** The colouring on screen. */
  def coloring: Coloring = coloringRef.get

object BrailleImagePanel:

  /** The demo's bundled photo, on the demo classpath. */
  val Resource: String = "smiley-xp.png"

  private val buttonStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val statusStyle = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  /** What each mode does, for the description line. */
  private def describe(dither: Dither): String = dither match
    case Dither.None           => "threshold cut: tones above it merge into solid blobs"
    case Dither.Ordered        => "4×4 Bayer matrix: sixteen levels in a regular texture"
    case Dither.FloydSteinberg => "error diffusion: finest tone, irregular grain"
    case Dither.Atkinson       => "error diffusion, ¾ of the error: crisp edges, extremes clip"

  /** Draws the image for the selected pair; sized like it. */
  final private class Selected(
    images: Map[(Dither, Coloring), BrailleImage],
    ditherRef: AtomicReference[Dither],
    coloringRef: AtomicReference[Coloring]
  ) extends Component:
    private def current: BrailleImage = images((ditherRef.get, coloringRef.get))
    override def preferredHeight(width: Int): Option[Int] = current.preferredHeight(width)
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = current.render(area, canvas, ctx)

  /** One line of text, read on every render and drawn on the area's middle row. */
  final private class Live(line: () => String, style: CellStyle) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      canvas.putText(area.x, area.y + area.height / 2, Widths.truncate(line(), area.width), style)

  private def next(dither: Dither): Dither = Dither.fromOrdinal((dither.ordinal + 1) % Dither.values.length)

  private def toggle(coloring: Coloring): Coloring = coloring match
    case Coloring.Uniform => Coloring.Source
    case Coloring.Source  => Coloring.Uniform

  /** Load the photo and construct the panel, showing `Dither.None` in `Coloring.Uniform` first. */
  def make: IO[IOException, BrailleImagePanel] =
    for
      photo <- ImageLoader.loadResource(Resource)
      pairs = for d <- Dither.values.toSeq; c <- Coloring.values.toSeq yield (d, c)
      images <- ZIO.foreach(pairs)((d, c) =>
                  BrailleImage.make(photo, polarity = Polarity.BrightInk, dither = d, coloring = c).map((d, c) -> _)
                )
      ditherRef   <- ZIO.succeed(new AtomicReference(Dither.None))
      coloringRef <- ZIO.succeed(new AtomicReference(Coloring.Uniform))
      cycle       <- Button.make("Cycle dither", ZIO.succeed(ditherRef.updateAndGet(next)).unit, style = buttonStyle)
      colour      <- Button.make("Toggle colour", ZIO.succeed(coloringRef.updateAndGet(toggle)).unit, style = buttonStyle)
    yield
      val status = Live(() => s" Dither.${ditherRef.get} · Coloring.${coloringRef.get}", statusStyle)
      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Braille Image — a photo in dots", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text("Tab to a button, Enter or Space to press it", DemoUtils.DimStyle),
        Constraint.Fixed(3) -> HBox(
          Constraint.Fixed(16) -> cycle,
          Constraint.Fixed(17) -> colour,
          Constraint.Fill      -> status
        ),
        Constraint.Fixed(1) -> Live(() => describe(ditherRef.get), DemoUtils.DimStyle),
        Constraint.Fill     -> Selected(images.toMap, ditherRef, coloringRef)
      )
      new BrailleImagePanel(AppPanel.of(tree), cycle, colour, ditherRef, coloringRef)
