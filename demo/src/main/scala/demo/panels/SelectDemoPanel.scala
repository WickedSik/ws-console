package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import layout.Constraint

import zio.{UIO, ZIO}

/**
 * Showcase panel for [[component.Select]] and [[component.TagInput]]: a
 * short single choice, a thirty-option combobox that filters as you type
 * and scrolls inside its popup, a tag field capped at six chips, and a
 * select on the bottom edge whose list opens upward.
 *
 * The "Tags remaining" line is the host's, not the tag field's: a
 * [[component.Label]] the tag field's `onChange` updates.
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

  private val tags: Seq[String] = Seq(
    "Fantasy",
    "Science fiction",
    "Romance",
    "Horror",
    "Mystery",
    "Comedy",
    "Drama",
    "Adventure",
    "Historical",
    "Slice of life",
    "Thriller",
    "Western"
  )

  /** One colour per tag, reversed so it fills the chip. */
  private val chipColors: Vector[FgColor] =
    Vector(FgColor.Cyan, FgColor.Magenta, FgColor.Yellow, FgColor.Green, FgColor.Blue, FgColor.Red)

  private def chipStyle(tag: Int): CellStyle =
    CellStyle(fg = Foreground.Named(chipColors(tag % chipColors.size)), attributes = Set(Attribute.Reverse))

  /** Most chips the tag field takes. */
  val MaxTags: Int = 6

  /** The tags chosen when the panel opens. */
  private val SeededTags: Seq[Int] = Seq(0, 3)

  /** Cells for the label column. */
  val LabelWidth: Int = 14

  /** Cells for each field. */
  val FieldWidth: Int = 66

  /** Label on the field's text row, the field beside it. */
  private def row(label: String, select: Component): Component =
    HBox(
      Constraint.Fixed(LabelWidth) -> VBox(
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(label, labelStyle),
        Constraint.Fill     -> Spacer
      ),
      Constraint.Fixed(FieldWidth) -> select,
      Constraint.Fill              -> Spacer
    )

  def make: UIO[AppPanel] =
    for
      model <- Select.make(models, selected = Some(1), style = selectStyle)
      country <- Select.make(
                   countries,
                   placeholder = "Type to filter",
                   style = selectStyle,
                   maxVisible = 6,
                   filterable = true
                 )
      theme    <- Select.make(themes, selected = Some(0), style = selectStyle)
      tagsLeft <- Label.make(s"Tags remaining: ${MaxTags - SeededTags.size}", DemoUtils.DimStyle)
      tagField <- TagInput.make(
                    tags,
                    onChange = chosen => ZIO.succeed(tagsLeft.set(s"Tags remaining: ${MaxTags - chosen.size}")),
                    selected = SeededTags,
                    placeholder = "Add tags",
                    style = selectStyle,
                    chipStyle = Some(chipStyle),
                    maxVisible = 5,
                    maxSelected = Some(MaxTags)
                  )
    yield AppPanel.of(
      VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Select — a dropdown list drawn over its surroundings", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "↓ open · ↑↓ PgUp PgDn Home End move · Enter choose · Esc cancel · Tab next",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(3) -> row("Model", model),
        Constraint.Fixed(3) -> row("Country", country),
        Constraint.Fixed(3) -> row("Tags", tagField),
        Constraint.Fixed(1) -> HBox(
          Constraint.Fixed(LabelWidth + 1) -> Spacer,
          Constraint.Fill                  -> tagsLeft
        ),
        Constraint.Fill     -> Spacer,
        Constraint.Fixed(1) -> Text("The theme list has no room below, so it opens upward.", DemoUtils.DimStyle),
        Constraint.Fixed(3) -> row("Theme", theme)
      )
    )
