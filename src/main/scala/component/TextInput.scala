package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Insets, Rect}

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * Focusable single-line editable field.
 *
 * Transient local state — edit buffer, caret position, horizontal
 * scroll offset — held in private atomics. On modifying keys it applies
 * the edit locally and returns `EventResult.Perform(onChange(newValue))`;
 * on caret-only keys, `EventResult.RequestRedraw`.
 *
 * The widget owns the edit buffer internally; the constructor `value`
 * seeds it and the host observes via `onChange`.
 *
 * Visual composition mirrors [[Panel]] and [[Button]]: opaque fill,
 * border (no-op for [[BoxStyle.Borderless]]), content in
 * `area.inner(border.inset).inner(padding)`.
 *
 * Interaction states — `normal`, `focused` (caret shown), `disabled` —
 * derive from focus and the `enabled` flag. `focused` adds `Bold`; the
 * caret is `Reverse` on the cell it stands on. Placeholder renders with
 * `Dim` when the buffer is empty and the field is not focused.
 */
final class TextInput private (
  initialValue:    String,
  val placeholder: String,
  val style:       CellStyle,
  val border:      BoxStyle,
  val padding:     Insets,
  val enabled:     Boolean,
  onChange:        String => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled fields are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val valueRef  = new AtomicReference[String](initialValue)
  private val caretRef  = new AtomicInteger(initialValue.length)
  private val scrollRef = new AtomicInteger(0)

  /** Current edit-buffer contents. Reflects every edit through `onChange`. */
  def value: String = valueRef.get()

  /** Current caret position, clamped to `[0, value.length]`. */
  def caret: Int = math.max(0, math.min(value.length, caretRef.get()))

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        case CharKey(c, mods) if isPrintable(c, mods)  => insertChar(c)
        case SpecialKey(SpecialKeyCode.Backspace, _)   => backspace()
        case SpecialKey(SpecialKeyCode.Delete, _)      => deleteForward()
        case SpecialKey(SpecialKeyCode.Left, _)        => moveCaret(-1)
        case SpecialKey(SpecialKeyCode.Right, _)       => moveCaret(+1)
        case SpecialKey(SpecialKeyCode.Home, _)        => setCaret(0)
        case SpecialKey(SpecialKeyCode.End, _)         => setCaret(value.length)
        case _                                         => EventResult.Ignored

  /** A key qualifies as printable text when no Ctrl/Alt is held and the char is not a control byte. */
  private def isPrintable(c: Char, mods: Set[KeyModifier]): Boolean =
    !mods.contains(KeyModifier.Ctrl) && !mods.contains(KeyModifier.Alt) && !c.isControl

  private def insertChar(c: Char): EventResult =
    val current = value
    val at      = caret
    val next    = current.substring(0, at) + c + current.substring(at)
    valueRef.set(next)
    caretRef.set(at + 1)
    EventResult.Perform(onChange(next))

  private def backspace(): EventResult =
    val current = value
    val at      = caret
    if at == 0 then EventResult.Ignored
    else
      val next = current.substring(0, at - 1) + current.substring(at)
      valueRef.set(next)
      caretRef.set(at - 1)
      EventResult.Perform(onChange(next))

  private def deleteForward(): EventResult =
    val current = value
    val at      = caret
    if at >= current.length then EventResult.Ignored
    else
      val next = current.substring(0, at) + current.substring(at + 1)
      valueRef.set(next)
      EventResult.Perform(onChange(next))

  private def moveCaret(delta: Int): EventResult =
    val at   = caret
    val next = math.max(0, math.min(value.length, at + delta))
    if next == at then EventResult.Ignored
    else
      caretRef.set(next)
      EventResult.RequestRedraw

  private def setCaret(pos: Int): EventResult =
    val at   = caret
    val next = math.max(0, math.min(value.length, pos))
    if next == at then EventResult.Ignored
    else
      caretRef.set(next)
      EventResult.RequestRedraw

  // ===== Rendering =====

  private def undersizedForBorder(area: Rect): Boolean =
    border.inset > 0 && (area.width < 2 || area.height < 2)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || undersizedForBorder(area) then return

    val isFocused = ctx.focus.isFocused(this.id)
    val effectiveStyle =
      if !enabled     then InteractionState.disabled(style)
      else if isFocused then InteractionState.focused(style)
      else                 style

    canvas.fillRect(area, Cell(' ', effectiveStyle))
    canvas.drawBox(area, border, None, effectiveStyle)

    val inner = area.inner(border.inset).inner(padding)
    if inner.isEmpty then return

    val v          = value
    val caretPos   = caret
    val innerWidth = inner.width
    val scroll     = adjustScroll(caretPos, innerWidth, v.length)

    if v.isEmpty && !isFocused && placeholder.nonEmpty then
      val placeholderStyle =
        effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Dim)
      val truncated =
        if placeholder.length > innerWidth then placeholder.take(innerWidth)
        else placeholder
      canvas.putText(inner.x, inner.y, truncated, placeholderStyle)
    else
      val end     = math.min(v.length, scroll + innerWidth)
      val visible = if scroll < end then v.substring(scroll, end) else ""
      canvas.putText(inner.x, inner.y, visible, effectiveStyle)

      if isFocused then
        val caretX = inner.x + (caretPos - scroll)
        if caretX >= inner.x && caretX < inner.x + innerWidth then
          val charUnderCaret = if caretPos < v.length then v.charAt(caretPos) else ' '
          val caretStyle     =
            effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Reverse)
          canvas.putChar(caretX, inner.y, charUnderCaret, caretStyle)

  /**
   * Slide the horizontal scroll to keep the caret visible in
   * `[scroll, scroll + innerWidth)`. Clamps to non-negative and to a
   * reasonable ceiling so trailing empty space does not scroll past
   * `valueLength`. Mutates `scrollRef` when the position changes;
   * safe because scroll offset is widget-local state.
   */
  private def adjustScroll(caretPos: Int, innerWidth: Int, valueLength: Int): Int =
    if innerWidth <= 0 then 0
    else
      val current = scrollRef.get()
      val naive =
        if caretPos < current                       then caretPos
        else if caretPos >= current + innerWidth     then caretPos - innerWidth + 1
        else                                              current
      // Cap so we do not scroll further right than needed to show the
      // caret at the very end of the value (caret one past last char).
      val ceiling = math.max(0, valueLength - innerWidth + 1)
      val clamped = math.max(0, math.min(math.max(current, ceiling), naive))
      if clamped != current then scrollRef.set(clamped)
      clamped

object TextInput:

  /**
   * Construct a text input. `value` seeds the edit buffer; the widget
   * owns it thereafter and fires `onChange(newValue)` per edit.
   * `placeholder` renders only when the buffer is empty and unfocused.
   */
  def make(
    value:       String    = "",
    placeholder: String    = "",
    onChange:    String => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    style:       CellStyle = CellStyle.Empty,
    border:      BoxStyle  = BoxStyle.Single,
    padding:     Insets    = Insets.zero,
    enabled:     Boolean   = true
  ): UIO[TextInput] =
    ZIO.succeed(new TextInput(value, placeholder, style, border, padding, enabled, onChange))
