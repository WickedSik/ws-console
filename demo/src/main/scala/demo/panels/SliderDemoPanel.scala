package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.Insets
import layout.Constraint

import zio.{UIO, ZIO}

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for [[component.Slider]]: the four LLM settings of the
 * form-6 reference, each with its default marked and a companion numeric
 * field kept in sync both ways.
 *
 * The binding is the host's, not the slider's. The slider's `onChange`
 * formats the value and pushes it into the field with
 * [[component.TextInput.set]]; the field's `onChange` parses what was
 * typed and, when it is a number inside the range, pushes it into the
 * slider with [[component.Slider.set]]. Neither `set` fires `onChange`,
 * so the two never echo each other. Formatting is Dutch (`0,8`) to show
 * the decimal separator is a host decision; the field accepts `,` or `.`.
 */
object SliderDemoPanel:

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val sliderStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val fieldStyle = CellStyle(fg = Foreground.Named(FgColor.White))

  private val dutch: Locale = Locale.forLanguageTag("nl")

  /** One reference setting: its range, step, default, and decimals shown in the field. */
  final private case class Setting(
    label: String,
    min: Double,
    max: Double,
    step: Double,
    default: Double,
    decimals: Int
  ):

    def format(v: Double): String =
      val pattern = if decimals == 0 then "0" else "0." + "0" * decimals
      new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(dutch)).format(v)

    /** The typed number, when it parses and lies inside the range. */
    def parse(text: String): Option[Double] =
      text.trim.replace(',', '.').toDoubleOption.filter(v => v >= min && v <= max)

  private val settings: Seq[Setting] = Seq(
    Setting("Temperature (?)", 0, 2, 0.1, 0.8, 1),
    Setting("Repetition Penalty (?)", 0.5, 2, 0.1, 1.1, 1),
    Setting("Top P (?)", 0, 1, 0.1, 0.9, 1),
    Setting("Top K (?)", 0, 100, 1, 30, 0)
  )

  /**
   * Label, slider and field on one three-row band. The slider sits one
   * row down so its track lines up with the field's text row, and its
   * default label with the field's bottom border.
   */
  private def row(setting: Setting): UIO[Component] =
    for
      sliderRef <- ZIO.succeed(new AtomicReference[Option[Slider]](None))
      field <- TextInput.make(
                 value = setting.format(setting.default),
                 onChange = text => ZIO.succeed(setting.parse(text).foreach(v => sliderRef.get().foreach(_.set(v)))),
                 style = fieldStyle,
                 padding = Insets.symmetric(horizontal = 1, vertical = 0)
               )
      slider <- Slider.make(
                  min = setting.min,
                  max = setting.max,
                  step = setting.step,
                  value = setting.default,
                  onChange = v => ZIO.succeed(field.set(setting.format(v))),
                  default = Some(setting.default),
                  style = sliderStyle
                )
      _ <- ZIO.succeed(sliderRef.set(Some(slider)))
    yield HBox(
      Constraint.Fixed(24) -> VBox(
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(setting.label, labelStyle),
        Constraint.Fill     -> Spacer
      ),
      Constraint.Fill -> VBox(
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(2) -> slider
      ),
      Constraint.Fixed(2) -> Spacer,
      // Three characters plus the cell TextInput keeps free for the caret.
      Constraint.Fixed(8) -> field
    )

  def make: UIO[AppPanel] =
    for rows <- ZIO.foreach(settings)(row)
    yield
      val tree = VBox(
        (Seq(
          Constraint.Fixed(3) -> Panel(
            border = BoxStyle.Double,
            style = DemoUtils.HeaderStyle,
            child =
              Text("Slider — a value in a range, with its default marked", DemoUtils.HeaderStyle, Alignment.Center)
          ),
          Constraint.Fixed(1) -> Text(
            "Tab: next control · ←/→ one step · PgUp/PgDn ten steps · Home/End the bounds",
            DemoUtils.DimStyle
          ),
          Constraint.Fixed(1) -> Spacer
        ) ++
          rows.map(r => Constraint.Fixed(3) -> r) ++
          Seq(
            Constraint.Fixed(1) -> Spacer,
            Constraint.Fixed(1) -> Text(
              "Type a number in a box (0,8 or 0.8) to move its slider; the slider writes back.",
              fieldStyle
            ),
            Constraint.Fill -> Spacer
          ))*
      )
      AppPanel.of(tree)
