package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.{Insets, Sides}
import layout.Constraint

/**
 * Showcase panel for the styleguide's border primitives — glyph choice
 * ([[BoxStyle]]) and per-edge visibility ([[Sides]]) as orthogonal axes.
 *
 * Row 1 exercises the two full-frame glyph sets (Single, Double) plus
 * `Sides.none` as the "no border, no title" state. Row 2 demonstrates
 * corner suppression: an edge that is `false` in [[Sides]] costs zero
 * cells and its adjacent corners are only written when the *other*
 * adjacent edge is still visible. The tile whose top edge is
 * suppressed also drops its title — the title rides the top edge.
 */
object BorderStylesPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val singleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val doubleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightMagenta))

  private val borderlessStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  private val partialStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow))

  /** One tile: a Panel with the given border, sides, padding, and a legend line. */
  private def tile(
    border: BoxStyle,
    sides: Sides,
    borderName: String,
    borderStyle: CellStyle,
    padding: Insets,
    detail: String
  ): Component =
    Panel(
      border = border,
      sides = sides,
      title = Some(s" $borderName "),
      style = borderStyle,
      padding = padding,
      child = VBox(
        Spacer,
        Text(detail, DemoUtils.DimStyle, Alignment.Center),
        Spacer,
        Text("Child inside", bodyStyle, Alignment.Center),
        Text("sides + padding", bodyStyle, Alignment.Center)
      )
    )

  /** The component tree. */
  val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style = DemoUtils.HeaderStyle,
      child = Text("Border Styles: 8-glyph frame × 4 edge flags", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Text(
      "Composition: area.inner(sides.toInsets).inner(padding). Corners appear only where both adjacent edges are visible.",
      DemoUtils.DimStyle
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill -> VBox(
      Constraint.Fill -> HBox(
        tile(BoxStyle.Single, Sides.all, "Single", singleStyle, Insets.zero, "sides = all"),
        tile(BoxStyle.Double, Sides.all, "Double", doubleStyle, Insets.all(2), "sides = all, pad = all(2)"),
        tile(
          BoxStyle.Single,
          Sides.none,
          "None",
          borderlessStyle,
          Insets.symmetric(2, 1),
          "sides = none — title dropped"
        )
      ),
      Constraint.Fixed(1) -> Spacer,
      Constraint.Fill -> HBox(
        tile(
          BoxStyle.Single,
          Sides(top = true, right = false, bottom = false, left = false),
          "Top",
          partialStyle,
          Insets.zero,
          "top only"
        ),
        tile(
          BoxStyle.Single,
          Sides.symmetric(horizontal = true, vertical = false),
          "Verticals",
          partialStyle,
          Insets.zero,
          "sides.symmetric(h=T, v=F)"
        ),
        tile(
          BoxStyle.Single,
          Sides(top = true, right = true, bottom = false, left = true),
          "3-Sided",
          partialStyle,
          Insets.zero,
          "no bottom edge"
        )
      )
    )
  )

  /** Layer 7 panel — fills whatever area the host grants it. */
  val panel: AppPanel = AppPanel.of(tree)
