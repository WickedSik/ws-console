package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle, Span, StyledLine}
import geometry.Rect

/** Horizontal alignment of text within an area. */
enum Alignment:
  case Left, Center, Right

  /** Columns to skip so a run `used` columns wide sits aligned in `available`. */
  def offset(used: Int, available: Int): Int = this match
    case Left   => 0
    case Center => math.max(0, (available - used) / 2)
    case Right  => math.max(0, available - used)

/**
 * A leaf widget that renders a single line of styled text within its area.
 *
 * The line is placed on the first row of `area`, each span in its own
 * style. Horizontal placement follows `align`; the line is truncated by
 * terminal columns rather than wrapped if it exceeds the area width. For
 * multi-line, word-wrapped text use [[WrappedText]].
 *
 * `Text("label", style)` is the one-span form; `Text(Spans(...))` takes
 * several spans:
 *
 * {{{
 * Text(Spans(Span("Name", label), Span("*", required)))
 * }}}
 *
 * For empty `area` (zero width or height), rendering is a no-op.
 */
final case class Text(line: StyledLine, align: Alignment) extends Component:

  /** One row, whatever the width — the text truncates rather than wraps. */
  override def preferredHeight(width: Int): Option[Int] = Some(1)

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || line.isEmpty then return

    // Measured in columns, not chars: a CJK glyph is two columns wide.
    val shown = line.truncate(area.width)
    shown.draw(canvas, area.x + align.offset(shown.width, area.width), area.y)

object Text:

  /** One line of text in a single style. */
  def apply(content: String, style: CellStyle = CellStyle.Empty, align: Alignment = Alignment.Left): Text =
    Text(StyledLine(Vector(Span(content, style))), align)

  /** A line of styled spans, left-aligned. */
  def apply(line: StyledLine): Text = Text(line, Alignment.Left)
