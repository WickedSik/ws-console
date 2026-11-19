package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle, Frame, TextEdit}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Rect, Sides}
import glyph.Widths

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Focusable multi-choice combobox: the chosen options render as chips in
 * the field, and a filtered list adds and removes them.
 *
 * Closed, it is one bordered row — the frame of [[Select]] — holding the
 * chosen options as chips, in the order they were added, each drawn as
 * ` label ` in its own `chipStyle`, one cell apart. Chips that do not fit
 * give way to a `+n` count of the rest, in `Dim`; when not even the first
 * chip fits whole, its label is cut by cells. With nothing chosen it
 * shows `placeholder` in `Dim`. A chevron sits at the right edge.
 *
 * Open, it draws the option list through the Layer 6 popup pass
 * (ADR-007), as [[Select]] does: below the field or above it, at least
 * the field's width, at most `maxVisible` rows, scrolling to keep the
 * highlight in view. Each row carries a checkbox mark, [[Checkbox]]'s
 * `☑` when its option is chosen and `☐` when not.
 *
 * Keys, while focused:
 *
 *   - Closed — `Enter`, `Space` or `Down` opens the list; a printable key
 *     opens it filtered by that key; `Backspace` removes the last chip.
 *   - Open — `Up` / `Down` / `Home` / `End` / `PgUp` / `PgDn` move the
 *     highlight; `Enter` adds the highlighted option, or removes it when
 *     already chosen, and keeps the list open with the filter cleared;
 *     printable keys filter the list by case-insensitive substring;
 *     `Backspace` deletes the last filter grapheme, or removes the last
 *     chip when the filter is empty; `Escape` closes; `Tab` closes and
 *     bubbles.
 *
 * `maxSelected` caps the chips. At the cap, unchosen rows render `Dim`
 * and `Enter` on one is refused (consumed, nothing changes); removing
 * stays allowed. [[remaining]] gives the host its "n remaining" count —
 * that text belongs to the host, not the field.
 *
 * As with [[Select]], the list is never a focus stop: the field keeps
 * focus while open and consumes every key without `Ctrl`, so shortcuts
 * behind it stay silent while `Ctrl` chords bubble. Losing focus closes
 * the list.
 *
 * `onChange` is bound at `make` and receives the chosen option indices,
 * in chip order, after every addition and removal. The constructor
 * `selected` seeds them; out-of-range and repeated indices are dropped
 * and the seed is not cut to `maxSelected`.
 */
final class TagInput private (
  val options: Seq[String],
  val placeholder: String,
  val style: CellStyle,
  val chipStyle: Int => CellStyle,
  val border: BoxStyle,
  val enabled: Boolean,
  val maxVisible: Int,
  val maxSelected: Option[Int],
  val noMatches: String,
  model: OptionPopup.Model,
  selectedRef: AtomicReference[Vector[Int]],
  list: Component,
  onChange: Seq[Int] => ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled or empty tag inputs are excluded from the focus cycle. */
  override val focusable: Boolean = enabled && options.nonEmpty

  /** The chosen option indices, in the order they were added. */
  def selected: Vector[Int] = selectedRef.get()

  /** Chips still allowed under `maxSelected`, or `None` when uncapped. */
  def remaining: Option[Int] = maxSelected.map(max => math.max(0, max - selected.size))

  /** Whether the option list is showing. */
  def isOpen: Boolean = model.open.get()

  /** The option the list highlights, or `None` when no option matches the filter. */
  def highlighted: Option[Int] = model.highlighted

  /** The filter text typed since the list opened. */
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
      case SpecialKey(SpecialKeyCode.Backspace, m) if m.isEmpty && selected.nonEmpty =>
        removeLast()
      case CharKey(c, m) if OptionPopup.isPrintable(c, m) =>
        val _ = openList()
        typeFilter(c.toString)
      case _ => EventResult.Ignored

  private def whileOpen(event: Event): EventResult =
    event match
      // Ctrl chords bubble: quit stays reachable and an enclosing view can still scroll.
      case SpecialKey(_, m) if m(KeyModifier.Ctrl) => EventResult.Ignored
      case CharKey(_, m) if m(KeyModifier.Ctrl)    => EventResult.Ignored
      case SpecialKey(SpecialKeyCode.Enter, _)     => toggle()
      case SpecialKey(SpecialKeyCode.Escape, _) =>
        model.close()
        EventResult.RequestRedraw
      case SpecialKey(SpecialKeyCode.Tab, _) =>
        model.close()
        EventResult.Ignored
      case SpecialKey(SpecialKeyCode.Backspace, _) =>
        TextEdit.atEnd(filter).backspace match
          case Some(edit)                => typeFilter(edit.value)
          case None if selected.nonEmpty => removeLast()
          case None                      => EventResult.Consumed
      case SpecialKey(code, _)                            => model.navigate(code).getOrElse(EventResult.Consumed)
      case CharKey(c, m) if OptionPopup.isPrintable(c, m) => typeFilter(filter + c)
      case _                                              => EventResult.Consumed

  private def openList(): EventResult =
    model.openAt(0)
    EventResult.RequestRedraw

  private def typeFilter(text: String): EventResult =
    model.refilter(text)
    EventResult.RequestRedraw

  private def atLimit: Boolean = maxSelected.exists(selected.size >= _)

  /** Add the highlighted option, or remove it when chosen; clear the filter and keep it highlighted. */
  private def toggle(): EventResult =
    highlighted match
      case None => EventResult.Consumed
      case Some(option) if selected.contains(option) =>
        model.clearFilterAt(option)
        change(selected.filterNot(_ == option))
      case Some(_) if atLimit => EventResult.Consumed
      case Some(option) =>
        model.clearFilterAt(option)
        change(selected :+ option)

  private def removeLast(): EventResult = change(selected.dropRight(1))

  private def change(next: Vector[Int]): EventResult =
    selectedRef.set(next)
    EventResult.Perform(onChange(next))

  // ===== Popup =====

  /** Widest row text in cells: mark, space and the longest label, or `noMatches`. */
  private lazy val longestRow: Int =
    val labels = options.iterator.map(Widths.stringWidth).maxOption.getOrElse(0)
    val mark = math.max(Widths.stringWidth(TagInput.Marks.on), Widths.stringWidth(TagInput.Marks.off))
    math.max(mark + 1 + labels, Widths.stringWidth(noMatches))

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
    if area.width >= 2 && area.height >= 2 then
      val effective =
        if !enabled then InteractionState.disabled(style)
        else if isFocused then InteractionState.focused(style)
        else style

      canvas.fillRect(area, Cell(' ', effective))
      canvas.drawBox(area, border, Sides.all, None, effective)
      drawInner(canvas, area.inner(1), effective)

  /** The chevron, then the filter, the placeholder or the chips before it. */
  private def drawInner(canvas: Canvas, inner: Rect, effective: CellStyle): Unit =
    if inner.nonEmpty then
      val chevron = if isOpen then Select.OpenChevron else Select.ClosedChevron
      canvas.putText(inner.x + inner.width - 1, inner.y, chevron, effective)

      // The chips keep one blank cell before the chevron.
      val width = inner.width - 2
      if width > 0 then
        val muted = effective.copy(attributes = effective.attributes + Attribute.Dim)
        if isOpen && filter.nonEmpty then OptionPopup.drawFilter(canvas, inner.x, inner.y, width, filter, effective)
        else if selected.isEmpty then
          if placeholder.nonEmpty then canvas.putText(inner.x, inner.y, Widths.truncate(placeholder, width), muted)
        else drawChips(canvas, inner.x, inner.y, width, muted)

  /** The chips that fit in `width` cells, then `+n` for the rest. */
  private def drawChips(canvas: Canvas, x: Int, y: Int, width: Int, countStyle: CellStyle): Unit =
    val chosen = selected
    val labels = chosen.map(options)
    TagInput.layoutChips(labels.map(Widths.stringWidth), width) match
      case TagInput.ChipLayout(shown, cut, rest) =>
        var cx = x
        chosen.take(shown).zip(labels).zipWithIndex.foreach { case ((option, label), i) =>
          val text = if i == shown - 1 then cut.fold(label)(Widths.truncate(label, _)) else label
          val chip = s" $text "
          val chipStyleNow = if enabled then chipStyle(option) else InteractionState.disabled(chipStyle(option))
          canvas.putText(cx, y, chip, chipStyleNow)
          cx += Widths.stringWidth(chip) + 1
        }
        if rest > 0 then canvas.putText(cx, y, s"+$rest", countStyle)

object TagInput:

  /** List-row marks, `(chosen, not chosen)`: the checkbox pair, a multi-choice list being a list of checkboxes. */
  val Marks: (on: String, off: String) = Checkbox.DefaultMarks

  /**
   * How chips fill a row: the first `shown` chips are drawn, the last of
   * them with its label cut to `cut` cells when set, followed by `+rest`.
   */
  final private[component] case class ChipLayout(shown: Int, cut: Option[Int], rest: Int)

  /**
   * Fit chips with label widths `widths` into `width` cells. A chip is
   * its label plus a space either side; chips sit one cell apart, and
   * `+n` follows one cell after the last chip shown. Shows as many whole
   * chips as fit beside the count; when none does, cuts the first label
   * to fit beside it, gives only the count when not even one label cell
   * fits, and nothing when the count does not fit either.
   */
  private[component] def layoutChips(widths: Seq[Int], width: Int): ChipLayout =
    val total = widths.size
    def countWidth(rest: Int): Int = if rest == 0 then 0 else s"+$rest".length
    /** Cells for the first `k` whole chips and the count after them. */
    def needed(k: Int): Int =
      val chips = widths.take(k).map(_ + 2).sum + math.max(0, k - 1)
      val rest = total - k
      chips + (if rest > 0 then (if k > 0 then 1 else 0) + countWidth(rest) else 0)
    (total to 1 by -1).find(k => needed(k) <= width) match
      case Some(k)            => ChipLayout(k, None, total - k)
      case None if total == 0 => ChipLayout(0, None, 0)
      case None =>
        val rest = total - 1
        val room = width - 2 - (if rest > 0 then 1 + countWidth(rest) else 0)
        if room >= 1 then ChipLayout(1, Some(room), rest)
        else if countWidth(total) <= width then ChipLayout(0, None, total)
        else ChipLayout(0, None, 0)

  /**
   * Construct a tag input. `selected` seeds the chosen option indices in
   * chip order; the widget owns them thereafter and fires `onChange` with
   * the full list on every addition and removal. `chipStyle(i)` styles
   * option `i`'s chip, `Reverse` on `style` by default. `maxSelected`, when
   * set, caps the chips; `maxVisible` caps the rows the list shows at
   * once. Both must be positive.
   */
  def make(
    options: Seq[String],
    onChange: Seq[Int] => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Seq[Int] = Seq.empty,
    placeholder: String = "",
    style: CellStyle = CellStyle.Empty,
    chipStyle: Option[Int => CellStyle] = None,
    border: BoxStyle = BoxStyle.Single,
    enabled: Boolean = true,
    maxVisible: Int = Select.DefaultMaxVisible,
    maxSelected: Option[Int] = None,
    noMatches: String = OptionPopup.DefaultNoMatches
  ): UIO[TagInput] =
    require(maxVisible >= 1, s"maxVisible must be positive, got $maxVisible")
    require(maxSelected.forall(_ >= 1), s"maxSelected must be positive, got $maxSelected")
    val model = OptionPopup.Model(options, maxVisible)
    val selectedRef = AtomicReference(selected.filter(options.indices.contains).distinct.toVector)
    val chips = chipStyle.getOrElse((_: Int) => style.copy(attributes = style.attributes + Attribute.Reverse))
    val bold = style.copy(attributes = style.attributes + Attribute.Bold)
    val muted = style.copy(attributes = style.attributes + Attribute.Dim)
    // Chosen rows bold; at the cap, the rows that can no longer be added are muted.
    val look = (i: Int) =>
      val chosen = selectedRef.get()
      if chosen.contains(i) then bold
      else if maxSelected.exists(chosen.size >= _) then muted
      else style
    val mark = (i: Int) => if selectedRef.get().contains(i) then Marks.on else Marks.off
    for list <- OptionPopup.content(model, style, border, noMatches, look, mark)
    yield TagInput(
      options,
      placeholder,
      style,
      chips,
      border,
      enabled,
      maxVisible,
      maxSelected,
      noMatches,
      model,
      selectedRef,
      list,
      onChange
    )
