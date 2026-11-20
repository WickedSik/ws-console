package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, CellStyle, Frame}
import event.{Event, EventResult, KeyInput, Keymap, SpecialKeyCode}
import geometry.Rect
import glyph.Widths
import layout.Direction

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Focusable set of mutually exclusive options, exactly one selected.
 *
 * `layout` places the options: [[RadioGroup.Layout.Vertical]] one per
 * row, [[RadioGroup.Layout.Horizontal]] all on one row, `gap` cells
 * apart.
 *
 * A single focus stop. While focused, the arrow keys along the layout's
 * axis move the selection — `Up` / `Down` when vertical, `Left` /
 * `Right` when horizontal — and `Home` / `End` jump to first / last
 * ([[RadioGroup.DefaultKeymap]]). The previous and next actions carry
 * the axis they move along, and one bound to the cross axis is never
 * consumed: it bubbles to the parent, so a horizontal group inside a
 * `ScrollView` never takes the keys that scroll it. Every unbound key
 * bubbles too, `Ctrl` chords among them.
 * Changes at the bounds return `Ignored` (no wrap-around).
 *
 * `onSelect` is bound at `make`. On each change the widget updates its
 * local index and returns `EventResult.Perform(onSelect(newIndex))`.
 * The widget owns the mutable index; the constructor `selected` seeds
 * it and the host observes changes via `onSelect`.
 *
 * Visual composition — each option is `{mark}` `{space}` `{label}`:
 *
 *   - Unselected option            — `style + Dim`
 *   - Selected mark               — `style + Bold`
 *   - Selected label at rest      — `style`
 *   - Selected label when focused — `style + Bold`
 *   - Disabled                    — every option `style + Dim`
 *
 * Labels truncate by cells, never splitting a wide grapheme. In a
 * horizontal group the option that reaches the right edge is truncated
 * and the ones after it are not drawn. A "none selected" entry, if
 * wanted, is added as an explicit option — the group does not model
 * absent selection.
 */
final class RadioGroup private (
  val options: Seq[String],
  initialSelected: Int,
  val marks: BooleanMarks,
  val style: CellStyle,
  val enabled: Boolean,
  val layout: RadioGroup.Layout,
  val keymap: Keymap[RadioGroup.Action],
  onSelect: Int => ZIO[Frame, IOException, Unit]
) extends Component:

  import RadioGroup.Layout

  layout match
    case Layout.Horizontal(gap) => require(gap >= 0, s"gap must not be negative, got $gap")
    case Layout.Vertical        => ()

  /** Disabled or empty groups are excluded from the focus cycle. */
  override val focusable: Boolean = enabled && options.nonEmpty

  private val selectedRef =
    AtomicInteger(clampIndex(initialSelected))

  /** Current selected option index, clamped to `[0, options.size - 1]`. */
  def selected: Int = selectedRef.get()

  private def clampIndex(i: Int): Int =
    if options.isEmpty then 0
    else math.max(0, math.min(options.size - 1, i))

  // ===== Event handling =====

  /** The axis the options run along; only a move along it changes the selection. */
  private val axis: Direction = layout match
    case Layout.Vertical      => Direction.Vertical
    case Layout.Horizontal(_) => Direction.Horizontal

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || options.isEmpty || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      KeyInput.decode(event, keymap) match
        case KeyInput.Bound(RadioGroup.Action.Previous(along))        => alongAxis(along, -1)
        case KeyInput.Bound(RadioGroup.Action.Next(along))            => alongAxis(along, +1)
        case KeyInput.Bound(RadioGroup.Action.First)                  => setSelection(0)
        case KeyInput.Bound(RadioGroup.Action.Last)                   => setSelection(options.size - 1)
        case KeyInput.Text(_) | KeyInput.Unbound(_) | KeyInput.NotKey => EventResult.Ignored

  /** Move by `delta` when `along` is this group's axis; a cross-axis move bubbles. */
  private def alongAxis(along: Direction, delta: Int): EventResult =
    if along == axis then moveSelection(delta) else EventResult.Ignored

  private def moveSelection(delta: Int): EventResult =
    setSelection(selected + delta)

  private def setSelection(target: Int): EventResult =
    val current = selected
    val next = clampIndex(target)
    if next == current then EventResult.Ignored
    else
      selectedRef.set(next)
      EventResult.Perform(onSelect(next))

  // ===== Rendering =====

  /** One row per option when vertical; one row in all when horizontal. */
  override def preferredHeight(width: Int): Option[Int] =
    layout match
      case Layout.Vertical      => Some(options.size)
      case Layout.Horizontal(_) => Some(if options.isEmpty then 0 else 1)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.nonEmpty && options.nonEmpty then draw(area, canvas, ctx)

  private def draw(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val isFocused = ctx.focus.isFocused(this.id)
    val currentSelected = selected
    val muted = style.copy(attributes = style.attributes + Attribute.Dim)
    val bold = style.copy(attributes = style.attributes + Attribute.Bold)

    /** Mark and label styles for option `i`. */
    def stylesFor(i: Int): (mark: CellStyle, label: CellStyle) =
      if !enabled then (muted, muted)
      else if i == currentSelected then (bold, if isFocused then bold else style)
      else (muted, muted)

    /**
     * Draw option `i` — `{mark} {label}` — at (`x`, `y`) within `width`
     * cells. Returns the cells drawn, or 0 when not even the mark, its
     * space and one label cell fit.
     */
    def drawOption(i: Int, x: Int, y: Int, width: Int): Int =
      val glyph = marks(i == currentSelected)
      // Every option reserves the wider mark, so the labels line up and
      // a change of selection never moves them.
      val markWidth = marks.width
      if width < markWidth + 2 then 0
      else
        val (markStyle, labelStyle) = stylesFor(i)
        val label = Widths.truncate(options(i), width - markWidth - 1)
        canvas.putText(x, y, glyph, markStyle)
        canvas.putText(x + markWidth + 1, y, label, labelStyle)
        markWidth + 1 + Widths.stringWidth(label)

    layout match
      case Layout.Vertical =>
        val rows = math.min(options.size, area.height)
        for i <- 0 until rows do
          val _ = drawOption(i, area.x, area.y + i, area.width)

      case Layout.Horizontal(gap) =>
        val right = area.x + area.width
        var x = area.x
        var i = 0
        while i < options.size && x < right do
          val drawn = drawOption(i, x, area.y, right - x)
          x = if drawn == 0 then right else x + drawn + gap
          i += 1

object RadioGroup:

  /** Where the options sit, and with it which arrow keys move the selection. */
  enum Layout:
    /** One option per row; `Up` / `Down` move the selection. */
    case Vertical

    /** All options on one row, `gap` cells apart; `Left` / `Right` move the selection. */
    case Horizontal(gap: Int = 2)

  /** What a key can ask a radio group to do. */
  enum Action:
    /** Select the option before the current one, when the group runs `along` this axis. */
    case Previous(along: Direction)

    /** Select the option after the current one, when the group runs `along` this axis. */
    case Next(along: Direction)

    /** Select the first option. */
    case First

    /** Select the last option. */
    case Last

  /**
   * The bare arrows, each moving along its own axis, so one keymap serves
   * both layouts; `Home` and `End` jump to the ends.
   */
  val DefaultKeymap: Keymap[Action] = Keymap(
    Keymap.plain(SpecialKeyCode.Up)    -> Action.Previous(Direction.Vertical),
    Keymap.plain(SpecialKeyCode.Down)  -> Action.Next(Direction.Vertical),
    Keymap.plain(SpecialKeyCode.Left)  -> Action.Previous(Direction.Horizontal),
    Keymap.plain(SpecialKeyCode.Right) -> Action.Next(Direction.Horizontal),
    Keymap.plain(SpecialKeyCode.Home)  -> Action.First,
    Keymap.plain(SpecialKeyCode.End)   -> Action.Last
  )

  /** Default mark glyphs — `●` selected, `○` unselected. */
  val DefaultMarks: BooleanMarks = BooleanMarks("●", "○")

  /**
   * Construct a radio group. `selected` seeds the initial index (clamped
   * into range); the widget owns it thereafter and fires
   * `onSelect(newIndex)` on every change. `layout` places the options
   * and picks the axis whose moves change the selection; `keymap` binds
   * keys to moves.
   */
  def make(
    options: Seq[String],
    onSelect: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Int = 0,
    marks: BooleanMarks = DefaultMarks,
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true,
    layout: Layout = Layout.Vertical,
    keymap: Keymap[Action] = DefaultKeymap
  ): UIO[RadioGroup] =
    ZIO.succeed(RadioGroup(options, selected, marks, style, enabled, layout, keymap, onSelect))
