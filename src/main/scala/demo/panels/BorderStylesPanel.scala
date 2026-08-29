package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.Insets
import layout.Constraint

/**
 * Showcase panel for the styleguide's border and padding primitives.
 *
 * Three tiles side by side, each exercising a different [[BoxStyle]] and
 * a different [[Insets]] padding. The composition
 * `area.inner(border.inset).inner(padding)` is the same for all three —
 * only the frame cost and the padding differ. The [[BoxStyle.Borderless]]
 * tile writes no border glyphs at its corners; its child sits inside
 * padding alone.
 */
object BorderStylesPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  private val singleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val doubleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightMagenta))

  private val borderlessStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  /** One tile: a Panel with the given border, padding, and a two-line legend. */
  private def tile(
    border: BoxStyle,
    borderName: String,
    borderStyle: CellStyle,
    padding: Insets,
    paddingLabel: String
  ): Component =
    Panel(
      border = border,
      style = borderStyle,
      padding = padding,
      child = VBox(
        Text(borderName, labelStyle, Alignment.Center),
        Spacer,
        Text(paddingLabel, DemoUtils.DimStyle, Alignment.Center),
        Spacer,
        Text("Child inside", bodyStyle, Alignment.Center),
        Text("border + padding", bodyStyle, Alignment.Center)
      )
    )

  /** The component tree. */
  val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style = DemoUtils.HeaderStyle,
      child = Text("Border Styles & Padding", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Text(
      "Composition: area.inner(border.inset).inner(padding) — one rule, three frames.",
      DemoUtils.DimStyle
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill -> HBox(
      tile(
        border = BoxStyle.Single,
        borderName = "Single",
        borderStyle = singleStyle,
        padding = Insets.zero,
        paddingLabel = "padding = zero"
      ),
      tile(
        border = BoxStyle.Double,
        borderName = "Double",
        borderStyle = doubleStyle,
        padding = Insets.all(2),
        paddingLabel = "padding = Insets.all(2)"
      ),
      tile(
        border = BoxStyle.Borderless,
        borderName = "Borderless",
        borderStyle = borderlessStyle,
        padding = Insets.symmetric(horizontal = 2, vertical = 1),
        paddingLabel = "padding = symmetric(2,1)"
      )
    )
  )

  /** Layer 7 panel — fills whatever area the host grants it. */
  val panel: AppPanel = AppPanel.of(tree)
