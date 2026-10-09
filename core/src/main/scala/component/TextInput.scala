package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame, TextEdit}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * Focusable single-line editable field.
 *
 * Transient local state — edit buffer and caret (one
 * [[buffer.TextEdit]], so edits are grapheme-safe), horizontal scroll
 * offset — held in private atomics. On modifying keys it applies
 * the edit locally and returns `EventResult.Perform(onChange(newValue))`;
 * on caret-only keys, `EventResult.RequestRedraw`.
 *
 * The widget owns the edit buffer internally; the constructor `value`
 * seeds it and the host observes via `onChange`.
 *
 * Visual composition mirrors [[Panel]] and [[Button]]: opaque fill,
 * border (short-circuited when `sides` is empty), content in
 * `area.inner(sides.toInsets).inner(padding)`.
 *
 * Interaction states — `normal`, `focused` (caret shown), `disabled` —
 * derive from focus and the `enabled` flag. `focused` adds `Bold`; the
 * caret is `Reverse` on the cell it stands on. Placeholder renders with
 * `Dim` whenever the buffer is empty (HTML-aligned); when focused, the
 * caret overlays its first cell in `Reverse`.
 *
 * `maxLength`, when set, caps the value in grapheme clusters
 * ([[buffer.Graphemes.count]]). Input that would grow the value past it
 * is refused and consumed; a paste, which arrives key by key, stops at
 * the limit. A seeded `value` over the limit is shown whole and can
 * only shrink.
 */
final class TextInput private (
  initialValue: String,
  val placeholder: String,
  val style: CellStyle,
  val border: BoxStyle,
  val sides: Sides,
  val padding: Insets,
  val enabled: Boolean,
  val maxLength: Option[Int],
  onChange: String => ZIO[Frame, IOException, Unit]
) extends Component:
  require(maxLength.forall(_ >= 0), s"maxLength must not be negative, got $maxLength")

  /** Disabled fields are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val editRef = new AtomicReference[TextEdit](TextEdit.atEnd(initialValue))
  private val scrollRef = new AtomicInteger(0)

  /** Current edit-buffer contents. Reflects every edit through `onChange`. */
  def value: String = editRef.get().value

  /** Current caret position, a char offset on a grapheme boundary in `[0, value.length]`. */
  def caret: Int = editRef.get().caret

  /**
   * Restore the buffer, caret, and horizontal scroll to their initial
   * state — `initialValue` reinstated, caret at its end, scroll cleared.
   * Intended for form-level submit flows that need to clear the field
   * after handing its value off; a widget seeded with `""` therefore
   * empties on reset.
   *
   * Does not fire `onChange`; the caller has already observed the value
   * they are resetting from. Safe from any thread — all mutations touch
   * widget-local atomics only.
   */
  def reset(): Unit =
    editRef.set(TextEdit.atEnd(initialValue))
    scrollRef.set(0)

  /**
   * Replace the buffer with `newValue`, caret at its end, scroll cleared.
   * For a host that pushes a value in — a companion field kept in sync
   * with a [[Slider]], a form loading a record. `maxLength` does not apply:
   * like a seeded `value`, a pushed value is shown whole.
   *
   * Does not fire `onChange`: the caller is the source of the value, so a
   * two-way binding cannot loop. Safe from any thread — it touches
   * widget-local atomics only.
   */
  def set(newValue: String): Unit =
    editRef.set(TextEdit.atEnd(newValue))
    scrollRef.set(0)

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      val edit = editRef.get()
      event match
        // quick motions
        case CharKey('b', m) if m(KeyModifier.Alt) => move(edit.wordLeft) // macOS
        case CharKey('f', m) if m(KeyModifier.Alt) => move(edit.wordRight)
        case SpecialKey(SpecialKeyCode.Left, m) if m(KeyModifier.Alt) => // everything else
          move(edit.wordLeft)
        case SpecialKey(SpecialKeyCode.Right, m) if m(KeyModifier.Alt) =>
          move(edit.wordRight)
        // typing
        case CharKey(c, mods) if isPrintable(c, mods) => change(Some(edit.insert(c.toString)))
        // regular motions
        case SpecialKey(SpecialKeyCode.Backspace, _) => change(edit.backspace)
        case SpecialKey(SpecialKeyCode.Delete, _)    => change(edit.delete)
        case SpecialKey(SpecialKeyCode.Left, _)      => move(edit.left)
        case SpecialKey(SpecialKeyCode.Right, _)     => move(edit.right)
        // Ctrl+Home / Ctrl+End move the enclosing view, not the caret: leave them to bubble.
        case SpecialKey(SpecialKeyCode.Home, m) if !m(KeyModifier.Ctrl) => move(edit.moveTo(0))
        case SpecialKey(SpecialKeyCode.End, m) if !m(KeyModifier.Ctrl)  => move(edit.moveTo(edit.value.length))
        // other
        case _ => EventResult.Ignored

  /** A key qualifies as printable text when no Ctrl/Alt is held and the char is not a control byte. */
  private def isPrintable(c: Char, mods: Set[KeyModifier]): Boolean =
    !mods.contains(KeyModifier.Ctrl) && !mods.contains(KeyModifier.Alt) && !c.isControl

  /**
   * Apply a value-changing edit and report it; `None` means nothing
   * changed. An edit past `maxLength` is refused but consumed, so the key
   * cannot bubble into an application shortcut.
   */
  private def change(next: Option[TextEdit]): EventResult =
    next match
      case None => EventResult.Ignored
      case Some(e) if !TextEdit.withinLimit(value, e.value, maxLength) => EventResult.Consumed
      case Some(e) =>
        editRef.set(e)
        EventResult.Perform(onChange(e.value))

  /** Apply a caret-only move; `None` means the caret stayed put. */
  private def move(next: Option[TextEdit]): EventResult =
    next match
      case None => EventResult.Ignored
      case Some(e) =>
        editRef.set(e)
        EventResult.RequestRedraw

  // ===== Rendering =====

  private def undersizedForBorder(area: Rect): Boolean =
    val insets = sides.toInsets
    area.width < insets.left + insets.right || area.height < insets.top + insets.bottom

  /** One content row plus border rows and vertical padding. */
  override def preferredHeight(width: Int): Option[Int] =
    val chrome = sides.toInsets
    Some(1 + chrome.top + chrome.bottom + padding.top + padding.bottom)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || undersizedForBorder(area) then return

    val isFocused = ctx.focus.isFocused(this.id)
    val effectiveStyle =
      if !enabled then InteractionState.disabled(style)
      else if isFocused then InteractionState.focused(style)
      else style

    canvas.fillRect(area, Cell(' ', effectiveStyle))
    canvas.drawBox(area, border, sides, None, effectiveStyle)

    val inner = area.inner(sides.toInsets).inner(padding)
    if inner.isEmpty then return

    val v = value
    val caretPos = caret
    val innerWidth = inner.width
    val scroll = adjustScroll(caretPos, innerWidth, v.length)

    if v.isEmpty && placeholder.nonEmpty then
      val placeholderStyle =
        effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Dim)
      val truncated =
        if placeholder.length > innerWidth then placeholder.take(innerWidth)
        else placeholder
      canvas.putText(inner.x, inner.y, truncated, placeholderStyle)

      if isFocused then
        val caretChar = if truncated.nonEmpty then truncated.charAt(0) else ' '
        val caretStyle =
          placeholderStyle.copy(attributes = placeholderStyle.attributes + Attribute.Reverse)
        canvas.putChar(inner.x, inner.y, caretChar, caretStyle)
    else
      val end = math.min(v.length, scroll + innerWidth)
      val visible = if scroll < end then v.substring(scroll, end) else ""
      canvas.putText(inner.x, inner.y, visible, effectiveStyle)

      if isFocused then
        val caretX = inner.x + (caretPos - scroll)
        if caretX >= inner.x && caretX < inner.x + innerWidth then
          val charUnderCaret = if caretPos < v.length then v.charAt(caretPos) else ' '
          val caretStyle =
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
        if caretPos < current then caretPos
        else if caretPos >= current + innerWidth then caretPos - innerWidth + 1
        else current
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
   * `placeholder` renders whenever the buffer is empty, focused or not.
   * `maxLength` caps the value in grapheme clusters; `None` is unlimited.
   */
  def make(
    value: String = "",
    placeholder: String = "",
    onChange: String => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    style: CellStyle = CellStyle.Empty,
    border: BoxStyle = BoxStyle.Single,
    sides: Sides = Sides.all,
    padding: Insets = Insets.zero,
    enabled: Boolean = true,
    maxLength: Option[Int] = None
  ): UIO[TextInput] =
    ZIO.succeed(new TextInput(value, placeholder, style, border, sides, padding, enabled, maxLength, onChange))
