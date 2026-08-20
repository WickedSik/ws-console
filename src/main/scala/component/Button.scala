package io.github.wickedsik.wsconsole
package component

import buffer.{BoxStyle, Canvas, Cell, CellStyle, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, SpecialKeyCode}
import geometry.{Insets, Rect}

import zio.{UIO, ZIO}

import java.io.IOException

/**
 * Focusable activatable control with a single-line label.
 *
 * Constructor-bound action: `onActivate` is supplied at `make` and
 * stored unexecuted. On Enter or Space while focused and enabled, the
 * button returns `EventResult.Perform(onActivate)` — the render loop
 * runs it on the loop fiber, and a redraw is scheduled automatically.
 * The widget carries no side effect and stores no shared state.
 *
 * Visual composition mirrors [[Panel]]: opaque fill across `area`, then
 * the border (no-op for [[BoxStyle.Borderless]]), then the label placed
 * in `area.inner(border.inset).inner(padding)` and centered.
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
  val label:   String,
  val style:   CellStyle,
  val border:  BoxStyle,
  val padding: Insets,
  val enabled: Boolean,
  onActivate:  ZIO[Frame, IOException, Unit]
) extends Component:

  /** Disabled buttons are excluded from the focus cycle. */
  override val focusable: Boolean = enabled

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !enabled || !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        case SpecialKey(SpecialKeyCode.Enter, _) => EventResult.Perform(onActivate)
        case CharKey(' ', _)                     => EventResult.Perform(onActivate)
        case _                                   => EventResult.Ignored

  private def undersizedForBorder(area: Rect): Boolean =
    border.inset > 0 && (area.width < 2 || area.height < 2)

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || undersizedForBorder(area) then return

    val effectiveStyle =
      if !enabled                       then InteractionState.disabled(style)
      else if ctx.focus.isFocused(this.id) then InteractionState.focused(style)
      else                                   style

    canvas.fillRect(area, Cell(' ', effectiveStyle))
    canvas.drawBox(area, border, None, effectiveStyle)

    val inner = area.inner(border.inset).inner(padding)
    if inner.isEmpty then return

    val truncated = if label.length > inner.width then label.take(inner.width) else label
    val xOffset   = math.max(0, (inner.width  - truncated.length) / 2)
    val yOffset   = math.max(0, (inner.height - 1) / 2)
    canvas.putText(inner.x + xOffset, inner.y + yOffset, truncated, effectiveStyle)

object Button:

  /**
   * Construct a button bound to `onActivate`. `style` carries the
   * semantic role; the widget derives interaction-state appearances
   * from it.
   */
  def make(
    label:      String,
    onActivate: ZIO[Frame, IOException, Unit],
    style:      CellStyle = CellStyle.Empty,
    border:     BoxStyle  = BoxStyle.Single,
    padding:    Insets    = Insets.zero,
    enabled:    Boolean   = true
  ): UIO[Button] =
    ZIO.succeed(new Button(label, style, border, padding, enabled, onActivate))
