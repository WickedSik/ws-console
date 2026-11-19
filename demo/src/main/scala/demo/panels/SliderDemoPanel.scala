package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import event.Keymap
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
 *
 * A fifth row rebinds its slider's keys: [[VimKeymap]] extends
 * [[component.Slider.DefaultKeymap]] with vim's `h` / `l`, `H` / `L` and
 * `g` / `G`, so the arrows keep working beside them.
 */
object SliderDemoPanel:

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val sliderStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val fieldStyle = CellStyle(fg = Foreground.Named(FgColor.White))

  private val dutch: Locale = Locale.forLanguageTag("nl")

  /** One reference setting: its range, step, default, and decimals shown in the field. */
  final private[demo] case class Setting(
    label: String,
    min: Double,
    max: Double,
    step: Double,
    default: Double,
    decimals: Int
  ):

    def format(v: Double): String =
      val pattern = if decimals == 0 then "0" else "0." + "0" * decimals
      DecimalFormat(pattern, DecimalFormatSymbols.getInstance(dutch)).format(v)

    /** The typed number, when it parses and lies inside the range. */
    def parse(text: String): Option[Double] =
      text.trim.replace(',', '.').toDoubleOption.filter(v => v >= min && v <= max)

  private[demo] val settings: Seq[Setting] = Seq(
    Setting("Temperature (?)", 0, 2, 0.1, 0.8, 1),
    Setting("Repetition Penalty (?)", 0.5, 2, 0.1, 1.1, 1),
    Setting("Top P (?)", 0, 1, 0.1, 0.9, 1),
    Setting("Top K (?)", 0, 100, 1, 30, 0)
  )

  /** The setting the rebound slider drives. */
  private[demo] val vimSetting: Setting = Setting("Volume (vim keys)", 0, 100, 1, 50, 0)

  /** The default slider keys plus vim's: `h` / `l` a step, `H` / `L` a page, `g` / `G` the bounds. */
  private[demo] val VimKeymap: Keymap[Slider.Action] = Slider.DefaultKeymap ++ Keymap(
    Keymap.plain('h') -> Slider.Action.Step(-1),
    Keymap.plain('l') -> Slider.Action.Step(+1),
    Keymap.plain('H') -> Slider.Action.Page(-1),
    Keymap.plain('L') -> Slider.Action.Page(+1),
    Keymap.plain('g') -> Slider.Action.ToMin,
    Keymap.plain('G') -> Slider.Action.ToMax
  )

  /**
   * Slider and field on one three-row band, bound both ways. The slider
   * sits one row down so its track lines up with the field's text row, and
   * its default label with the field's bottom border. `keymap` binds the
   * slider's keys.
   */
  private[demo] def control(setting: Setting, keymap: Keymap[Slider.Action] = Slider.DefaultKeymap): UIO[Component] =
    for
      sliderRef <- ZIO.succeed(AtomicReference[Option[Slider]](None))
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
                  style = sliderStyle,
                  keymap = keymap
                )
      _ <- ZIO.succeed(sliderRef.set(Some(slider)))
    yield HBox(
      Constraint.Fill -> VBox(
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(2) -> slider
      ),
      Constraint.Fixed(2) -> Spacer,
      // Three characters plus the cell TextInput keeps free for the caret.
      Constraint.Fixed(8) -> field
    )

  /** Label beside the slider-and-field band. */
  private def row(setting: Setting, keymap: Keymap[Slider.Action] = Slider.DefaultKeymap): UIO[Component] =
    control(setting, keymap).map { band =>
      HBox(
        Constraint.Fixed(24) -> VBox(
          Constraint.Fixed(1) -> Spacer,
          Constraint.Fixed(1) -> Text(setting.label, labelStyle),
          Constraint.Fill     -> Spacer
        ),
        Constraint.Fill -> band
      )
    }

  def make: UIO[AppPanel] =
    for
      rows <- ZIO.foreach(settings)(row(_))
      vim  <- row(vimSetting, VimKeymap)
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
          )
        ) ++
          (rows :+ vim).map(r => Constraint.Fixed(3) -> r) ++
          Seq(
            Constraint.Fixed(1) -> Text(
              "Type a number in a box (0,8 or 0.8) to move its slider; the slider writes back.",
              fieldStyle
            ),
            Constraint.Fixed(1) -> Text(
              "Volume rebinds its keys: h/l one step · H/L ten steps · g/G the bounds · arrows still work",
              DemoUtils.DimStyle
            ),
            Constraint.Fill -> Spacer
          ))*
      )
      AppPanel.of(tree)
