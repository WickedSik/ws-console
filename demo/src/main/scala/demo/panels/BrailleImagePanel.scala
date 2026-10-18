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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Showcase panel for [[component.BrailleImage]] on a photo: the same
 * picture under every [[raster.Dither]] mode.
 *
 * `smiley-xp.png` is a photo with three broad tones — a pale face, a blue
 * sky, green grass. Under `Dither.None` the threshold cut lights face and
 * sky alike and the picture collapses into blobs; the dithering modes keep
 * the three apart. Tab to the button and press Enter or Space to cycle the
 * mode; the status line names the one on screen.
 *
 * `BrailleImage` is immutable, so the panel builds one image per mode and
 * draws whichever is selected.
 */
final class BrailleImagePanel private (
  val panel: AppPanel,
  val ditherButton: Button,
  selected: AtomicInteger
):

  /** The dither mode on screen. */
  def dither: Dither = Dither.fromOrdinal(selected.get)

object BrailleImagePanel:

  /** The demo's bundled photo, on the demo classpath. */
  val Resource: String = "smiley-xp.png"

  private val buttonStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val statusStyle = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  /** What each mode does, for the status line. */
  private def describe(dither: Dither): String = dither match
    case Dither.None           => "threshold cut: tones above it merge into solid blobs"
    case Dither.Ordered        => "4×4 Bayer matrix: sixteen levels in a regular texture"
    case Dither.FloydSteinberg => "error diffusion: finest tone, irregular grain"
    case Dither.Atkinson       => "error diffusion, ¾ of the error: crisp edges, extremes clip"

  /** Draws the image for the selected mode; sized like it. */
  final private class Selected(images: IndexedSeq[BrailleImage], selected: AtomicInteger) extends Component:
    override def preferredHeight(width: Int): Option[Int] = images(selected.get).preferredHeight(width)
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      images(selected.get).render(area, canvas, ctx)

  /** Names the selected mode on every render, level with the button's label. */
  final private class StatusLine(selected: AtomicInteger) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val dither = Dither.fromOrdinal(selected.get)
      val line = Widths.truncate(s" Dither.$dither · ${describe(dither)}", area.width)
      canvas.putText(area.x, area.y + area.height / 2, line, statusStyle)

  /** Load the photo and construct the panel, showing `Dither.None` first. */
  def make: IO[IOException, BrailleImagePanel] =
    for
      photo    <- ImageLoader.loadResource(Resource)
      images   <- ZIO.foreach(Dither.values.toIndexedSeq)(d => BrailleImage.make(photo, polarity = Polarity.BrightInk, dither = d))
      selected <- ZIO.succeed(new AtomicInteger(Dither.None.ordinal))
      cycle    <- Button.make(
                    "Cycle dither",
                    ZIO.succeed(selected.updateAndGet(i => (i + 1) % images.size)).unit,
                    style = buttonStyle
                  )
    yield
      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Braille Image — a photo in dots", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text("Tab to the button, Enter or Space to cycle the dither mode", DemoUtils.DimStyle),
        Constraint.Fixed(3) -> HBox(Constraint.Fixed(16) -> cycle, Constraint.Fill -> new StatusLine(selected)),
        Constraint.Fill     -> new Selected(images, selected)
      )
      new BrailleImagePanel(AppPanel.of(tree), cycle, selected)
