package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Frame}
import component.{Alignment, Component, Panel, Spacer, Text, VBox}
import demo.DemoUtils
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Final panel: summary of what was showcased and preview of future phases.
 *
 * Migrated to the Layer 4 component model: a `Panel` wraps a `VBox` of
 * heading rows + bulleted item rows, all centered.
 */
object FarewellPanel:

  private val borderStyle    = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val titleStyle     = CellStyle(fg = Foreground.Named(FgColor.BrightGreen),  attributes = Set(Attribute.Bold))
  private val highlightStyle = CellStyle(fg = Foreground.Named(FgColor.White))
  private val futureStyle    = CellStyle(fg = Foreground.Named(FgColor.BrightYellow))
  private val exitStyle      = CellStyle(fg = Foreground.Named(FgColor.BrightBlack),  attributes = Set(Attribute.Dim))

  private val showcased = List(
    "16-color, 256-color, and true-color RGB",
    "Text styles: bold, dim, italic, underline, strikethrough",
    "Cursor positioning across the cell grid",
    "Scroll regions with fixed status bar",
    "Braille spinner and block progress bar",
    "Constraint-based layout: Fixed, Percentage, Fill, Bounded",
    "Component model: HBox / VBox / Panel / Text"
  )

  private val future = List(
    "Event system — keyboard and mouse routing",
    "Differential rendering pipeline",
    "Application framework — lifecycle and state"
  )

  private def textRow(text: String, style: CellStyle): Component =
    Text(text, style, Alignment.Center)

  private val tree: Panel =
    val rows = scala.collection.mutable.ArrayBuffer.empty[Component]
    rows += Spacer
    rows += textRow("Demo Complete", titleStyle)
    rows += Spacer
    rows += textRow("Showcased in this demo:", highlightStyle)
    showcased.foreach(item => rows += textRow(item, DemoUtils.DimStyle))
    rows += Spacer
    rows += textRow("Future phases will add:", futureStyle)
    future.foreach(item => rows += textRow(item, DemoUtils.DimStyle))
    rows += Spacer
    rows += textRow("Exiting in 3 seconds...", exitStyle)
    rows += Spacer

    Panel(
      border = BoxStyle.Double,
      style  = borderStyle,
      child  = VBox(rows.toSeq*)
    )

  // 2 borders + showcased.size + future.size + 7 fixed rows (title, headings, spacers, exit)
  private val height = 2 + showcased.size + future.size + 7 + 2

  def show: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      tree.render(Rect(2, 1, 78, height), canvas)
    }
