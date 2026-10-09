package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, BoxStyle, Canvas, Cell, CellStyle}
import event.EventResult
import event.SpecialKeyCode
import geometry.Rect
import glyph.{Graphemes, Widths}

import zio.UIO

import java.util.Locale
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

/**
 * The option list shared by [[Select]] and [[TagInput]]: its open and
 * filter state, highlight movement, the popup it draws, and the filter
 * text shown in the field. Each widget keeps its own selection and
 * decides what a row looks like.
 */
private[component] object OptionPopup:

  /** Default row shown when the filter matches no option. */
  val DefaultNoMatches: String = "No matches"

  /**
   * Widget-local list state, shared between a field and the rows its
   * popup draws.
   *
   *   - `open` — whether the list is showing
   *   - `filter` / `matches` — the typed text and the option indices it
   *     keeps, in order
   *   - `position` — the highlight, as a position in `matches`
   *   - `page` — option rows the list showed last frame, the `PgUp` /
   *     `PgDn` distance
   */
  final class Model(val options: Seq[String], initialPage: Int):
    private val all: Vector[Int] = options.indices.toVector

    val open = new AtomicBoolean(false)
    val filter = new AtomicReference[String]("")
    val matches = new AtomicReference[Vector[Int]](all)
    val position = new AtomicInteger(0)
    val page = new AtomicInteger(initialPage)

    /** The highlighted option, or `None` when no option matches the filter. */
    def highlighted: Option[Int] = matches.get().lift(position.get())

    /** Keep the options containing `text`, ignoring case; the highlight moves to the first. */
    def refilter(text: String): Unit =
      filter.set(text)
      matches.set(if text.isEmpty then all else all.filter(i => OptionPopup.contains(options(i), text)))
      position.set(0)

    /** Show the list unfiltered with `option` highlighted. */
    def openAt(option: Int): Unit =
      refilter("")
      position.set(math.max(0, math.min(all.size - 1, option)))
      open.set(true)

    /** Clear the filter, keeping the list open, with `option` highlighted. */
    def clearFilterAt(option: Int): Unit =
      refilter("")
      position.set(math.max(0, math.min(all.size - 1, option)))

    def close(): Unit =
      open.set(false)
      refilter("")

    /**
     * Move the highlight for a navigation key, clamped, or `None` for a
     * key that does not navigate. A move that cannot happen is still
     * consumed, so the key never reaches the shell behind an open list.
     */
    def navigate(code: SpecialKeyCode): Option[EventResult] =
      val current = position.get()
      val target = code match
        case SpecialKeyCode.Up   => Some(current - 1)
        case SpecialKeyCode.Down => Some(current + 1)
        case SpecialKeyCode.Home => Some(0)
        case SpecialKeyCode.End  => Some(matches.get().size - 1)
        case SpecialKeyCode.PgUp => Some(current - page.get())
        case SpecialKeyCode.PgDn => Some(current + page.get())
        case _                   => None
      target.map { t =>
        val size = matches.get().size
        val next = math.max(0, math.min(size - 1, t))
        if size == 0 || next == current then EventResult.Consumed
        else
          position.set(next)
          EventResult.RequestRedraw
      }

  /**
   * Build the popup content: a bordered list of the options the filter
   * keeps, scrolling through a `ScrollView` when they outgrow the
   * popup. `look(i)` styles option `i`'s row and `mark(i)` prefixes its
   * label (empty for none); the highlighted row adds `Reverse`.
   */
  def content(
    model: Model,
    style: CellStyle,
    border: BoxStyle,
    noMatches: String,
    look: Int => CellStyle,
    mark: Int => String
  ): UIO[Component] =
    val rows = model.options.indices.map(i => Row(i, model, look, mark)).toVector
    val muted = style.copy(attributes = style.attributes + Attribute.Dim)
    val column = OptionColumn(model, rows, Row(-1, model, _ => muted, _ => "", Some(noMatches)))
    ScrollView
      .make(column, style = style)
      .map(view => Panel(child = OptionList(model, column, view), border = border, style = style))

  /**
   * The popup for an open list anchored to `area`: at least the field's
   * width, wide enough for `rowWidth` cells of row text plus border, one
   * space each side and, when all options overflow `maxVisible`, the
   * scrollbar column. The width follows the full option list, so
   * filtering never makes it jump; the height follows the matches.
   */
  def popup(model: Model, content: Component, area: Rect, maxVisible: Int, rowWidth: Int): Popup =
    val rows = math.min(math.max(1, model.matches.get().size), maxVisible)
    val scrollbar = if model.options.size > maxVisible then 1 else 0
    Popup(content, area, math.max(area.width, rowWidth + 4 + scrollbar), rows + 2)

  /**
   * Draw `text` as typed filter input at (`x`, `y`) within `width`
   * cells: its tail when too long, then a `Reverse` caret cell.
   */
  def drawFilter(canvas: Canvas, x: Int, y: Int, width: Int, text: String, style: CellStyle): Unit =
    if width <= 0 then return
    val shown = tail(text, width - 1)
    canvas.putText(x, y, shown, style)
    val caret = style.copy(attributes = style.attributes + Attribute.Reverse)
    canvas.putChar(x + Widths.stringWidth(shown), y, ' ', caret)

  /** The longest suffix of `text`, in whole graphemes, at most `columns` cells wide. */
  def tail(text: String, columns: Int): String =
    val graphemes = Graphemes.toSeq(text)
    var start = graphemes.size
    var used = 0
    while start > 0 && used + Widths.cellsFor(graphemes(start - 1)) <= columns do
      start -= 1
      used += Widths.cellsFor(graphemes(start))
    graphemes.drop(start).mkString

  /** Case-insensitive substring match, independent of the default locale. */
  def contains(label: String, text: String): Boolean =
    label.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))

  /** Printable text: no Ctrl or Alt held, and not a control character. */
  def isPrintable(c: Char, mods: Set[event.KeyModifier]): Boolean =
    !mods.contains(event.KeyModifier.Ctrl) && !mods.contains(event.KeyModifier.Alt) && !c.isControl

  // ===== Internal =====

  /**
   * One row of the list: ` {mark} {label}`, styled by `look`, plus
   * `Reverse` when highlighted. Index `-1` is the "no matches" row,
   * whose text is `fixed`.
   */
  final private class Row(
    index: Int,
    model: Model,
    look: Int => CellStyle,
    mark: Int => String,
    fixed: Option[String] = None
  ) extends Component:
    override def preferredHeight(width: Int): Option[Int] = Some(1)

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val base = look(index)
      val rowStyle =
        if model.highlighted.contains(index) then base.copy(attributes = base.attributes + Attribute.Reverse)
        else base
      val prefix = mark(index)
      val label = fixed.getOrElse(model.options(index))
      val text = if prefix.isEmpty then label else s"$prefix $label"
      canvas.fillRect(area, Cell(' ', rowStyle))
      canvas.putText(area.x + 1, area.y, Widths.truncate(text, math.max(0, area.width - 2)), rowStyle)

  /** The rows of the options the filter keeps, one per line, or the "no matches" row. */
  final private class OptionColumn(model: Model, rows: Vector[Row], none: Row) extends Component:
    def count: Int = math.max(1, model.matches.get().size)

    override def preferredHeight(width: Int): Option[Int] = Some(count)

    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      val kept = model.matches.get()
      if kept.isEmpty then Seq(none -> Rect(area.x, area.y, area.width, 1))
      else kept.zipWithIndex.map((option, line) => rows(option) -> Rect(area.x, area.y + line, area.width, 1))

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      childLayouts(area).foreach((row, rect) => row.render(rect, canvas, ctx))

  /**
   * The rows inside the popup's border. When every row fits they are
   * laid out directly; otherwise through `view`, asked during layout to
   * keep the highlighted row in sight, so the scroll is a layout decision
   * and the layout result matches what is drawn.
   */
  final private class OptionList(model: Model, column: OptionColumn, view: ScrollView) extends Component:

    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      model.page.set(math.max(1, area.height))
      if column.count <= area.height then Seq(column -> area)
      else
        view.keepInView(model.position.get())
        Seq(view -> area)

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val clipped = canvas.clip(area)
      childLayouts(area).foreach((child, rect) => child.render(rect, clipped, ctx))
