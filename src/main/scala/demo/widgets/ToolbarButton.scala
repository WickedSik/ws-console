package io.github.wickedsik.wsconsole
package demo.widgets

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Frame}
import component.{Component, RenderContext}
import event.{Event, EventResult}
import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode
import geometry.Rect

import zio.{UIO, ZIO}

import java.io.IOException

/**
 * A bordered, focusable toolbar button.
 *
 * The action is supplied at construction and stored unexecuted. On
 * Enter / Space the button returns `EventResult.Perform(onActivate)` —
 * the render loop runs the effect on the loop fiber, and a redraw is
 * scheduled automatically. Detection and consequence live in one
 * place; no polling, no shared flag.
 *
 * Focus state is read from the per-frame [[RenderContext]] — no local
 * cache, no push pattern.
 *
 * Visual state:
 *   - Unfocused: dim border + label
 *   - Focused:   bright border + bold label
 */
final class ToolbarButton private (
  val label:  String,
  onActivate: ZIO[Frame, IOException, Unit]
) extends Component:

  override val focusable: Boolean = true

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !ctx.focus.isFocused(this.id) then EventResult.Ignored
    else
      event match
        case SpecialKey(SpecialKeyCode.Enter, _) => EventResult.Perform(onActivate)
        case CharKey(' ', _)                     => EventResult.Perform(onActivate)
        case _                                   => EventResult.Ignored

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.width < 4 || area.height < 3 then return
    val style =
      if ctx.focus.isFocused(this.id) then ToolbarButton.focusedStyle
      else ToolbarButton.unfocusedStyle
    canvas.drawBox(area, BoxStyle.Single, None, style)
    val inner = area.inner(1)
    if inner.height >= 1 then
      val truncated = if label.length > inner.width then label.take(inner.width) else label
      val x = inner.x + math.max(0, (inner.width - truncated.length) / 2)
      val y = inner.y + math.max(0, (inner.height - 1) / 2)
      canvas.putText(x, y, truncated, style)

object ToolbarButton:

  /** Construct a button bound to `onActivate` — fired on Enter / Space. */
  def make(label: String, onActivate: ZIO[Frame, IOException, Unit]): UIO[ToolbarButton] =
    ZIO.succeed(new ToolbarButton(label, onActivate))

  private val unfocusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  private val focusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))
