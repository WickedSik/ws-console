package io.github.wickedsik.wsconsole
package component

import buffer.{BoxStyle, Canvas, Cell, CellStyle, Frame}
import event.{Event, EventResult, KeyInput, Keymap, SpecialKeyCode}
import geometry.{Insets, Rect, Sides}

import zio.{UIO, ZIO}

import java.io.IOException

/**
 * Focusable activatable control with a single-line label.
 *
 * Constructor-bound action: `onActivate` is supplied at `make` and
 * stored unexecuted. On a key bound to [[Button.Action.Activate]] while
 * focused and enabled (by default a bare Enter or Space; see
 * [[Button.DefaultKeymap]]), the button returns
 * `EventResult.Perform(onActivate)` — the render loop runs it on the
 * loop fiber, and a redraw is scheduled automatically. Every other key
 * bubbles. The widget carries no side effect and stores no shared state.
 *
 * Visual composition mirrors [[Panel]]: opaque fill across `area`, then
 * the border (short-circuited when `sides` is empty), then the label
 * placed in `area.inner(sides.toInsets).inner(padding)` and centered.
 *
 * Interaction states derive from framework focus and the `enabled` flag:
 *
 *   - `normal`   — `style` as supplied
 *   - `focused`  — [[InteractionState.focused]] adds `Bold`
 *   - `disabled` — [[InteractionState.disabled]] adds `Dim`; the widget
 *                  is excluded from the focus cycle (`focusable = false`)
 *                  and refuses Enter / Space
 *
 * `active` is documented in [[InteractionState]] but not triggered by
 * keyboard activation — there is no natural moment to render a press
 * flash when `Perform` returns immediately. Mouse-driven activation
 * will introduce a mousedown/mouseup boundary where it earns its keep.
 *
 * State modulation adds attributes only — `focused` does not swap hue.
 * Consumers wanting a stronger focus signal supply a brighter base
 * `style`.
 */
final class Button private (
  val label: String,
  val style: CellStyle,
  val border: BoxStyle,
  val sides: Sides,
  val padding: Insets,
  val enabled: Boolean,
  val keymap: Keymap[Button.Action],
  onActivate: ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled buttons are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      KeyInput.decode(event, keymap) match
        case KeyInput.Bound(Button.Action.Activate)                   => EventResult.Perform(onActivate)
        case KeyInput.Text(_) | KeyInput.Unbound(_) | KeyInput.NotKey => EventResult.Ignored

  private def undersizedForBorder(area: Rect): Boolean =
    val insets = sides.toInsets
    area.width < insets.left + insets.right || area.height < insets.top + insets.bottom

  /** One content row plus border rows and vertical padding. */
  override def preferredHeight(width: Int): Option[Int] =
    val chrome = sides.toInsets
    Some(1 + chrome.top + chrome.bottom + padding.top + padding.bottom)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.nonEmpty && !undersizedForBorder(area) then
      val effectiveStyle =
        if !enabled then InteractionState.disabled(style)
        else if ctx.focus.isFocused(this.id) then InteractionState.focused(style)
        else style

      canvas.fillRect(area, Cell(' ', effectiveStyle))
      canvas.drawBox(area, border, sides, None, effectiveStyle)

      val inner = area.inner(sides.toInsets).inner(padding)
      if inner.nonEmpty then
        val truncated = label.take(inner.width)
        val xOffset = math.max(0, (inner.width - truncated.length) / 2)
        val yOffset = math.max(0, (inner.height - 1) / 2)
        canvas.putText(inner.x + xOffset, inner.y + yOffset, truncated, effectiveStyle)

object Button:

  /** What a key can ask a button to do. */
  enum Action:
    /** Run the bound `onActivate` action. */
    case Activate

  /** A bare Enter or Space activates. */
  val DefaultKeymap: Keymap[Action] = Keymap(
    Keymap.plain(SpecialKeyCode.Enter) -> Action.Activate,
    Keymap.plain(' ')                  -> Action.Activate
  )

  /**
   * Construct a button bound to `onActivate`. `style` carries the
   * semantic role; the widget derives interaction-state appearances
   * from it. `keymap` decides which keys activate it.
   */
  def make(
    label: String,
    onActivate: ZIO[Frame, IOException, Unit],
    style: CellStyle = CellStyle.Empty,
    border: BoxStyle = BoxStyle.Single,
    sides: Sides = Sides.all,
    padding: Insets = Insets.zero,
    enabled: Boolean = true,
    keymap: Keymap[Action] = DefaultKeymap
  ): UIO[Button] =
    ZIO.succeed(Button(label, style, border, sides, padding, enabled, keymap, onActivate))
