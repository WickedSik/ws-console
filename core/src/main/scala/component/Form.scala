package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Span, StyledLine}
import geometry.{Insets, Rect}
import layout.Constraint

import zio.UIO

/**
 * The styles and spacing of a [[Form]], in one place: every field, section
 * and required marker of a form takes them from here, never from itself.
 *
 *   - `label` — field labels (`emphasis` by default)
 *   - `required` — the required marker after a label (`error` by default);
 *     visual only, the form does not validate
 *   - `help` — help text and section subtitles (`muted` by default)
 *   - `heading` — a section's border and the title on it
 *   - `border` — a section's border glyphs
 *   - `requiredMark` — the marker text, drawn right after the label
 *   - `gap` — blank rows between items of a form or section
 *   - `labelWidth` — the label column of `Inline` and `Beside` fields, in
 *     cells, the same on every field so their controls line up
 */
final case class FormStyle(
  label: CellStyle = CellStyle(attributes = Set(Attribute.Bold)),
  required: CellStyle = CellStyle(fg = Foreground.Named(FgColor.Red)),
  help: CellStyle = CellStyle(attributes = Set(Attribute.Dim)),
  heading: CellStyle = CellStyle(attributes = Set(Attribute.Bold)),
  border: BoxStyle = BoxStyle.Single,
  requiredMark: String = "*",
  gap: Int = 1,
  labelWidth: Int = 24
):
  require(gap >= 0, s"gap must not be negative, got $gap")
  require(labelWidth >= 1, s"labelWidth must be positive, got $labelWidth")

/** Where a field puts its label relative to its control. */
enum FieldLayout:
  /** Label, help, control and footer, each on its own rows: the common form field. */
  case Stacked

  /**
   * Label (and help) in the form's label column, control and footer to its
   * right. The label sits on the control's middle row, so it lines up with
   * a slider track or a bordered field's text row.
   */
  case Inline

  /**
   * Label and help stacked in the form's label column, the control beside
   * them from the top: a preview or drop zone next to its description.
   */
  case Beside

/**
 * A form written as data: fields, titled sections, side-by-side columns
 * and free rows, built into a component tree with one [[FormStyle]].
 *
 * {{{
 *   Form.make(
 *     Seq(
 *       Form.Item.Section("Step 1", Seq(
 *         Form.Item.Field("Name", nameInput, help = Some("The character's name."),
 *                         footer = Some(nameCounter), required = true),
 *         Form.Item.Field("Temperature", sliderRow, layout = FieldLayout.Inline)
 *       ))
 *     )
 *   )
 * }}}
 *
 * Items stack top to bottom, `gap` blank rows apart; the tree is laid out
 * in reading order, so Tab follows it. Every height comes from the
 * children's preferred heights, so a control in a field needs one — all
 * library controls have it; wrap anything else in a `Fixed` row.
 *
 * The form holds no state. Values, counters ([[Label]] footers updated
 * from `onChange`) and anything else that changes are the host's.
 */
object Form:

  /** One entry of a form or section. */
  enum Item:
    /**
     * A labelled control. `help` is wrapped text under (or, `Inline` and
     * `Beside`, beside) the label; `footer` sits under the control, typically
     * a [[Label]] counter. `aside` shares the label row, right half, for a
     * secondary action such as a "Show visual view" toggle; it aligns
     * itself. `control` is any component, including a row of buttons.
     */
    case Field(
      label: String,
      control: Component,
      help: Option[String] = None,
      footer: Option[Component] = None,
      required: Boolean = false,
      aside: Option[Component] = None,
      layout: FieldLayout = FieldLayout.Stacked
    )

    /**
     * A bordered card with `title` on its top border and an optional
     * `subtitle` as its first row, holding `items`.
     */
    case Section(title: String, items: Seq[Item], subtitle: Option[String] = None)

    /** Items side by side, sharing the width equally, two cells apart. */
    case Columns(items: Seq[Item])

    /** Any component at its preferred height: header rows, buttons, notes. */
    case Row(component: Component)

  /** Blank columns between `Columns` items, and between a label column and its control. */
  val ColumnGap: Int = 2

  /** The form's content without a scroll viewport: `items` stacked `gap` rows apart. */
  def content(items: Seq[Item], style: FormStyle = FormStyle()): Component =
    stack(items, style)

  /** The form inside a [[ScrollView]], for pages taller than the terminal. */
  def make(items: Seq[Item], style: FormStyle = FormStyle(), scrollStyle: CellStyle = CellStyle.Empty): UIO[ScrollView] =
    ScrollView.make(content(items, style), style = scrollStyle)

  // ===== Building =====

  private def stack(items: Seq[Item], style: FormStyle): VBox =
    val built = items.map(build(_, style))
    val gap = (Constraint.Fixed(style.gap): Constraint) -> (Spacer: Component)
    val rows = built.zipWithIndex.flatMap { (c, i) =>
      val row = (Constraint.Fit: Constraint) -> c
      if i == 0 || style.gap == 0 then Seq(row) else Seq(gap, row)
    }
    VBox(rows*)

  private def build(item: Item, style: FormStyle): Component =
    item match
      case f: Item.Field => field(f, style)
      case Item.Section(title, items, subtitle) =>
        val intro = subtitle.toSeq.map(s => (Constraint.Fit: Constraint) -> (helpText(s, style): Component))
        Panel(
          child = VBox((intro :+ ((Constraint.Fit: Constraint) -> (stack(items, style): Component)))*),
          title = Some(s" $title "),
          border = style.border,
          style = style.heading,
          padding = Insets.symmetric(horizontal = 1, vertical = 0)
        )
      case Item.Columns(items) =>
        val gap = (Constraint.Fixed(ColumnGap): Constraint) -> (Spacer: Component)
        val cols = items.zipWithIndex.flatMap { (it, i) =>
          val col = (Constraint.Fill: Constraint) -> build(it, style)
          if i == 0 then Seq(col) else Seq(gap, col)
        }
        HBox(cols*)
      case Item.Row(component) => component

  private def field(f: Item.Field, style: FormStyle): Component =
    val labelRow: Component = f.aside match
      case None    => Text(labelLine(f, style))
      case Some(a) => HBox(Constraint.Fill -> Text(labelLine(f, style)), Constraint.Fill -> a)
    val help = f.help.map(helpText(_, style))
    def labelColumn = column(Some(labelRow) ++ help)
    def controlColumn = column(Some(f.control) ++ f.footer)
    f.layout match
      case FieldLayout.Stacked => column(Some(labelRow) ++ help ++ Some(f.control) ++ f.footer)
      case FieldLayout.Beside =>
        HBox(
          Constraint.Fixed(style.labelWidth) -> labelColumn,
          Constraint.Fixed(ColumnGap)        -> Spacer,
          Constraint.Fill                    -> controlColumn
        )
      case FieldLayout.Inline => InlineField(labelColumn, f.control, controlColumn, style.labelWidth)

  /** The label in the label style, then the required marker in its own style. */
  private def labelLine(f: Item.Field, style: FormStyle): StyledLine =
    val label = Span(f.label, style.label)
    if f.required then StyledLine(Vector(label, Span(style.requiredMark, style.required)))
    else StyledLine(Vector(label))

  private def helpText(text: String, style: FormStyle): WrappedText =
    WrappedText(StyledLine(Vector(Span(text))), style.help)

  /** Parts stacked at their preferred heights. */
  private def column(parts: Iterable[Component]): VBox =
    VBox(parts.toSeq.map(p => (Constraint.Fit: Constraint) -> p)*)

  /**
   * The `Inline` layout: the label column, `labelWidth` cells wide, moved
   * down so its first row meets the control's middle row; the control
   * column to its right, [[ColumnGap]] cells on.
   */
  final private case class InlineField(labels: Component, control: Component, controls: Component, labelWidth: Int)
      extends Component:

    private def split(width: Int): (Int, Int) =
      val left = math.min(labelWidth, width)
      (left, math.max(0, width - left - ColumnGap))

    /** Rows the label column moves down: to the control's middle row. */
    private def offset(controlWidth: Int): Int =
      (control.preferredHeight(controlWidth).getOrElse(1) - 1) / 2

    override def preferredHeight(width: Int): Option[Int] =
      val (left, right) = split(width)
      val labelRows = labels.preferredHeight(left).map(_ + offset(right))
      val controlRows = controls.preferredHeight(right)
      (labelRows ++ controlRows).maxOption

    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      val (left, right) = split(area.width)
      val down = math.min(offset(right), area.height)
      Seq(
        labels   -> Rect(area.x, area.y + down, left, area.height - down),
        controls -> Rect(area.x + area.width - right, area.y, right, area.height)
      )

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      childLayouts(area).foreach((c, r) => c.render(r, canvas, ctx))
