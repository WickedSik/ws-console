package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.{Insets, Rect}
import glyph.{Graphemes, Widths}
import layout.Constraint

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for the styleguide's [[component.TextInput]] widget.
 *
 * Tab into the field; type; the label below mirrors the buffer as it
 * changes. The mirror is driven by the field's `onChange` — every edit
 * returns `EventResult.Perform(onChange(newValue))`, the render loop
 * runs that on its own fiber, and the natural redraw refreshes the
 * mirror without a poll.
 *
 * A second field carries a `maxLength`; the counter under it reads the
 * field's value and counts with [[buffer.Graphemes.count]], the same unit
 * the field refuses input by.
 */
object TextInputDemoPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val fieldStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val mirrorLabelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  /** Custom leaf component: reads a mirror reference on each render. */
  final private class MirrorLabel(state: AtomicReference[String]) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.nonEmpty then
        val text = "You typed: " + state.get()
        val truncated = if text.length > area.width then text.take(area.width) else text
        canvas.putText(area.x, area.y, truncated, mirrorLabelStyle)

  private val NicknameLimit = 20

  /** "Characters: n/max" for a limited field, read on each render. */
  final private class CharacterCounter(field: TextInput) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.nonEmpty then
        val text = field.maxLength.fold("")(max => s"Characters: ${Graphemes.count(field.value)}/$max")
        canvas.putText(area.x, area.y, Widths.truncate(text, area.width), DemoUtils.DimStyle)

  /** Build the panel — effectful because [[component.TextInput.make]] is UIO. */
  def make: UIO[AppPanel] =
    for
      state <- ZIO.succeed(AtomicReference[String](""))
      input <- TextInput.make(
                 placeholder = "Type here — Tab to focus, arrows to move the caret",
                 onChange = (value: String) => ZIO.succeed { state.set(value); () },
                 style = fieldStyle,
                 padding = Insets.symmetric(horizontal = 1, vertical = 0)
               )
      nickname <- TextInput.make(
                    placeholder = s"Nickname — at most $NicknameLimit characters",
                    style = fieldStyle,
                    padding = Insets.symmetric(horizontal = 1, vertical = 0),
                    maxLength = Some(NicknameLimit)
                  )
    yield
      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Text Input — single-line editable field", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Focused: caret shown (Reverse). Backspace / Delete / arrows / Home / End all wire through.",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(3) -> input,
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> MirrorLabel(state),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(3) -> nickname,
        Constraint.Fixed(1) -> CharacterCounter(nickname),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(
          "The widget owns the edit buffer; onChange is the signal the host sees per edit.",
          bodyStyle
        ),
        Constraint.Fill -> Spacer
      )
      AppPanel.of(tree)
