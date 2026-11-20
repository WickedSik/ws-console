package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, CellStyle, Frame}
import event.{Event, EventResult, KeyInput, Keymap}
import geometry.Rect
import glyph.Widths

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Focusable bistable toggle pairing a mark with a label.
 *
 * `onToggle` is bound at `make` and stored unexecuted. On a key bound to
 * [[Checkbox.Action.Toggle]] while focused and enabled (by default a bare
 * Space; see [[Checkbox.DefaultKeymap]]), the widget flips local state
 * and returns `EventResult.Perform(onToggle(newChecked))`. Every other
 * key bubbles, Enter among them, so a form can treat it as submit.
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
  val marks: BooleanMarks,
  val style: CellStyle,
  val enabled: Boolean,
  val keymap: Keymap[Checkbox.Action],
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
      KeyInput.decode(event, keymap) match
        case KeyInput.Bound(Checkbox.Action.Toggle)                   => toggle()
        case KeyInput.Text(_) | KeyInput.Unbound(_) | KeyInput.NotKey => EventResult.Ignored

  private def toggle(): EventResult =
    val next = !checked
    checkedRef.set(next)
    EventResult.Perform(onToggle(next))

  // ===== Rendering =====

  /** One row: glyph and label. */
  override def preferredHeight(width: Int): Option[Int] = Some(1)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val isChecked = checked
    val glyph = marks(isChecked)

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

      // The label starts after the wider mark, so toggling never moves it.
      val labelWidth = area.width - marks.width - 1
      if labelWidth > 0 then
        canvas.putText(area.x + marks.width + 1, area.y, Widths.truncate(label, labelWidth), labelStyle)

object Checkbox:

  /** What a key can ask a checkbox to do. */
  enum Action:
    /** Flip the checked state and fire `onToggle`. */
    case Toggle

  /** A bare Space toggles. */
  val DefaultKeymap: Keymap[Action] = Keymap(Keymap.plain(' ') -> Action.Toggle)

  /** Default mark glyphs — `☑` checked, `☐` unchecked. */
  val DefaultMarks: BooleanMarks = BooleanMarks("☑", "☐")

  /**
   * Construct a checkbox. `checked` seeds the initial state; the widget
   * owns it thereafter and fires `onToggle(newChecked)` per toggle.
   * `keymap` decides which keys toggle it.
   */
  def make(
    label: String,
    onToggle: Boolean => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    checked: Boolean = false,
    marks: BooleanMarks = DefaultMarks,
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true,
    keymap: Keymap[Action] = DefaultKeymap
  ): UIO[Checkbox] =
    ZIO.succeed(Checkbox(label, checked, marks, style, enabled, keymap, onToggle))
