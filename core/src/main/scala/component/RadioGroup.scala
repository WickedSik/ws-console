package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, CellStyle, Frame}
import event.KeyEvent.SpecialKey
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import glyph.Widths

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
 * `Right` when horizontal — and `Home` / `End` jump to first / last. The
 * cross-axis arrows are never consumed: they bubble to the parent, so
 * a horizontal group inside a `ScrollView` never takes the keys that
 * scroll it.
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
  val marks: (on: String, off: String),
  val style: CellStyle,
  val enabled: Boolean,
  val layout: RadioGroup.Layout,
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

  /** The arrow keys along the layout's axis: (previous, next). */
  private val (previousKey, nextKey) = layout match
    case Layout.Vertical      => (SpecialKeyCode.Up, SpecialKeyCode.Down)
    case Layout.Horizontal(_) => (SpecialKeyCode.Left, SpecialKeyCode.Right)

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || options.isEmpty || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        // Ctrl-chords scroll an enclosing view, not the selection: leave them to bubble.
        case SpecialKey(_, m) if m(KeyModifier.Ctrl)    => EventResult.Ignored
        case SpecialKey(code, _) if code == previousKey => moveSelection(-1)
        case SpecialKey(code, _) if code == nextKey     => moveSelection(+1)
        case SpecialKey(SpecialKeyCode.Home, _)         => setSelection(0)
        case SpecialKey(SpecialKeyCode.End, _)          => setSelection(options.size - 1)
        case _                                          => EventResult.Ignored

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
      val glyph = if i == currentSelected then marks.on else marks.off
      val markWidth = Widths.stringWidth(glyph)
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

  /** Default mark glyphs — `("●", "○")`. */
  val DefaultMarks: (on: String, off: String) = (on = "●", off = "○")

  /**
   * Construct a radio group. `selected` seeds the initial index (clamped
   * into range); the widget owns it thereafter and fires
   * `onSelect(newIndex)` on every change. `layout` places the options
   * and picks the arrow keys that move the selection.
   */
  def make(
    options: Seq[String],
    onSelect: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    selected: Int = 0,
    marks: (on: String, off: String) = DefaultMarks,
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true,
    layout: Layout = Layout.Vertical
  ): UIO[RadioGroup] =
    ZIO.succeed(RadioGroup(options, selected, marks, style, enabled, layout, onSelect))
