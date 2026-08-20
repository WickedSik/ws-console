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
 * Focusable bistable toggle pairing a check mark with a label.
 *
 * Constructor-bound signal: `onToggle` is supplied at `make` and stored
 * unexecuted. On Space while focused and enabled, the widget flips its
 * local state and returns `EventResult.Perform(onToggle(newChecked))`.
 *
 * '''Shape deviation from styleguide §3.''' As with [[TextInput]], the
 * styleguide's "the host supplies `checked`" model presumes retained-
 * mode identity. Reconstructing the widget on every toggle would
 * allocate a fresh `ComponentId` and lose focus. Practical shape:
 * constructor's `checked` is the initial state; the widget owns the
 * mutable state thereafter and the host observes via `onToggle`.
 *
 * Visual composition — one row, single-line layout:
 *
 *   `{mark}` `{space}` `{label}`
 *
 * Mark and label carry independent styles:
 *
 *   - Label style — the consumer's `style` arg (the label's role)
 *   - Mark style — derived from `checked` state on top of `style`:
 *     `+ Bold` when checked (accent-like), `+ Dim` when unchecked
 *     (muted-like). No `Theme` service yet — accent/muted expressed as
 *     attribute modulation. When `Theme` ships, this derivation moves
 *     behind `ctx.theme.markFor(role, checked)`.
 *
 * Interaction states then modulate the whole widget per §2.3:
 *
 *   - `focused`  — `Bold` on both mark and label
 *   - `disabled` — `Dim` on both mark and label; excluded from focus
 *
 * Long labels truncate to fit. Hanging-indent wrap is deferred to
 * `WrappedText` (styleguide §5 roadmap) — a shared dependency the
 * whole campaign waits on.
 */
final class Checkbox private (
  val label:      String,
  initialChecked: Boolean,
  val marks:      (String, String),
  val style:      CellStyle,
  val enabled:    Boolean,
  onToggle:       Boolean => ZIO[Frame, IOException, Unit]
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

    // Label style — role plus interaction-state modulation.
    val labelStyle =
      if !enabled     then InteractionState.disabled(style)
      else if isFocused then InteractionState.focused(style)
      else                 style

    // Mark style — the consumer's role plus a checked/unchecked
    // derivation (Bold for accent, Dim for muted), then the same
    // interaction-state modulation as the label.
    val markBase =
      if isChecked then style.copy(attributes = style.attributes + Attribute.Bold)
      else              style.copy(attributes = style.attributes + Attribute.Dim)
    val markStyle =
      if !enabled     then InteractionState.disabled(markBase)
      else if isFocused then InteractionState.focused(markBase)
      else                 markBase

    val (checkedGlyph, uncheckedGlyph) = marks
    val glyph                          = if isChecked then checkedGlyph else uncheckedGlyph

    // Layout: mark + space + label.
    // The mark alone must fit; the label truncates to whatever remains.
    if area.width < glyph.length then return
    canvas.putText(area.x, area.y, glyph, markStyle)

    val labelX     = area.x + glyph.length + 1
    val labelWidth = area.width - glyph.length - 1
    if labelWidth <= 0 then return
    val truncated  = if label.length > labelWidth then label.take(labelWidth) else label
    canvas.putText(labelX, area.y, truncated, labelStyle)

object Checkbox:

  /** Styleguide-default mark glyphs — `("☑", "☐")`. */
  val DefaultMarks: (String, String) = ("☑", "☐")

  /**
   * Construct a checkbox.
   *
   * `checked` is the initial state; the widget owns the state
   * thereafter and fires `onToggle(newChecked)` per Space keypress.
   */
  def make(
    label:    String,
    onToggle: Boolean => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    checked:  Boolean          = false,
    marks:    (String, String) = DefaultMarks,
    style:    CellStyle        = CellStyle.Empty,
    enabled:  Boolean          = true
  ): UIO[Checkbox] =
    ZIO.succeed(new Checkbox(label, checked, marks, style, enabled, onToggle))
