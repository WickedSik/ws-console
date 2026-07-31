package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.{Alignment, Component, Panel, Spacer, Text, VBox}
import demo.DemoLayout
import demo.DemoUtils
import geometry.Rect

/**
 * Final panel: summary of what was showcased and preview of future phases.
 *
 * Migrated to Layer 7: the existing Layer 4 component tree becomes the
 * `root` of an [[AppPanel]]. Default `onUnload` (region clear) absorbs
 * the manual cleanup pattern.
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
    "Component model: HBox / VBox / Panel / Text",
    "Event system + RenderLoop + Application framework"
  )

  private val future = List(
    "Migrate remaining demo panels onto Application + PanelHost",
    "Theming, animation, and routing primitives",
    "Library consumer widgets: REPL panes, log viewers, dashboards"
  )

  private def textRow(text: String, style: CellStyle): Component =
    Text(text, style, Alignment.Center)

  /** The component tree. */
  val tree: Panel =
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

  /** Bounds normalised to the demo's shared content region (Q2 ratification). */
  val bounds: Rect = DemoLayout.contentBounds

  /** Layer 7 panel — full default lifecycle. */
  val panel: AppPanel = AppPanel.of(tree, bounds)
