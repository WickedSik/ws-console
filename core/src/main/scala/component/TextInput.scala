package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame, TextEdit}
import event.{Event, EventResult, KeyInput, Keymap, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}
import glyph.{Graphemes, Widths}

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
 * on caret-only keys, `EventResult.RequestRedraw`. Typed text inserts at
 * the caret; [[TextInput.DefaultKeymap]] binds the editing and motion
 * keys, and every unbound key bubbles (`Tab`, `Ctrl+Home`, `Ctrl+End`
 * among them).
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
  val keymap: Keymap[TextInput.Action],
  onChange: String => ZIO[Frame, IOException, Unit]
) extends Component:
  require(maxLength.forall(_ >= 0), s"maxLength must not be negative, got $maxLength")

  /** Disabled fields are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val editRef = AtomicReference[TextEdit](TextEdit.atEnd(initialValue))
  private val scrollRef = AtomicInteger(0)

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
      KeyInput.decode(event, keymap) match
        case KeyInput.Bound(TextInput.Action.Left)      => move(edit.left)
        case KeyInput.Bound(TextInput.Action.Right)     => move(edit.right)
        case KeyInput.Bound(TextInput.Action.WordLeft)  => move(edit.wordLeft)
        case KeyInput.Bound(TextInput.Action.WordRight) => move(edit.wordRight)
        case KeyInput.Bound(TextInput.Action.Home)      => move(edit.moveTo(0))
        case KeyInput.Bound(TextInput.Action.End)       => move(edit.moveTo(edit.value.length))
        case KeyInput.Bound(TextInput.Action.Backspace) => change(edit.backspace)
        case KeyInput.Bound(TextInput.Action.Delete)    => change(edit.delete)
        case KeyInput.Text(char)                        => change(Some(edit.insert(char.toString)))
        case KeyInput.Unbound(_) | KeyInput.NotKey      => EventResult.Ignored

  /**
   * Apply a value-changing edit and report it; `None` means nothing
   * changed. An edit past `maxLength` is refused but consumed, so the key
   * cannot bubble into an application shortcut.
   */
  private def change(next: Option[TextEdit]): EventResult =
    next match
      case None                                                        => EventResult.Ignored
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
    if area.nonEmpty && !undersizedForBorder(area) then
      val isFocused = ctx.focus.isFocused(this.id)
      val effectiveStyle =
        if !enabled then InteractionState.disabled(style)
        else if isFocused then InteractionState.focused(style)
        else style

      canvas.fillRect(area, Cell(' ', effectiveStyle))
      canvas.drawBox(area, border, sides, None, effectiveStyle)

      val inner = area.inner(sides.toInsets).inner(padding)
      if inner.nonEmpty then drawContent(canvas, inner, effectiveStyle, isFocused)

  /** The value or placeholder, scrolled to keep the caret in view, and the caret when focused. */
  private def drawContent(canvas: Canvas, inner: Rect, effectiveStyle: CellStyle, isFocused: Boolean): Unit =
    val edit = editRef.get()
    val v = edit.value
    val innerWidth = inner.width
    val caretStyle = effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Reverse)

    if v.isEmpty && placeholder.nonEmpty then
      scrollRef.set(0)
      val placeholderStyle =
        effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Dim)
      val shown = Widths.truncate(placeholder, innerWidth)
      canvas.putText(inner.x, inner.y, shown, placeholderStyle)

      if isFocused then
        // The caret covers the placeholder's whole first grapheme.
        val first = shown.substring(0, Graphemes.following(shown, 0))
        canvas.putText(
          inner.x,
          inner.y,
          if first.isEmpty then " " else first,
          placeholderStyle.copy(attributes = placeholderStyle.attributes + Attribute.Reverse)
        )
    else
      val scroll = adjustScroll(v, edit.caret, innerWidth)
      // Whole graphemes only: a wide one that would straddle the right edge is left out.
      canvas.putText(inner.x, inner.y, Widths.truncate(v.substring(scroll), innerWidth), effectiveStyle)

      if isFocused then
        val caretX = inner.x + Widths.stringWidth(v.substring(scroll, edit.caret))
        val under = caretCluster(v, edit.caret)
        if caretX + Widths.stringWidth(under) <= inner.x + innerWidth then
          canvas.putText(caretX, inner.y, under, caretStyle)

  /** The grapheme the caret covers, or a blank cell when it stands after the last one. */
  private def caretCluster(v: String, caret: Int): String =
    if caret < v.length then v.substring(caret, Graphemes.following(v, caret)) else " "

  /**
   * Slide the horizontal scroll — a char offset on a grapheme boundary —
   * so the caret's cell fits in `innerWidth` cells, then pull it back left
   * while the whole remaining text plus a caret cell still fits, so
   * trailing space never hides text that could be shown. Widths are
   * measured in cells, so a wide grapheme counts two. Mutates `scrollRef`
   * when the position changes; safe because scroll offset is widget-local
   * state.
   */
  private def adjustScroll(v: String, caret: Int, innerWidth: Int): Int =
    if innerWidth <= 0 then 0
    else
      val caretCells = Widths.stringWidth(caretCluster(v, caret))
      var scroll = Graphemes.floor(v, math.max(0, math.min(scrollRef.get(), v.length)))
      if caret < scroll then scroll = caret
      while scroll < caret && Widths.stringWidth(v.substring(scroll, caret)) + caretCells > innerWidth do
        scroll = Graphemes.following(v, scroll)
      while scroll > 0 && Widths.stringWidth(v.substring(Graphemes.preceding(v, scroll))) + 1 <= innerWidth do
        scroll = Graphemes.preceding(v, scroll)
      if scroll != scrollRef.get() then scrollRef.set(scroll)
      scroll

object TextInput:

  /** What a key can ask a text input to do. */
  enum Action:
    /** Move the caret one grapheme left. */
    case Left

    /** Move the caret one grapheme right. */
    case Right

    /** Move the caret to the start of the previous word. */
    case WordLeft

    /** Move the caret past the end of the next word. */
    case WordRight

    /** Move the caret to the start of the value. */
    case Home

    /** Move the caret to the end of the value. */
    case End

    /** Delete the grapheme before the caret. */
    case Backspace

    /** Delete the grapheme after the caret. */
    case Delete

  /**
   * The bare editing and motion keys, with `Alt+Left` / `Alt+Right` and
   * their macOS spellings `Alt+b` / `Alt+f` moving by word.
   */
  val DefaultKeymap: Keymap[Action] = Keymap(
    Keymap.plain(SpecialKeyCode.Left)      -> Action.Left,
    Keymap.plain(SpecialKeyCode.Right)     -> Action.Right,
    Keymap.alt(SpecialKeyCode.Left)        -> Action.WordLeft,
    Keymap.alt(SpecialKeyCode.Right)       -> Action.WordRight,
    Keymap.alt('b')                        -> Action.WordLeft,
    Keymap.alt('f')                        -> Action.WordRight,
    Keymap.plain(SpecialKeyCode.Home)      -> Action.Home,
    Keymap.plain(SpecialKeyCode.End)       -> Action.End,
    Keymap.plain(SpecialKeyCode.Backspace) -> Action.Backspace,
    Keymap.plain(SpecialKeyCode.Delete)    -> Action.Delete
  )

  /**
   * Construct a text input. `value` seeds the edit buffer; the widget
   * owns it thereafter and fires `onChange(newValue)` per edit.
   * `placeholder` renders whenever the buffer is empty, focused or not.
   * `maxLength` caps the value in grapheme clusters; `None` is unlimited.
   * `keymap` binds the editing and motion keys.
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
    maxLength: Option[Int] = None,
    keymap: Keymap[Action] = DefaultKeymap
  ): UIO[TextInput] =
    ZIO.succeed(TextInput(value, placeholder, style, border, sides, padding, enabled, maxLength, keymap, onChange))
