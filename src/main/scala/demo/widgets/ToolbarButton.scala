package io.github.wickedsik.wsconsole
package demo.widgets

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.Component
import event.{Event, EventResult, KeyEvent}
import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode
import geometry.Rect

/**
 * A bordered, focusable toolbar button. The label includes its shortcut
 * letter, underlined for affordance: `[Next (n)]`.
 *
 * The button itself does not own an action — it returns
 * `EventResult.Consumed` on Enter/Space so the focus-cycle parent knows
 * the event was handled, and exposes `consumePending` for the application
 * to poll after dispatch. This keeps the side effect out of the sync
 * `handleEvent` path.
 *
 * Visual state:
 *   - Unfocused: dim border + label
 *   - Focused:   bright border + bold label
 */
final class ToolbarButton(
  val label:    String,
  val shortcut: Char
) extends Component:

  override val focusable: Boolean = true

  @volatile private var focusedFlag: Boolean = false
  @volatile private var pendingFlag: Boolean = false

  /** Set the visual focus flag. The flag is read by `render` next frame. */
  def setFocused(b: Boolean): Unit = focusedFlag = b
  def isFocused:  Boolean          = focusedFlag

  /** True if Enter / Space landed on this button since the last consume. */
  def consumePending(): Boolean =
    if pendingFlag then
      pendingFlag = false
      true
    else false

  override def handleEvent(event: Event): EventResult = event match
    case SpecialKey(SpecialKeyCode.Enter, _) if focusedFlag =>
      pendingFlag = true
      EventResult.RequestRedraw
    case CharKey(' ', _) if focusedFlag =>
      pendingFlag = true
      EventResult.RequestRedraw
    case _ => EventResult.Ignored

  def render(area: Rect, canvas: Canvas): Unit =
    if area.width < 4 || area.height < 3 then return
    val style = if focusedFlag then ToolbarButton.focusedStyle else ToolbarButton.unfocusedStyle
    canvas.drawBox(area, BoxStyle.Single, None, style)
    val inner = area.inner(1)
    if inner.height >= 1 then
      val text = s"$label ($shortcut)"
      val truncated = if text.length > inner.width then text.take(inner.width) else text
      val x = inner.x + math.max(0, (inner.width - truncated.length) / 2)
      val y = inner.y + math.max(0, (inner.height - 1) / 2)
      canvas.putText(x, y, truncated, style)

object ToolbarButton:

  private val unfocusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  private val focusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))
