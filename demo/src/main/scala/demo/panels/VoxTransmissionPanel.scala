package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import layout.Constraint
import raster.{ImageLoader, Polarity}

import zio.IO

import java.io.IOException

/**
 * Showcase panel for [[component.BrailleImage]] — a monochrome "vox
 * transmission" with the Aquila drawn in Braille under the text.
 *
 * The image sits under `Constraint.Fill`: the layout decides its area and
 * the image takes the largest undistorted block that fits, centred.
 * Resizing the terminal re-fits it. `aquilla.png` is white on black, so it
 * draws with `Polarity.BrightInk`, and `trim` crops its black margin so the
 * eagle itself is centred.
 */
object VoxTransmissionPanel:

  /** The demo's bundled picture, on the demo classpath. */
  val Resource: String = "aquilla.png"

  private val vox = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))
  private val voxDim = CellStyle(fg = Foreground.Named(FgColor.Green), attributes = Set(Attribute.Dim))

  private val rule = Text("─" * 400, voxDim)

  /** Load the picture and construct the panel. */
  def make: IO[IOException, AppPanel] =
    for
      aquila <- ImageLoader.loadResource(Resource)
      image  <- BrailleImage.make(aquila, vox, Polarity.BrightInk, trim = true)
    yield AppPanel.of(tree(image))

  private def tree(image: BrailleImage): Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style = DemoUtils.HeaderStyle,
      child = Text("+++ Vox Transmission +++", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Text("Origin: Terra  ·  Relay: astropathic choir VII  ·  Thought for the day follows", vox),
    Constraint.Fixed(1) -> rule,
    Constraint.Fill     -> image,
    Constraint.Fixed(1) -> rule,
    Constraint.Fixed(1) -> Text(
      "BrailleImage fits the area the layout gives it: resize the terminal to watch it re-fit.",
      DemoUtils.DimStyle
    )
  )
