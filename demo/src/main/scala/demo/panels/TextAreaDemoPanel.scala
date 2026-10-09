package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{BoxStyle, Canvas, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.{Insets, Rect}
import glyph.{Graphemes, Widths}
import layout.Constraint

import zio.UIO

/**
 * Showcase panel for [[component.TextArea]]: one empty field with a
 * placeholder, one pre-filled with multi-line text and a long wrapping
 * line, and one disabled. A live line under each reports its `rows` and
 * the caret's visual line and column; the first field has a `maxLength`
 * and its line adds a "Characters: n/max" counter.
 *
 * The fields sit in a [[component.ScrollView]], so on a short terminal
 * Tab still brings each one into view, caret included.
 */
object TextAreaDemoPanel:

  private val fieldStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val labelStyle = CellStyle(fg = Foreground.Named(FgColor.White))

  private val Prefilled: String =
    "Multi-line content, seeded by the host.\n" +
      "This second line is long enough to soft-wrap at the field's width; " +
      "the breaks are visual only and never alter the value.\n" +
      "\n" +
      "Wide graphemes wrap by cell: 你好世界, こんにちは.\n" +
      "Last line."

  private val NotesLimit = 120

  /**
   * Reads the field's caret on each render; drawn after the field in the
   * same frame. A field with a `maxLength` also shows its character count,
   * counted with [[buffer.Graphemes.count]] as the field itself counts.
   */
  final private class CaretLine(field: TextArea) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val p = field.caretPosition
      val counter = field.maxLength.fold("")(max => s" · Characters: ${Graphemes.count(field.value)}/$max")
      val text = s"rows ${field.rows} · line ${p.line + 1} · col ${p.column + 1}$counter"
      canvas.putText(area.x, area.y, Widths.truncate(text, area.width), DemoUtils.DimStyle)

  /** A label, the field at its preferred height, and its caret line. */
  private def labelled(label: String, field: TextArea): Seq[(Constraint, Component)] =
    Seq(
      Constraint.Fixed(1) -> Text(label, labelStyle),
      Constraint.Fit      -> field,
      Constraint.Fixed(1) -> new CaretLine(field),
      Constraint.Fixed(1) -> Spacer
    )

  def make: UIO[AppPanel] =
    val padding = Insets.symmetric(horizontal = 1, vertical = 0)
    for
      empty <- TextArea.make(
                 placeholder = "Nothing here yet. Tab to focus and type; Enter starts a new line.",
                 rows = 2,
                 style = fieldStyle,
                 padding = padding,
                 maxLength = Some(NotesLimit)
               )
      filled <- TextArea.make(
                  value = Prefilled,
                  rows = 4,
                  style = fieldStyle,
                  padding = padding,
                  grip = Some(TextArea.GripGlyph)
                )
      disabled <- TextArea.make(
                    value = "Disabled: skipped by Tab, ignores every key.",
                    rows = 2,
                    style = fieldStyle,
                    padding = padding,
                    enabled = false
                  )
      fields = VBox(
                 (labelled(s"Notes (placeholder, at most $NotesLimit characters)", empty) ++
                   labelled("Description (pre-filled, with grip)", filled) ++
                   labelled("Archived (disabled)", disabled))*
               )
      view <- ScrollView.make(fields)
    yield
      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Text Area — multi-line field with soft wrap", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "↑/↓: visual lines · Home/End: line edges · PgUp/PgDn: by rows · Alt+←/→: words",
          DemoUtils.DimStyle
        ),
        Constraint.Fill -> view
      )
      AppPanel.of(tree)
