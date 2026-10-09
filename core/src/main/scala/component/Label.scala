package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle}
import geometry.Rect

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * One line of text the host updates: a character counter, a "n remaining"
 * hint, a status line.
 *
 * Renders like [[Text]] — truncated by cells, never splitting a wide
 * grapheme, placed by `align` — but its text is widget-local state the
 * host replaces with [[set]], typically from a control's `onChange`:
 *
 * {{{
 *   counter <- Label.make("Characters: 0/100", muted)
 *   field   <- TextInput.make(
 *                maxLength = Some(100),
 *                onChange = v => ZIO.succeed(counter.set(s"Characters: ${Graphemes.count(v)}/100"))
 *              )
 * }}}
 *
 * Not focusable; it never handles events.
 */
final class Label private (initial: String, val style: CellStyle, val align: Alignment) extends Component:

  private val textRef = new AtomicReference[String](initial)

  /** The text currently shown. */
  def text: String = textRef.get()

  /**
   * Replace the text; it shows on the next frame. Fires nothing, so a
   * host can call it from any `onChange` without a loop. Safe from any
   * thread — it touches widget-local state only — but only a redraw
   * shows it: call from an `onChange` or `Perform` effect, which redraw.
   */
  def set(newText: String): Unit = textRef.set(newText)

  /** One row, whatever the width — the text truncates rather than wraps. */
  override def preferredHeight(width: Int): Option[Int] = Some(1)

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    Text(text, style, align).render(area, canvas, ctx)

object Label:

  /** Construct a label showing `text` in `style`, placed by `align`. */
  def make(text: String = "", style: CellStyle = CellStyle.Empty, align: Alignment = Alignment.Left): UIO[Label] =
    ZIO.succeed(new Label(text, style, align))
