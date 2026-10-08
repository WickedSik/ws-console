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
  val marks: (String, String),
  val style: CellStyle,
  val enabled: Boolean,
  onToggle: Boolean => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled checkboxes are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val checkedRef = new AtomicBoolean(initialChecked)

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

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty then return

    val isFocused = ctx.focus.isFocused(this.id)
    val isChecked = checked

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

    val (checkedGlyph, uncheckedGlyph) = marks
    val glyph = if isChecked then checkedGlyph else uncheckedGlyph

    // Mark must fit; label truncates to what remains.
    if area.width < glyph.length then return
    canvas.putText(area.x, area.y, glyph, markStyle)

    val labelX = area.x + glyph.length + 1
    val labelWidth = area.width - glyph.length - 1
    if labelWidth <= 0 then return
    val truncated = if label.length > labelWidth then label.take(labelWidth) else label
    canvas.putText(labelX, area.y, truncated, labelStyle)

object Checkbox:

  /** Default mark glyphs — `("☑", "☐")`. */
  val DefaultMarks: (String, String) = ("☑", "☐")

  /**
   * Construct a checkbox. `checked` seeds the initial state; the widget
   * owns it thereafter and fires `onToggle(newChecked)` per Space press.
   */
  def make(
    label: String,
    onToggle: Boolean => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    checked: Boolean = false,
    marks: (String, String) = DefaultMarks,
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true
  ): UIO[Checkbox] =
    ZIO.succeed(new Checkbox(label, checked, marks, style, enabled, onToggle))
