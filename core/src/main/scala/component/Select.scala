package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame, Widths}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Rect, Sides}
import layout.Constraint

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/**
 * Focusable single-choice dropdown.
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
 * The list is never a focus stop: the Select keeps focus while open and
 * consumes every key that has no `Ctrl`, so no shortcut behind it fires.
 * Keys with `Ctrl` bubble, which keeps `Ctrl+C` (or any custom quit
 * chord) and the `Ctrl`-arrow ScrollView keys reachable; the list moves
 * with the field. Losing focus closes the list.
 *
 * `onSelect` is bound at `make`. On each committed change the widget
 * updates its local index and returns `EventResult.Perform(onSelect(i))`.
 * The constructor `selected` seeds the index; the host observes changes
 * via `onSelect`. Re-selecting the current option fires nothing.
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
  model: Select.Model,
  list: Component,
  onSelect: Int => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled or empty selects are excluded from the focus cycle. */
  override val focusable: Boolean = enabled && options.nonEmpty

  /** The selected option index, or `None` when nothing is selected. */
  def selected: Option[Int] = model.selected

  /** Whether the option list is showing. */
  def isOpen: Boolean = model.open.get()

  /** The option the list highlights; meaningful while open. */
  def highlighted: Int = model.highlight.get()

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !focusable || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else if isOpen then whileOpen(event)
    else whileClosed(event)

  private def whileClosed(event: Event): EventResult =
    event match
      case SpecialKey(SpecialKeyCode.Enter | SpecialKeyCode.Down, m) if m.isEmpty => openList()
      case CharKey(' ', m) if m.isEmpty                                          => openList()
      case _                                                                     => EventResult.Ignored

  private def whileOpen(event: Event): EventResult =
    event match
      // Ctrl chords bubble: quit stays reachable and an enclosing view can still scroll.
      case SpecialKey(_, m) if m(KeyModifier.Ctrl) => EventResult.Ignored
      case CharKey(_, m) if m(KeyModifier.Ctrl)    => EventResult.Ignored
      case SpecialKey(SpecialKeyCode.Up, _)        => moveHighlight(highlighted - 1)
      case SpecialKey(SpecialKeyCode.Down, _)      => moveHighlight(highlighted + 1)
      case SpecialKey(SpecialKeyCode.Home, _)      => moveHighlight(0)
      case SpecialKey(SpecialKeyCode.End, _)       => moveHighlight(options.size - 1)
      case SpecialKey(SpecialKeyCode.PgUp, _)      => moveHighlight(highlighted - model.page.get())
      case SpecialKey(SpecialKeyCode.PgDn, _)      => moveHighlight(highlighted + model.page.get())
      case SpecialKey(SpecialKeyCode.Enter, _)     => commit()
      case SpecialKey(SpecialKeyCode.Escape, _) =>
        model.open.set(false)
        EventResult.RequestRedraw
      case SpecialKey(SpecialKeyCode.Tab, _) =>
        model.open.set(false)
        EventResult.Ignored
      case _ => EventResult.Consumed

  private def openList(): EventResult =
    model.highlight.set(selected.getOrElse(0))
    model.open.set(true)
    EventResult.RequestRedraw

  /** Move the highlight, clamped; a move that cannot happen is still consumed. */
  private def moveHighlight(target: Int): EventResult =
    val next = math.max(0, math.min(options.size - 1, target))
    if next == highlighted then EventResult.Consumed
    else
      model.highlight.set(next)
      EventResult.RequestRedraw

  private def commit(): EventResult =
    model.open.set(false)
    val choice = highlighted
    if selected.contains(choice) then EventResult.RequestRedraw
    else
      model.selectedRef.set(choice)
      EventResult.Perform(onSelect(choice))

  // ===== Popup =====

  /** Widest option label, in cells. */
  private lazy val longestLabel: Int =
    options.iterator.map(Widths.stringWidth).maxOption.getOrElse(0)

  /**
   * While open, the option list: at least the field's width, wide enough
   * for the longest label plus border, one space each side and, when
   * the options overflow `maxVisible`, the scrollbar column.
   */
  override def popups(area: Rect): Seq[Popup] =
    if !isOpen || !focusable then Seq.empty
    else
      val rows = math.min(options.size, maxVisible)
      val scrollbar = if options.size > rows then 1 else 0
      val width = math.max(area.width, longestLabel + 4 + scrollbar)
      Seq(Popup(list, area, width, rows + 2))

  // ===== Rendering =====

  /** One content row plus the top and bottom border. */
  override def preferredHeight(width: Int): Option[Int] = Some(3)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val isFocused = ctx.focus.isFocused(this.id)
    // Focus moved away while open (programmatically or by policy): close.
    if isOpen && !isFocused then model.open.set(false)
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
    if labelWidth > 0 then
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

  /**
   * Construct a select. `selected` seeds the chosen index (`None`, or an
   * index out of range, shows `placeholder`); the widget owns it
   * thereafter and fires `onSelect(index)` on every committed change.
   * `maxVisible` caps the option rows shown at once; must be positive.
   */
  def make(
    options: Seq[String],
    onSelect: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Option[Int] = None,
    placeholder: String = "",
    style: CellStyle = CellStyle.Empty,
    border: BoxStyle = BoxStyle.Single,
    enabled: Boolean = true,
    maxVisible: Int = DefaultMaxVisible
  ): UIO[Select] =
    require(maxVisible >= 1, s"maxVisible must be positive, got $maxVisible")
    val model = new Model(selected.filter(options.indices.contains).getOrElse(-1), maxVisible)
    val rows = options.zipWithIndex.map((label, i) => (Constraint.Fixed(1): Constraint) -> Row(i, label, model, style))
    val column = VBox(rows*)
    for view <- ScrollView.make(column, style = style)
    yield
      val list = Panel(child = OptionList(model, options.size, column, view), border = border, style = style)
      new Select(options, placeholder, style, border, enabled, maxVisible, model, list, onSelect)

  // ===== Internal =====

  /**
   * Widget-local list state, shared between the field and the rows it
   * draws in its popup. `selectedRef` holds `-1` for no selection; `page`
   * is the number of option rows the list showed last frame, the
   * `PgUp` / `PgDn` distance.
   */
  final private class Model(initialSelected: Int, initialPage: Int):
    val selectedRef = new AtomicInteger(initialSelected)
    val open = new AtomicBoolean(false)
    val highlight = new AtomicInteger(0)
    val page = new AtomicInteger(initialPage)

    def selected: Option[Int] = Option(selectedRef.get()).filter(_ >= 0)

  /** One option row: the highlighted one `Reverse`, the selected one `Bold`. */
  final private class Row(index: Int, label: String, model: Model, style: CellStyle) extends Component:
    override def preferredHeight(width: Int): Option[Int] = Some(1)

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val chosen = if model.selected.contains(index) then Set(Attribute.Bold) else Set.empty[Attribute]
      val highlighted = if model.highlight.get() == index then Set(Attribute.Reverse) else Set.empty[Attribute]
      val rowStyle = style.copy(attributes = style.attributes ++ chosen ++ highlighted)
      canvas.fillRect(area, Cell(' ', rowStyle))
      canvas.putText(area.x + 1, area.y, Widths.truncate(label, math.max(0, area.width - 2)), rowStyle)

  /**
   * The rows inside the popup's border. When every option fits they are
   * laid out directly; otherwise through `view`, scrolled each frame so
   * the highlighted row is visible.
   */
  final private class OptionList(model: Model, count: Int, column: Component, view: ScrollView) extends Component:

    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      if count <= area.height then Seq(column -> area) else Seq(view -> area)

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      model.page.set(math.max(1, area.height))
      childLayouts(area).foreach { (child, rect) =>
        if child eq view then
          // The row's rect at the current offset; the view scrolls the least that shows it.
          val row = Rect(rect.x, rect.y - view.offset + model.highlight.get(), rect.width, 1)
          view.reveal(row, rect)
        child.render(rect, canvas, ctx)
      }
