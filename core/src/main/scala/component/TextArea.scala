package io.github.wickedsik.wsconsole
package component

import buffer.{
  Attribute,
  BoxStyle,
  Canvas,
  Cell,
  CellStyle,
  Frame,
  Span,
  StyledLine,
  TextEdit,
  TextWrap,
  Widths,
  WrappedLine
}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * Focusable multi-line editable field with soft wrap.
 *
 * The constructor `value` seeds the edit buffer, which may hold `\n`; the
 * widget owns it thereafter and fires `onChange(newValue)` per edit, as
 * [[TextInput]] does. Editing runs through [[buffer.TextEdit]], so every
 * edit and caret step is grapheme-safe.
 *
 * Content soft-wraps at the inner width less one column
 * ([[buffer.TextWrap.wrap]]). The last column is always reserved for the
 * caret, so a caret after a full line has a cell to stand on and the text
 * never reflows when focus arrives. Soft breaks never alter `value`.
 *
 * The field shows `rows` visual lines and scrolls internally to keep the
 * caret visible; its preferred height is `rows` plus border rows and
 * vertical padding. It assumes it fits inside any enclosing `ScrollView`:
 * reveal-on-focus shows the whole field, and with it the caret.
 *
 * Keys while focused:
 *
 *   - printable text inserts at the caret; `Enter` inserts `\n`
 *   - `Backspace` / `Delete` remove a grapheme, joining lines at the edges
 *   - `Left` / `Right` step a grapheme, crossing line ends; whitespace
 *     dropped at a soft break is stepped over in one move
 *   - `Up` / `Down` move by visual line, keeping the preferred column; on
 *     the first / last line they are `Ignored` so an enclosing view or the
 *     host can use them
 *   - `Home` / `End` go to the start / end of the visual line
 *   - `PgUp` / `PgDn` move by `rows` visual lines
 *   - `Alt+Left` / `Alt+Right` (`Alt+b` / `Alt+f`) move by word
 *
 * `Tab`, `Ctrl+Home` and `Ctrl+End` are never consumed: they bubble to the
 * focus cycle and the enclosing view.
 *
 * Interaction states — `normal`, `focused` (caret shown), `disabled` —
 * derive from focus and the `enabled` flag, as for [[TextInput]]. The
 * placeholder renders `Dim` and wrapped whenever the buffer is empty;
 * when focused, the caret overlays its first cell. `grip` replaces the
 * bottom-right border corner when both the bottom and right sides are
 * drawn; it is decoration only.
 *
 * `maxLength`, when set, caps the value in grapheme clusters
 * ([[buffer.Graphemes.count]]); a line break counts as one. Input that
 * would grow the value past it is refused and consumed; a paste, which
 * arrives key by key, stops at the limit. A seeded `value` over the limit
 * is shown whole and can only shrink.
 *
 * Widget-local state: the edit buffer and caret, the preferred column of
 * a run of vertical moves, which of two lines a caret on their shared
 * boundary belongs to, the internal scroll row, and the geometry of the
 * last render (needed by `handleEvent`, which receives no area).
 */
final class TextArea private (
  initialValue: String,
  val placeholder: String,
  val rows: Int,
  val style: CellStyle,
  val border: BoxStyle,
  val sides: Sides,
  val padding: Insets,
  val enabled: Boolean,
  val grip: Option[Char],
  val maxLength: Option[Int],
  onChange: String => ZIO[Frame, IOException, Unit]
) extends Component:
  require(rows >= 1, s"rows must be at least 1, got $rows")
  require(maxLength.forall(_ >= 0), s"maxLength must not be negative, got $maxLength")

  import TextArea.*

  /** Disabled fields are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  private val editRef = new AtomicReference[TextEdit](TextEdit.atEnd(initialValue))
  private val navRef = new AtomicReference[Nav](Nav.free)
  private val scrollRef = new AtomicInteger(0)
  private val geometryRef = new AtomicReference[Geometry](Geometry.empty)

  /** Current edit-buffer contents. Reflects every edit through `onChange`. */
  def value: String = editRef.get().value

  /** Current caret position, a char offset on a grapheme boundary in `[0, value.length]`. */
  def caret: Int = editRef.get().caret

  /** First visual line shown, as of the last render. */
  def scrollRow: Int = scrollRef.get()

  /**
   * The caret's visual line and cell column, both zero-based, at the wrap
   * width of the last render. Before the first render, lines are the
   * logical (`\n`-separated) ones.
   */
  def caretPosition: Position =
    val edit = editRef.get()
    val lines = layout(edit.value, geometryRef.get().wrapWidth)
    val row = rowOf(lines, edit.caret, navRef.get().upstream)
    Position(row, columnOf(edit.value, lines(row), edit.caret))

  /**
   * Restore the buffer to `initialValue` with the caret at its end, and
   * clear the scroll. Does not fire `onChange`.
   */
  def reset(): Unit =
    editRef.set(TextEdit.atEnd(initialValue))
    navRef.set(Nav.free)
    scrollRef.set(0)

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      val edit = editRef.get()
      event match
        // word motion
        case CharKey('b', m) if m(KeyModifier.Alt)                     => move(edit.wordLeft) // macOS
        case CharKey('f', m) if m(KeyModifier.Alt)                     => move(edit.wordRight)
        case SpecialKey(SpecialKeyCode.Left, m) if m(KeyModifier.Alt)  => move(edit.wordLeft)
        case SpecialKey(SpecialKeyCode.Right, m) if m(KeyModifier.Alt) => move(edit.wordRight)
        // typing
        case CharKey(c, mods) if isPrintable(c, mods)         => change(Some(edit.insert(c.toString)))
        case SpecialKey(SpecialKeyCode.Enter, m) if m.isEmpty => change(Some(edit.insert("\n")))
        case SpecialKey(SpecialKeyCode.Backspace, _)          => change(edit.backspace)
        case SpecialKey(SpecialKeyCode.Delete, _)             => change(edit.delete)
        // motion; Ctrl+Home / Ctrl+End belong to the enclosing view
        case SpecialKey(code, m) if m.isEmpty =>
          code match
            case SpecialKeyCode.Left  => stepLeft(edit)
            case SpecialKeyCode.Right => stepRight(edit)
            case SpecialKeyCode.Up    => vertical(edit, -1, clampAtEnds = false)
            case SpecialKeyCode.Down  => vertical(edit, 1, clampAtEnds = false)
            case SpecialKeyCode.PgUp  => vertical(edit, -rows, clampAtEnds = true)
            case SpecialKeyCode.PgDn  => vertical(edit, rows, clampAtEnds = true)
            case SpecialKeyCode.Home  => lineEdge(edit, toEnd = false)
            case SpecialKeyCode.End   => lineEdge(edit, toEnd = true)
            case _                    => EventResult.Ignored
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
      case None                                                        => EventResult.Ignored
      case Some(e) if !TextEdit.withinLimit(value, e.value, maxLength) => EventResult.Consumed
      case Some(e) =>
        editRef.set(e)
        navRef.set(Nav.free)
        EventResult.Perform(onChange(e.value))

  /** Apply a caret move that forgets the preferred column. */
  private def move(next: Option[TextEdit]): EventResult =
    next match
      case None => EventResult.Ignored
      case Some(e) =>
        editRef.set(e)
        navRef.set(Nav.free)
        EventResult.RequestRedraw

  /** Place the caret with explicit navigation state; `Ignored` when nothing changes. */
  private def place(edit: TextEdit, offset: Int, nav: Nav): EventResult =
    val next = edit.moveTo(offset).getOrElse(edit)
    if next == edit && nav == navRef.get() then EventResult.Ignored
    else
      editRef.set(next)
      navRef.set(nav)
      EventResult.RequestRedraw

  /** The current wrapped lines and the caret's line, at the last render's width. */
  private def located(edit: TextEdit): (Vector[WrappedLine], Int) =
    val lines = layout(edit.value, geometryRef.get().wrapWidth)
    (lines, rowOf(lines, edit.caret, navRef.get().upstream))

  private def stepLeft(edit: TextEdit): EventResult =
    val (lines, row) = located(edit)
    val line = lines(row)
    if edit.caret > line.end then place(edit, line.end, Nav.free) // inside a dropped gap
    else if edit.caret == line.start && row > 0 && isSoftGap(lines, row - 1) then
      place(edit, lines(row - 1).end, Nav(None, upstream = true))
    else move(edit.left)

  private def stepRight(edit: TextEdit): EventResult =
    val (lines, row) = located(edit)
    val line = lines(row)
    val hasNext = row + 1 < lines.size
    if hasNext && navRef.get().upstream && edit.caret == lines(row + 1).start then
      place(edit, edit.caret, Nav.free) // end of a mid-word break: drop to the next line
    else if hasNext && edit.caret >= line.end && isSoftGap(lines, row) then
      place(edit, lines(row + 1).start, Nav.free)
    else move(edit.right)

  private def vertical(edit: TextEdit, delta: Int, clampAtEnds: Boolean): EventResult =
    if geometryRef.get().wrapWidth <= 0 then EventResult.Ignored
    else
      val (lines, row) = located(edit)
      val target =
        if clampAtEnds then math.max(0, math.min(lines.size - 1, row + delta))
        else row + delta
      if target < 0 || target >= lines.size || target == row then EventResult.Ignored
      else
        val column = navRef.get().column.getOrElse(columnOf(edit.value, lines(row), edit.caret))
        val line = lines(target)
        val offset = line.start + Widths.truncate(edit.value.substring(line.start, line.end), column).length
        if clampAtEnds then
          val viewRows = geometryRef.get().viewRows
          scrollRef.set(math.max(0, scrollRef.get() + (target - row).sign * viewRows))
        place(edit, offset, Nav(Some(column), upstream = offset == line.end))

  private def lineEdge(edit: TextEdit, toEnd: Boolean): EventResult =
    val (lines, row) = located(edit)
    val line = lines(row)
    if toEnd then place(edit, line.end, Nav(None, upstream = true))
    else place(edit, line.start, Nav.free)

  // ===== Rendering =====

  private def undersizedForBorder(area: Rect): Boolean =
    val insets = sides.toInsets
    area.width < insets.left + insets.right || area.height < insets.top + insets.bottom

  /** `rows` content rows plus border rows and vertical padding. */
  override def preferredHeight(width: Int): Option[Int] =
    val chrome = sides.toInsets
    Some(rows + chrome.top + chrome.bottom + padding.top + padding.bottom)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || undersizedForBorder(area) then return

    val isFocused = enabled && ctx.focus.isFocused(this.id)
    val effectiveStyle =
      if !enabled then InteractionState.disabled(style)
      else if isFocused then InteractionState.focused(style)
      else style

    canvas.fillRect(area, Cell(' ', effectiveStyle))
    canvas.drawBox(area, border, sides, None, effectiveStyle)
    grip.foreach { g =>
      if sides.bottom && sides.right then
        canvas.putChar(area.x + area.width - 1, area.y + area.height - 1, g, effectiveStyle)
    }

    val inner = area.inner(sides.toInsets).inner(padding)
    if inner.isEmpty then return

    val wrapWidth = math.max(1, inner.width - 1)
    geometryRef.set(Geometry(wrapWidth, inner.height))
    val caretStyle = effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Reverse)
    val edit = editRef.get()

    if edit.value.isEmpty then
      scrollRef.set(0)
      val placeholderStyle = effectiveStyle.copy(attributes = effectiveStyle.attributes + Attribute.Dim)
      val lines = TextWrap.wrap(StyledLine(Vector(Span(placeholder))), wrapWidth)
      lines.take(inner.height).zipWithIndex.foreach { (l, row) =>
        l.line.over(placeholderStyle).draw(canvas, inner.x, inner.y + row)
      }
      if isFocused then
        val first = lines.headOption.map(_.line.text).getOrElse("")
        val cluster = first.substring(0, TextEdit(first, 0).right.fold(0)(_.caret))
        val shown = if cluster.isEmpty then " " else cluster
        canvas.putText(
          inner.x,
          inner.y,
          shown,
          placeholderStyle.copy(attributes = placeholderStyle.attributes + Attribute.Reverse)
        )
    else
      val lines = layout(edit.value, wrapWidth)
      val caretRow = rowOf(lines, edit.caret, navRef.get().upstream)
      val scroll = adjustScroll(caretRow, inner.height, lines.size)
      lines.slice(scroll, scroll + inner.height).zipWithIndex.foreach { (l, row) =>
        l.line.over(effectiveStyle).draw(canvas, inner.x, inner.y + row)
      }
      if isFocused then
        val line = lines(caretRow)
        val x = inner.x + columnOf(edit.value, line, edit.caret)
        val y = inner.y + caretRow - scroll
        val under =
          if edit.caret < line.end then
            val cluster = edit.value.substring(edit.caret, edit.right.fold(edit.value.length)(_.caret))
            if cluster == "\t" then " " else cluster
          else " "
        canvas.putText(x, y, under, caretStyle)

  /**
   * Slide the internal scroll so the caret row is within
   * `[scroll, scroll + viewRows)`, clamped so no blank rows show below the
   * last line. Mutates `scrollRef`; widget-local state.
   */
  private def adjustScroll(caretRow: Int, viewRows: Int, lineCount: Int): Int =
    val current = scrollRef.get()
    val naive =
      if caretRow < current then caretRow
      else if caretRow >= current + viewRows then caretRow - viewRows + 1
      else current
    val clamped = math.max(0, math.min(math.max(0, lineCount - viewRows), naive))
    if clamped != current then scrollRef.set(clamped)
    clamped

object TextArea:

  /** A caret position: zero-based visual line and cell column. */
  final case class Position(line: Int, column: Int)

  /** Default resize-grip glyph: U+25E2, black lower right triangle (narrow). */
  val GripGlyph: Char = '◢'

  /**
   * Construct a text area. `value` seeds the edit buffer; the widget owns
   * it thereafter and fires `onChange(newValue)` per edit. `rows` is the
   * number of visible lines. `grip` draws a decorative glyph in the
   * bottom-right border corner; pass `Some(TextArea.GripGlyph)` for the
   * default one. `maxLength` caps the value in grapheme clusters; `None`
   * is unlimited.
   */
  def make(
    value: String = "",
    placeholder: String = "",
    rows: Int = 3,
    onChange: String => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    style: CellStyle = CellStyle.Empty,
    border: BoxStyle = BoxStyle.Single,
    sides: Sides = Sides.all,
    padding: Insets = Insets.zero,
    enabled: Boolean = true,
    grip: Option[Char] = None,
    maxLength: Option[Int] = None
  ): UIO[TextArea] =
    ZIO.succeed(
      new TextArea(value, placeholder, rows, style, border, sides, padding, enabled, grip, maxLength, onChange)
    )

  /**
   * Navigation state between keys. `column` is the preferred cell column
   * held across a run of vertical moves. `upstream` places a caret that
   * sits on a boundary shared by two lines (a word broken mid-way) at the
   * end of the upper line instead of the start of the lower one.
   */
  final private case class Nav(column: Option[Int], upstream: Boolean)

  private object Nav:
    val free: Nav = Nav(None, upstream = false)

  /** Wrap width and visible row count from the last render. */
  final private case class Geometry(wrapWidth: Int, viewRows: Int)

  private object Geometry:
    val empty: Geometry = Geometry(0, 0)

  /**
   * The visual lines of `value` at `wrapWidth`. Always at least one line,
   * so an empty value still has a line for the caret. A width of zero
   * (never rendered) yields the logical lines.
   */
  private[component] def layout(value: String, wrapWidth: Int): Vector[WrappedLine] =
    val width = if wrapWidth > 0 then wrapWidth else Int.MaxValue
    val lines = TextWrap.wrap(StyledLine(Vector(Span(value))), width)
    if lines.isEmpty then Vector(WrappedLine(StyledLine.Empty, 0, 0, hardBreak = false)) else lines

  /**
   * The line the caret at `offset` sits on: the last line starting at or
   * before it. An offset inside whitespace dropped at a soft break belongs
   * to the line before the gap. `upstream` resolves an offset that ends one
   * line and starts the next to the earlier line.
   */
  private[component] def rowOf(lines: Vector[WrappedLine], offset: Int, upstream: Boolean): Int =
    val i = math.max(0, lines.lastIndexWhere(_.start <= offset))
    if upstream && i > 0 && lines(i - 1).end == offset && !lines(i - 1).hardBreak then i - 1 else i

  /** Cell column of `offset` on `line`; offsets past the line's end sit at its end. */
  private[component] def columnOf(value: String, line: WrappedLine, offset: Int): Int =
    Widths.stringWidth(value.substring(line.start, math.max(line.start, math.min(offset, line.end))))

  /** Whether line `row` ends in a soft break, so a gap may follow it. */
  private def isSoftGap(lines: Vector[WrappedLine], row: Int): Boolean =
    row + 1 < lines.size && !lines(row).hardBreak
