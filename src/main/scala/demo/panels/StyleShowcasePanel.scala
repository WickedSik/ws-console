package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoLayout
import demo.DemoUtils
import geometry.Rect
import layout.Constraint

/**
 * Demonstrates all available text styles and style combinations.
 *
 * Migrated to the Layer 4 component model: each row of styled text is a
 * `Text` component, indented inside an HBox with a leading `Spacer`.
 */
object StyleShowcasePanel:

  private val individualStyles: Seq[(String, Set[Attribute])] = Seq(
    "Bold text"          -> Set(Attribute.Bold),
    "Dim text"           -> Set(Attribute.Dim),
    "Italic text"        -> Set(Attribute.Italic),
    "Underline text"     -> Set(Attribute.Underline),
    "Strikethrough text" -> Set(Attribute.Strikethrough),
    "Reverse video"      -> Set(Attribute.Reverse),
    "Blink text"         -> Set(Attribute.Blink)
  )

  private val combinations: Seq[(String, CellStyle)] = Seq(
    "Bold + Italic"
      -> CellStyle(attributes = Set(Attribute.Bold, Attribute.Italic)),
    "Bold + Underline + Cyan"
      -> CellStyle(fg = Foreground.Named(FgColor.Cyan), attributes = Set(Attribute.Bold, Attribute.Underline)),
    "Dim + Italic + Magenta"
      -> CellStyle(fg = Foreground.Named(FgColor.Magenta), attributes = Set(Attribute.Dim, Attribute.Italic)),
    "Bold + Yellow + Underline + Strikethrough"
      -> CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold, Attribute.Underline, Attribute.Strikethrough)),
    "Italic + Underline + Green"
      -> CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Italic, Attribute.Underline))
  )

  /** Indent helper — a row indented two cells from the left. */
  private def indented(child: Component): Component =
    HBox(
      Constraint.Fixed(2) -> Spacer,
      Constraint.Fill     -> child
    )

  private def styleRow(label: String, attrs: Set[Attribute]): Component =
    if label == "Blink text" then
      indented(HBox(
        Constraint.Fixed(label.length) -> Text(label, CellStyle(attributes = attrs)),
        Constraint.Fill                 -> Text("  (terminal support varies)", DemoUtils.DimStyle)
      ))
    else
      indented(Text(label, CellStyle(attributes = attrs)))

  private def comboRow(label: String, style: CellStyle): Component =
    indented(Text(label, style))

  private val individualSection: Component =
    val rows = scala.collection.mutable.ArrayBuffer.empty[(Constraint, Component)]
    rows += Constraint.Fixed(1) -> Text("Individual Styles", DemoUtils.SectionLabelStyle)
    individualStyles.foreach { (label, attrs) =>
      rows += Constraint.Fixed(1) -> styleRow(label, attrs)
    }
    VBox(rows.toSeq*)

  private val combinationsSection: Component =
    val rows = scala.collection.mutable.ArrayBuffer.empty[(Constraint, Component)]
    rows += Constraint.Fixed(1) -> Text("Style Combinations", DemoUtils.SectionLabelStyle)
    combinations.foreach { (label, style) =>
      rows += Constraint.Fixed(1) -> comboRow(label, style)
    }
    VBox(rows.toSeq*)

  /** Component tree — public so demo orchestrators can mount it directly. */
  val tree: Component = VBox(
    Constraint.Fixed(3)  -> Panel(
      border = BoxStyle.Double,
      style  = DemoUtils.HeaderStyle,
      child  = Text("Style Showcase", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1)  -> Spacer,
    Constraint.Fixed(1 + individualStyles.size) -> individualSection,
    Constraint.Fixed(1)  -> Spacer,
    Constraint.Fixed(1 + combinations.size)     -> combinationsSection,
    Constraint.Fill      -> Spacer
  )

  /** Layer 7 panel — demo content-region bounds (Q2 ratification), default lifecycle. */
  val panel: AppPanel = AppPanel.of(tree, DemoLayout.contentBounds)
