package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, CellStyle, Frame}
import event.KeyEvent.SpecialKey
import event.{Event, EventResult, SpecialKeyCode}
import geometry.Rect

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Focusable set of mutually exclusive options, exactly one selected.
 *
 * A single focus stop. While focused: Up/Down (Left/Right accepted)
 * move the selection, Home/End jump to first/last. Changes at the
 * bounds return `Ignored` (no wrap-around).
 *
 * `onSelect` is bound at `make`. On each change the widget updates its
 * local index and returns `EventResult.Perform(onSelect(newIndex))`.
 * The widget owns the mutable index; the constructor `selected` seeds
 * it and the host observes changes via `onSelect`.
 *
 * Visual composition — one option per row, `{mark}` `{space}` `{label}`:
 *
 *   - Unselected row               — `style + Dim`
 *   - Selected mark               — `style + Bold`
 *   - Selected label at rest      — `style`
 *   - Selected label when focused — `style + Bold`
 *   - Disabled                    — every row `style + Dim`
 *
 * Long labels truncate. A "none selected" entry, if wanted, is added
 * as an explicit option — the group does not model absent selection.
 */
final class RadioGroup private (
  val options:     Seq[String],
  initialSelected: Int,
  val marks:       (String, String),
  val style:       CellStyle,
  val enabled:     Boolean,
  onSelect:        Int => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled or empty groups are excluded from the focus cycle. */
  override val focusable: Boolean = enabled && options.nonEmpty

  private val selectedRef =
    new AtomicInteger(clampIndex(initialSelected))

  /** Current selected option index, clamped to `[0, options.size - 1]`. */
  def selected: Int = selectedRef.get()

  private def clampIndex(i: Int): Int =
    if options.isEmpty then 0
    else math.max(0, math.min(options.size - 1, i))

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || options.isEmpty || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        case SpecialKey(SpecialKeyCode.Up, _)    => moveSelection(-1)
        case SpecialKey(SpecialKeyCode.Left, _)  => moveSelection(-1)
        case SpecialKey(SpecialKeyCode.Down, _)  => moveSelection(+1)
        case SpecialKey(SpecialKeyCode.Right, _) => moveSelection(+1)
        case SpecialKey(SpecialKeyCode.Home, _)  => setSelection(0)
        case SpecialKey(SpecialKeyCode.End, _)   => setSelection(options.size - 1)
        case _                                   => EventResult.Ignored

  private def moveSelection(delta: Int): EventResult =
    setSelection(selected + delta)

  private def setSelection(target: Int): EventResult =
    val current = selected
    val next    = clampIndex(target)
    if next == current then EventResult.Ignored
    else
      selectedRef.set(next)
      EventResult.Perform(onSelect(next))

  // ===== Rendering =====

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || options.isEmpty then return

    val isFocused                        = ctx.focus.isFocused(this.id)
    val currentSelected                  = selected
    val (selectedGlyph, unselectedGlyph) = marks

    val muted = style.copy(attributes = style.attributes + Attribute.Dim)
    val bold  = style.copy(attributes = style.attributes + Attribute.Bold)

    val rows = math.min(options.size, area.height)
    var i    = 0
    while i < rows do
      val label          = options(i)
      val isThisSelected = i == currentSelected

      val (markStyle, labelStyle) =
        if !enabled then
          (muted, muted)
        else if isThisSelected then
          val ls = if isFocused then bold else style
          (bold, ls)
        else
          (muted, muted)

      val glyph = if isThisSelected then selectedGlyph else unselectedGlyph
      val rowY  = area.y + i
      if area.width >= glyph.length + 2 then
        canvas.putText(area.x, rowY, glyph, markStyle)
        val labelX     = area.x + glyph.length + 1
        val labelWidth = area.width - glyph.length - 1
        val truncated  = if label.length > labelWidth then label.take(labelWidth) else label
        canvas.putText(labelX, rowY, truncated, labelStyle)

      i += 1

object RadioGroup:

  /** Default mark glyphs — `("●", "○")`. */
  val DefaultMarks: (String, String) = ("●", "○")

  /**
   * Construct a radio group. `selected` seeds the initial index (clamped
   * into range); the widget owns it thereafter and fires
   * `onSelect(newIndex)` on every change.
   */
  def make(
    options:  Seq[String],
    onSelect: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Int              = 0,
    marks:    (String, String) = DefaultMarks,
    style:    CellStyle        = CellStyle.Empty,
    enabled:  Boolean          = true
  ): UIO[RadioGroup] =
    ZIO.succeed(new RadioGroup(options, selected, marks, style, enabled, onSelect))
