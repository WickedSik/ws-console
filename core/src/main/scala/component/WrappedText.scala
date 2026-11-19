package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle, StyledLine, TextWrap}
import geometry.Rect

/**
 * A leaf widget that word-wraps styled text into its area.
 *
 * Wrapping follows [[buffer.TextWrap.wrap]]: breaks on whitespace,
 * measures in terminal columns, honours `\n`, and carries each span's
 * style across line breaks. Every span's style is laid over `style`
 * ([[buffer.CellStyle.over]]), so an unstyled span takes the base style
 * and a styled one keeps its own hue. `align` applies to each wrapped
 * line on its own.
 *
 * `maxLines` caps the line count. When the text needs more, the last
 * kept line ends in [[buffer.TextWrap.Ellipsis]] (`…`). Without a cap
 * the text takes as many rows as it wraps to; rows beyond the area are
 * clipped.
 *
 * The preferred height is the wrapped line count at the given width,
 * capped by `maxLines`: text grows taller as it narrows.
 *
 * For empty `area` (zero width or height), rendering is a no-op.
 */
final case class WrappedText(
  line: StyledLine,
  style: CellStyle = CellStyle.Empty,
  align: Alignment = Alignment.Left,
  maxLines: Option[Int] = None
) extends Component:
  require(maxLines.forall(_ >= 1), s"maxLines must be at least 1, got $maxLines")

  override def preferredHeight(width: Int): Option[Int] = Some(lines(width).size)

  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.nonEmpty then
      lines(area.width).take(area.height).zipWithIndex.foreach { (l, row) =>
        l.draw(canvas, area.x + align.offset(l.width, area.width), area.y + row)
      }

  /** The lines drawn at `width`, after the `maxLines` cap and its marker. */
  def lines(width: Int): Vector[StyledLine] =
    WrappedText.layout(line.over(style), width, maxLines)

object WrappedText:

  /**
   * Wrap `line` at `width` and apply the `maxLines` cap: when the text needs
   * more lines, keep `max` and end the last one with the ellipsis.
   */
  def layout(line: StyledLine, width: Int, maxLines: Option[Int]): Vector[StyledLine] =
    val wrapped = TextWrap.wrap(line, width).map(_.line)
    maxLines
      .filter(wrapped.size > _)
      .fold(wrapped)(max => wrapped.take(max - 1) :+ TextWrap.ellipsize(wrapped(max - 1), width))
