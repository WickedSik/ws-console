package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import layout.Constraint

import zio.UIO

/**
 * Showcase panel for [[component.Select]]: a short single choice, a
 * thirty-option list that scrolls inside its popup, and a select on the
 * bottom edge whose list opens upward.
 *
 * Each list is drawn by the Layer 6 popup pass (ADR-007), over the
 * fields below it. While a list is open the Select keeps focus and
 * consumes plain keys, so the demo's `p` / `n` / `q` shortcuts do not
 * fire behind it; `Tab` closes the list and moves on.
 */
object SelectDemoPanel:

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val selectStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val models: Seq[String] = Seq("Haiku", "Sonnet", "Opus")

  private val countries: Seq[String] = Seq(
    "Argentina",
    "Australia",
    "Belgium",
    "Brazil",
    "Canada",
    "Chile",
    "Côte d'Ivoire",
    "Denmark",
    "Egypt",
    "Finland",
    "France",
    "Germany",
    "Greece",
    "India",
    "Ireland",
    "Italy",
    "Japan (日本)",
    "Kenya",
    "Mexico",
    "Netherlands",
    "New Zealand",
    "Norway",
    "Poland",
    "Portugal",
    "Russia (Россия)",
    "South Africa",
    "Spain",
    "Sweden",
    "Ukraine (Україна)",
    "Uruguay"
  )

  private val themes: Seq[String] = Seq("System", "Light", "Dark", "High contrast")

  /** Label on the select's text row, the select beside it. */
  private def row(label: String, select: Select): Component =
    HBox(
      Constraint.Fixed(14) -> VBox(
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(label, labelStyle),
        Constraint.Fill     -> Spacer
      ),
      Constraint.Fixed(32) -> select,
      Constraint.Fill      -> Spacer
    )

  def make: UIO[AppPanel] =
    for
      model   <- Select.make(models, selected = Some(1), style = selectStyle)
      country <- Select.make(countries, placeholder = "Choose a country", style = selectStyle, maxVisible = 6)
      theme   <- Select.make(themes, selected = Some(0), style = selectStyle)
    yield AppPanel.of(
      VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Select — a dropdown list drawn over its surroundings", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Enter/Space/↓ open · ↑/↓ PgUp/PgDn Home/End move · Enter choose · Esc cancel · Tab next",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(3) -> row("Model", model),
        Constraint.Fixed(3) -> row("Country", country),
        Constraint.Fill     -> Spacer,
        Constraint.Fixed(1) -> Text("The theme list has no room below, so it opens upward.", DemoUtils.DimStyle),
        Constraint.Fixed(3) -> row("Theme", theme)
      )
    )
