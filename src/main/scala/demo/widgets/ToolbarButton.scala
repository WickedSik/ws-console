package io.github.wickedsik.wsconsole
package demo.widgets

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.{Component, RenderContext}
import event.{Event, EventResult, KeyEvent}
import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode
import geometry.Rect

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A bordered, focusable toolbar button. The label includes its shortcut
 * letter: `[Next (n)]`.
 *
 * The button itself does not own an action — it returns
 * `EventResult.RequestRedraw` on Enter/Space and flips a pending flag
 * so the application can poll `consumePending` after dispatch. This
 * keeps the side effect out of the sync `handleEvent` path.
 *
 * Focus state is read from the per-frame [[RenderContext]] — no local
 * cache, no push pattern. Pending activation remains widget-local
 * (transient, polled-after-dispatch) per the ADT's "local stays local"
 * discipline. We use `AtomicBoolean` for the pending cell rather than
 * `zio.Ref` because `Ref.Atomic` — the only `Ref` subtype with a public
 * synchronous unsafe API — is `private[zio]`. The chosen primitive
 * provides the same memory semantics with a public surface that stays
 * effectful at every call site.
 *
 * Visual state:
 *   - Unfocused: dim border + label
 *   - Focused:   bright border + bold label
 */
final class ToolbarButton private (
  val label:    String,
  val shortcut: Char,
  pendingFlag:  AtomicBoolean
) extends Component:

  override val focusable: Boolean = true

  /** True if Enter / Space landed on this button since the last consume. */
  def consumePending: UIO[Boolean] = ZIO.succeed(pendingFlag.getAndSet(false))

  override def handleEvent(event: Event, ctx: RenderContext): EventResult = event match
    case SpecialKey(SpecialKeyCode.Enter, _) if ctx.focus.isFocused(this.id) =>
      pendingFlag.set(true)
      EventResult.RequestRedraw
    case CharKey(' ', _) if ctx.focus.isFocused(this.id) =>
      pendingFlag.set(true)
      EventResult.RequestRedraw
    case _ => EventResult.Ignored

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.width < 4 || area.height < 3 then return
    val style =
      if ctx.focus.isFocused(this.id) then ToolbarButton.focusedStyle
      else ToolbarButton.unfocusedStyle
    canvas.drawBox(area, BoxStyle.Single, None, style)
    val inner = area.inner(1)
    if inner.height >= 1 then
      val text = s"$label ($shortcut)"
      val truncated = if text.length > inner.width then text.take(inner.width) else text
      val x = inner.x + math.max(0, (inner.width - truncated.length) / 2)
      val y = inner.y + math.max(0, (inner.height - 1) / 2)
      canvas.putText(x, y, truncated, style)

object ToolbarButton:

  /** Construct a fresh button with its own internal pending-activation cell. */
  def make(label: String, shortcut: Char): UIO[ToolbarButton] =
    ZIO.succeed(new ToolbarButton(label, shortcut, AtomicBoolean(false)))

  private val unfocusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  private val focusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))
