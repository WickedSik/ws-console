package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, Canvas, CellStyle, Foreground, Frame}
import event.{Event, EventResult, KeyInput, Keymap, SpecialKeyCode}
import geometry.Rect
import glyph.Widths

import zio.{UIO, ZIO}

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Focusable single-value slider over `[min, max]` in steps of `step`.
 *
 * The value is held as a step index, so repeated keys never accumulate
 * floating-point drift: the value at index `i` is `min + i * step`,
 * computed in decimal. When the range is not a whole number of steps,
 * the last index is `max` itself, so `End` always reaches the bound.
 * Any value supplied — the constructor `value`, [[set]] — is clamped into
 * the range and snapped to the nearest step.
 *
 * A single focus stop. While focused: `Left` / `Right` move by one step,
 * `PgDn` / `PgUp` by `pageSteps` steps, `Home` / `End` to the bounds
 * ([[Slider.DefaultKeymap]]). Moves at a bound return `Ignored`. Every
 * unbound key bubbles — `Up` / `Down` and Ctrl-chords among them — so a
 * slider inside a `ScrollView` leaves vertical scrolling alone.
 *
 * `onChange` is bound at `make`. On each change the widget updates its
 * index and returns `EventResult.Perform(onChange(newValue))`. The widget
 * owns the value; the host seeds it, observes changes, and may push a new
 * one with [[set]] (a companion numeric field, a form reset).
 *
 * Visual composition — row 1 is the track, `─` across the full width,
 * with the thumb `●` at column `round(fraction * (width - 1))`; the thumb
 * alone marks the value, there is no fill. With a `default`, row 2
 * carries `defaultLabel` centred under the default's column, clamped at
 * both edges and truncated by cells when wider than the slider.
 *
 *   - Track          — `style + Dim`
 *   - Thumb at rest  — `style`
 *   - Thumb focused  — `style + Bold`, fg [[Slider.FocusedThumbColor]]
 *   - Default label  — `style + Dim`
 *   - Disabled       — everything `style + Dim`
 *
 * The focused thumb swaps hue, a deliberate exception to the styleguide's
 * "role owns hue, state owns attributes" rule (§2.3): `Bold` on a lone
 * `●` is not visible, and the thumb is the only cell that can show focus.
 * It holds until a `Theme` service supplies per-state overrides, which
 * then replaces [[Slider.FocusedThumbColor]].
 */
final class Slider private (
  val min: Double,
  val max: Double,
  val step: Double,
  initialValue: Double,
  val pageSteps: Int,
  val default: Option[Double],
  val defaultLabel: String,
  val style: CellStyle,
  val enabled: Boolean,
  val keymap: Keymap[Slider.Action],
  onChange: Double => ZIO[Frame, IOException, Unit]
) extends Component:
  require(min < max, s"min must be below max, got [$min, $max]")
  require(step > 0, s"step must be positive, got $step")
  require(pageSteps >= 1, s"pageSteps must be at least 1, got $pageSteps")
  require((max - min) / step <= Int.MaxValue, s"[$min, $max] by $step has more steps than an Int holds")

  /** Disabled sliders are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  /** Index of `max`: the step count, rounded up when `step` does not divide the range. */
  private val lastIndex: Int =
    val steps = (max - min) / step
    val nearest = math.round(steps)
    if math.abs(steps - nearest) < 1e-9 then nearest.toInt else math.ceil(steps).toInt

  private val indexRef = AtomicInteger(indexOf(initialValue))

  /** Current value, always a step of the range or `max`. */
  def value: Double = valueAt(indexRef.get())

  /**
   * Replace the value, clamped into the range and snapped to the nearest
   * step. Does not fire `onChange`: the caller is the source of the value.
   * Safe from any thread — it touches widget-local state only.
   */
  def set(newValue: Double): Unit = indexRef.set(indexOf(newValue))

  private def valueAt(i: Int): Double =
    if i >= lastIndex then max
    else (BigDecimal(min) + BigDecimal(step) * i).toDouble

  private def indexOf(v: Double): Int =
    val clamped = if v.isNaN then min else math.max(min, math.min(max, v))
    val nearest = math.max(0, math.min(lastIndex, math.round((clamped - min) / step).toInt))
    // The last step may be shorter than `step`: prefer `max` when it is closer.
    if math.abs(max - clamped) < math.abs(valueAt(nearest) - clamped) then lastIndex else nearest

  // ===== Event handling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      KeyInput.decode(event, keymap) match
        case KeyInput.Bound(Slider.Action.Step(delta))                => moveTo(indexRef.get() + delta)
        case KeyInput.Bound(Slider.Action.Page(delta))                => moveTo(indexRef.get() + delta * pageSteps)
        case KeyInput.Bound(Slider.Action.ToMin)                      => moveTo(0)
        case KeyInput.Bound(Slider.Action.ToMax)                      => moveTo(lastIndex)
        case KeyInput.Text(_) | KeyInput.Unbound(_) | KeyInput.NotKey => EventResult.Ignored

  private def moveTo(target: Int): EventResult =
    val current = indexRef.get()
    val next = math.max(0, math.min(lastIndex, target))
    if next == current then EventResult.Ignored
    else
      indexRef.set(next)
      EventResult.Perform(onChange(valueAt(next)))

  // ===== Rendering =====

  /** The track, plus a row for the default label when there is a default. */
  override def preferredHeight(width: Int): Option[Int] =
    Some(if default.isDefined then 2 else 1)

  /** Column of `v` on a track `width` cells wide. */
  private def columnOf(v: Double, width: Int): Int =
    math.round((v - min) / (max - min) * (width - 1)).toInt

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.nonEmpty then
      val muted = style.copy(attributes = style.attributes + Attribute.Dim)
      val thumbStyle =
        if !enabled then muted
        else if ctx.focus.isFocused(this.id) then
          style.copy(fg = Slider.FocusedThumbColor, attributes = style.attributes + Attribute.Bold)
        else style

      val width = area.width
      val thumbX = area.x + columnOf(value, width)
      for x <- area.x until area.x + width do
        if x == thumbX then canvas.putText(x, area.y, Slider.ThumbGlyph, thumbStyle)
        else canvas.putText(x, area.y, Slider.TrackGlyph, muted)

      if area.height >= 2 then
        for d <- default do
          val label = Widths.truncate(defaultLabel, width)
          val labelWidth = Widths.stringWidth(label)
          val centre = columnOf(math.max(min, math.min(max, d)), width)
          val start = math.max(0, math.min(width - labelWidth, centre - labelWidth / 2))
          canvas.putText(area.x + start, area.y + 1, label, muted)

object Slider:

  /** Track glyph: U+2500, box drawings light horizontal. */
  val TrackGlyph: String = "─"

  /** Thumb glyph: U+25CF, black circle. */
  val ThumbGlyph: String = "●"

  /**
   * Thumb colour while focused. Magenta carries no semantic role in the
   * styleguide (§2.1), so it reads as "selected here", not as a warning,
   * error or success. Interim, until a `Theme` service owns state colours.
   */
  val FocusedThumbColor: Foreground = Foreground.Named(FgColor.BrightMagenta)

  /** What a key can ask a slider to do. */
  enum Action:
    /** Move by `delta` steps. */
    case Step(delta: Int)

    /** Move by `delta` pages of `pageSteps` steps. */
    case Page(delta: Int)

    /** Jump to `min`. */
    case ToMin

    /** Jump to `max`. */
    case ToMax

  /** Bare `Left` / `Right` step, `PgDn` / `PgUp` page, `Home` / `End` jump to the bounds. */
  val DefaultKeymap: Keymap[Action] = Keymap(
    Keymap.plain(SpecialKeyCode.Left)  -> Action.Step(-1),
    Keymap.plain(SpecialKeyCode.Right) -> Action.Step(+1),
    Keymap.plain(SpecialKeyCode.PgDn)  -> Action.Page(-1),
    Keymap.plain(SpecialKeyCode.PgUp)  -> Action.Page(+1),
    Keymap.plain(SpecialKeyCode.Home)  -> Action.ToMin,
    Keymap.plain(SpecialKeyCode.End)   -> Action.ToMax
  )

  /**
   * Construct a slider. `value` seeds it, clamped and snapped to a step;
   * the widget owns it thereafter, fires `onChange(newValue)` per change,
   * and accepts host pushes through [[Slider.set]]. `pageSteps` is the
   * `PgUp` / `PgDn` stride in steps. `default`, when given, is marked by
   * `defaultLabel` on a second row. `keymap` binds keys to moves.
   */
  def make(
    min: Double,
    max: Double,
    step: Double,
    value: Double,
    onChange: Double => ZIO[Frame, IOException, Unit] = _ => ZIO.unit,
    pageSteps: Int = 10,
    default: Option[Double] = None,
    defaultLabel: String = "Default",
    style: CellStyle = CellStyle.Empty,
    enabled: Boolean = true,
    keymap: Keymap[Action] = DefaultKeymap
  ): UIO[Slider] =
    ZIO.succeed(Slider(min, max, step, value, pageSteps, default, defaultLabel, style, enabled, keymap, onChange))
