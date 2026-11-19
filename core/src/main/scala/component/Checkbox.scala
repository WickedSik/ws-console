package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, CellStyle, Frame}
import event.KeyEvent.CharKey
import event.{Event, EventResult}
import geometry.Rect

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Focusable bistable toggle pairing a mark with a label.
 *
 * `onToggle` is bound at `make` and stored unexecuted. On Space while
 * focused and enabled, the widget flips local state and returns
 * `EventResult.Perform(onToggle(newChecked))`.
 *
 * The widget owns the mutable checked state internally; the constructor
 * `checked` seeds the initial value and the host observes changes via
 * `onToggle`.
 *
 * Visual composition — `{mark}` `{space}` `{label}`. Mark carries a
 * checked/unchecked derivation (`+ Bold` when checked, `+ Dim` when
 * unchecked). Interaction states modulate the whole widget: `focused`
 * adds `Bold`, `disabled` adds `Dim` and excludes from focus. Long
 * labels truncate.
 */
final class Checkbox private (
  val label: String,
  initialChecked: Boolean,
  val marks: (on: String, off: String),
  val style: CellStyle,
  val enabled: Boolean,
  onToggle: Boolean => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled checkboxes are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val checkedRef = AtomicBoolean(initialChecked)

  /** Current checked state. Reflects every toggle through `onToggle`. */
  def checked: Boolean = checkedRef.get()

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        case CharKey(' ', mods) if mods.isEmpty => toggle()
        case _                                  => EventResult.Ignored

  private def toggle(): EventResult =
    val next = !checked
    checkedRef.set(next)
    EventResult.Perform(onToggle(next))

  // ===== Rendering =====

  /** One row: glyph and label. */
  override def preferredHeight(width: Int): Option[Int] = Some(1)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val isChecked = checked
    val glyph = if isChecked then marks.on else marks.off

    // Mark must fit; label truncates to what remains.
    if area.nonEmpty && area.width >= glyph.length then
      val isFocused = ctx.focus.isFocused(this.id)

      val labelStyle =
        if !enabled then InteractionState.disabled(style)
        else if isFocused then InteractionState.focused(style)
        else style

      val markBase =
        if isChecked then style.copy(attributes = style.attributes + Attribute.Bold)
        else style.copy(attributes = style.attributes + Attribute.Dim)
      val markStyle =
        if !enabled then InteractionState.disabled(markBase)
        else if isFocused then InteractionState.focused(markBase)
        else markBase

      canvas.putText(area.x, area.y, glyph, markStyle)

      val labelWidth = area.width - glyph.length - 1
      if labelWidth > 0 then canvas.putText(area.x + glyph.length + 1, area.y, label.take(labelWidth), labelStyle)

object Checkbox:

  /** Default mark glyphs — `("☑", "☐")`. */
  val DefaultMarks: (on: String, off: String) = (on = "☑", off = "☐")

  /**
   * Construct a checkbox. `checked` seeds the initial state; the widget
   * owns it thereafter and fires `onToggle(newChecked)` per Space press.
   */
  def make(
    label: String,
    onToggle: Boolean => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    checked: Boolean = false,
    marks: (on: String, off: String) = DefaultMarks,
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true
  ): UIO[Checkbox] =
    ZIO.succeed(Checkbox(label, checked, marks, style, enabled, onToggle))
