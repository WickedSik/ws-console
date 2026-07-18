package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle}
import geometry.Rect

/** Horizontal alignment of text within an area. */
enum Alignment:
  case Left, Center, Right

/**
 * A leaf widget that renders a single line of text within its area.
 *
 * The text is placed on the first row of `area`. Horizontal placement
 * follows `align`; the text is truncated rather than wrapped if it
 * exceeds the area width. Word-wrapping arrives with the dedicated
 * `WrappedText` widget once Unicode-width handling is in place.
 *
 * For empty `area` (zero width or height), rendering is a no-op.
 */
final case class Text(
  content: String,
  style:   CellStyle = CellStyle.Empty,
  align:   Alignment = Alignment.Left
) extends Component:

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || content.isEmpty then return

    val truncated =
      if content.length > area.width then content.take(area.width)
      else content

    val xOffset = align match
      case Alignment.Left   => 0
      case Alignment.Center => math.max(0, (area.width - truncated.length) / 2)
      case Alignment.Right  => math.max(0, area.width - truncated.length)

    canvas.putText(area.x + xOffset, area.y, truncated, style)
