package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame, TextEdit}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Rect, Sides}
import glyph.Widths

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Focusable single-choice dropdown, optionally filtered by typing (a
 * combobox).
 *
 * Closed, it is one bordered row — the same frame as [[TextInput]] —
 * showing the selected option's label, or `placeholder` in `Dim` when
 * nothing is selected, with a chevron at the right edge. Labels truncate
 * by cells, never splitting a wide grapheme.
 *
 * Open, it draws a bordered list of the options below the field, or
 * above it when only that side has room, through the Layer 6 popup pass
 * (ADR-007): an enclosing `ScrollView` does not clip it and later
 * siblings do not cover it. The list is at least as wide as the field
 * and at most `maxVisible` options tall; longer lists scroll, with a
 * scrollbar, to keep the highlighted option in view.
 *
 * Keys, while focused:
 *
 *   - Closed — `Enter`, `Space` or `Down` opens the list with the
 *     selected option highlighted (the first when none is).
 *   - Open — `Up` / `Down` / `Home` / `End` / `PgUp` / `PgDn` move the
 *     highlight without wrapping; `Enter` selects it and closes;
 *     `Escape` closes and keeps the selection; `Tab` closes and keeps the
 *     selection, then bubbles so focus moves on as usual.
 *
 * With `filterable = true`, typing filters the list: printable keys
 * build a filter text shown in the field, `Backspace` deletes its last
 * grapheme, and the list keeps the options whose label contains the text,
 * ignoring case, with the highlight on the first of them. When none
 * does, the list shows `noMatches` in `Dim` and `Enter` does nothing.
 * A printable key on the closed field opens the list with that key as
 * the filter. Closing, by any key, clears the filter. `Space` is text
 * while a filterable list is open.
 *
 * The list is never a focus stop: the Select keeps focus while open and
 * consumes every key that has no `Ctrl`, so no shortcut behind it fires.
 * Keys with `Ctrl` bubble, which keeps `Ctrl+C` (or any custom quit
 * chord) and the `Ctrl`-arrow ScrollView keys reachable; the list moves
 * with the field. Losing focus closes the list.
 *
 * `onSelect` is bound at `make`. On each committed change the widget
 * updates its local index and returns `EventResult.Perform(onSelect(i))`
 * with the index into `options`. The constructor `selected` seeds the
 * index; the host observes changes via `onSelect`. Re-selecting the
 * current option fires nothing.
 *
 * Interaction states — `normal`, `focused` (`Bold`), `disabled` (`Dim`,
 * out of the focus cycle), plus `open` (the chevron points up). In the
 * list, the highlighted option is `Reverse` and the selected one `Bold`.
 */
final class Select private (
  val options: Seq[String],
  val placeholder: String,
  val style: CellStyle,
  val border: BoxStyle,
  val enabled: Boolean,
  val maxVisible: Int,
  val filterable: Boolean,
  val noMatches: String,
  model: OptionPopup.Model,
  selectedRef: AtomicInteger,
  list: Component,
  onSelect: Int => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled or empty selects are excluded from the focus cycle. */
  override val focusable: Boolean = enabled && options.nonEmpty

  /** The selected option index, or `None` when nothing is selected. */
  def selected: Option[Int] = Select.selectedOf(selectedRef)

  /** Whether the option list is showing. */
  def isOpen: Boolean = model.open.get()

  /** The option the list highlights, or `None` when no option matches the filter. */
  def highlighted: Option[Int] = model.highlighted

  /** The filter text typed since the list opened; empty when not filtering. */
  def filter: String = model.filter.get()

  /** The options the list shows, in order: all of them unless a filter narrows them. */
  def matches: Vector[Int] = model.matches.get()

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !focusable || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else if isOpen then whileOpen(event)
    else whileClosed(event)

  private def whileClosed(event: Event): EventResult =
    event match
      case SpecialKey(SpecialKeyCode.Enter | SpecialKeyCode.Down, m) if m.isEmpty => openList()
      case CharKey(' ', m) if m.isEmpty                                           => openList()
      case CharKey(c, m) if filterable && OptionPopup.isPrintable(c, m) =>
        openList()
        typeFilter(c.toString)
      case _ => EventResult.Ignored

  private def whileOpen(event: Event): EventResult =
    event match
      // Ctrl chords bubble: quit stays reachable and an enclosing view can still scroll.
      case SpecialKey(_, m) if m(KeyModifier.Ctrl) => EventResult.Ignored
      case CharKey(_, m) if m(KeyModifier.Ctrl)    => EventResult.Ignored
      case SpecialKey(SpecialKeyCode.Enter, _)     => commit()
      case SpecialKey(SpecialKeyCode.Escape, _) =>
        model.close()
        EventResult.RequestRedraw
      case SpecialKey(SpecialKeyCode.Tab, _) =>
        model.close()
        EventResult.Ignored
      case SpecialKey(SpecialKeyCode.Backspace, _) if filterable =>
        TextEdit.atEnd(filter).backspace match
          case Some(edit) => typeFilter(edit.value)
          case None       => EventResult.Consumed
      case SpecialKey(code, _) => model.navigate(code).getOrElse(EventResult.Consumed)
      case CharKey(c, m) if filterable && OptionPopup.isPrintable(c, m) => typeFilter(filter + c)
      case _                                                            => EventResult.Consumed

  private def openList(): EventResult =
    model.openAt(selected.getOrElse(0))
    EventResult.RequestRedraw

  private def typeFilter(text: String): EventResult =
    model.refilter(text)
    EventResult.RequestRedraw

  private def commit(): EventResult =
    highlighted match
      case None => EventResult.Consumed
      case Some(choice) =>
        model.close()
        if selected.contains(choice) then EventResult.RequestRedraw
        else
          selectedRef.set(choice)
          EventResult.Perform(onSelect(choice))

  // ===== Popup =====

  /** Widest row text in cells: the longest label, or `noMatches` when filtering. */
  private lazy val longestRow: Int =
    val labels = options.iterator.map(Widths.stringWidth).maxOption.getOrElse(0)
    if filterable then math.max(labels, Widths.stringWidth(noMatches)) else labels

  /** While open, the option list (see [[OptionPopup.popup]] for its size). */
  override def popups(area: Rect): Seq[Popup] =
    if !isOpen || !focusable then Seq.empty
    else Seq(OptionPopup.popup(model, list, area, maxVisible, longestRow))

  // ===== Rendering =====

  /** One content row plus the top and bottom border. */
  override def preferredHeight(width: Int): Option[Int] = Some(3)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val isFocused = ctx.focus.isFocused(this.id)
    // Focus moved away while open (programmatically or by policy): close.
    if isOpen && !isFocused then model.close()
    if area.width < 2 || area.height < 2 then return

    val effective =
      if !enabled then InteractionState.disabled(style)
      else if isFocused then InteractionState.focused(style)
      else style

    canvas.fillRect(area, Cell(' ', effective))
    canvas.drawBox(area, border, Sides.all, None, effective)

    val inner = area.inner(1)
    if inner.isEmpty then return

    val chevron = if isOpen then Select.OpenChevron else Select.ClosedChevron
    canvas.putText(inner.x + inner.width - 1, inner.y, chevron, effective)

    // The label keeps one blank cell before the chevron.
    val labelWidth = inner.width - 2
    if labelWidth <= 0 then return
    if isOpen && filter.nonEmpty then OptionPopup.drawFilter(canvas, inner.x, inner.y, labelWidth, filter, effective)
    else
      selected match
        case Some(i) =>
          canvas.putText(inner.x, inner.y, Widths.truncate(options(i), labelWidth), effective)
        case None if placeholder.nonEmpty =>
          val muted = effective.copy(attributes = effective.attributes + Attribute.Dim)
          canvas.putText(inner.x, inner.y, Widths.truncate(placeholder, labelWidth), muted)
        case None => ()

object Select:

  /** Chevron while closed: the list opens downward. */
  val ClosedChevron: String = "▾"

  /** Chevron while open. */
  val OpenChevron: String = "▴"

  /** Default cap on the option rows the list shows at once. */
  val DefaultMaxVisible: Int = 8

  /** Default row shown when the filter matches no option. */
  val DefaultNoMatches: String = OptionPopup.DefaultNoMatches

  /**
   * Construct a select. `selected` seeds the chosen index (`None`, or an
   * index out of range, shows `placeholder`); the widget owns it
   * thereafter and fires `onSelect(index)` on every committed change.
   * `maxVisible` caps the option rows shown at once; must be positive.
   * `filterable` turns on filtering by typing; `noMatches` is the row
   * shown when the filter matches nothing.
   */
  def make(
    options: Seq[String],
    onSelect: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Option[Int] = None,
    placeholder: String = "",
    style: CellStyle = CellStyle.Empty,
    border: BoxStyle = BoxStyle.Single,
    enabled: Boolean = true,
    maxVisible: Int = DefaultMaxVisible,
    filterable: Boolean = false,
    noMatches: String = DefaultNoMatches
  ): UIO[Select] =
    require(maxVisible >= 1, s"maxVisible must be positive, got $maxVisible")
    val model = new OptionPopup.Model(options, maxVisible)
    // -1 for no selection.
    val selectedRef = new AtomicInteger(selected.filter(options.indices.contains).getOrElse(-1))
    // The selected option's row is bold.
    val bold = style.copy(attributes = style.attributes + Attribute.Bold)
    val look = (i: Int) => if selectedOf(selectedRef).contains(i) then bold else style
    for list <- OptionPopup.content(model, style, border, noMatches, look, _ => "")
    yield new Select(
      options,
      placeholder,
      style,
      border,
      enabled,
      maxVisible,
      filterable,
      noMatches,
      model,
      selectedRef,
      list,
      onSelect
    )

  private def selectedOf(ref: AtomicInteger): Option[Int] = Option(ref.get()).filter(_ >= 0)
