package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Span, Spans, StyledLine}
import component.*
import demo.DemoUtils
import layout.Constraint

import zio.UIO

/**
 * Showcase panel for [[component.WrappedText]] and styled spans.
 *
 * One paragraph with styled spans wrapped at three widths side by side,
 * a CJK and Cyrillic paragraph, a `maxLines = 2` caption ending in `…`,
 * and form labels whose required marker is a red span on the same line
 * as the label. Every row is sized by `Fit`, so each block takes the
 * height its text wraps to; the whole column sits in a focusable
 * ScrollView for terminals too short to show it at once.
 */
object TextFlowPanel:

  private val sectionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  private val bodyStyle = CellStyle(fg = Foreground.Named(FgColor.White))
  private val accent = CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))
  private val code = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))
  private val warning = CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Underline))
  private val required = CellStyle(fg = Foreground.Named(FgColor.BrightRed), attributes = Set(Attribute.Bold))
  private val labelStyle = CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val paragraph: StyledLine = Spans(
    "Help text ",
    Span("wraps on words", accent),
    " and measures in terminal columns. A span such as ",
    Span("Widths.stringWidth", code),
    " keeps its style when it ",
    Span("crosses a line break", warning),
    ", and a word longer than the column breaks at the last grapheme that fits."
  )

  private val unicode: StyledLine = Spans(
    Span("中文字符", accent),
    "占两列，绝不会跨越右边缘。",
    "\n",
    Span("Кириллица", code),
    " переносится по словам, как и латиница. ",
    "Emoji 👍🏽 and flags 🇳🇱 stay whole."
  )

  private val caption: StyledLine = Spans(
    "This caption is capped at two lines with ",
    Span("maxLines = 2", code),
    ". Whatever does not fit is cut, and the last kept line ends in an ellipsis so the reader knows there is more."
  )

  private def fit(c: Component): (Constraint, Component) = Constraint.Fit -> c
  private def gap: (Constraint, Component) = Constraint.Fixed(1)          -> Spacer
  private def section(title: String): (Constraint, Component) = fit(Text(title, sectionStyle))

  private def column(width: Int): Panel =
    Panel(title = Some(s"$width cols"), child = WrappedText(paragraph, style = bodyStyle))

  private def label(name: String, isRequired: Boolean): Component =
    Text(if isRequired then Spans(Span(name, labelStyle), Span("*", required)) else Spans(Span(name, labelStyle)))

  def make: UIO[AppPanel] =
    val content = VBox(
      section("One paragraph, three widths"),
      fit(
        HBox(
          Constraint.Fixed(18) -> column(16),
          Constraint.Fixed(1)  -> Spacer,
          Constraint.Fixed(30) -> column(28),
          Constraint.Fixed(1)  -> Spacer,
          Constraint.Fill      -> Panel(title = Some("rest"), child = WrappedText(paragraph, style = bodyStyle))
        )
      ),
      gap,
      section("Wide glyphs and other scripts"),
      fit(
        HBox(
          Constraint.Fixed(24) -> Panel(child = WrappedText(unicode, style = bodyStyle)),
          Constraint.Fixed(1)  -> Spacer,
          Constraint.Fill      -> Panel(child = WrappedText(unicode, style = bodyStyle, align = Alignment.Center))
        )
      ),
      gap,
      section("Capped at two lines"),
      fit(
        HBox(
          Constraint.Fixed(40) -> WrappedText(caption, style = DemoUtils.DimStyle, maxLines = Some(2)),
          Constraint.Fill      -> Spacer
        )
      ),
      gap,
      section("Form labels"),
      fit(label("Name", isRequired = true)),
      fit(
        WrappedText(
          Spans("The name your character goes by. Shown in the chat header and in every message."),
          style = DemoUtils.DimStyle
        )
      ),
      gap,
      fit(label("Nickname", isRequired = false)),
      fit(WrappedText(Spans("Optional. Leave empty to use the name."), style = DemoUtils.DimStyle))
    )

    for view <- ScrollView.make(content, focusable = true, style = accent)
    yield AppPanel.of(
      VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Text Flow — styled spans, word wrap, Unicode", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Ctrl+PgUp/PgDn page · Ctrl+Up/Down row · resize to watch it reflow",
          DemoUtils.DimStyle
        ),
        Constraint.Fill -> view
      )
    )
