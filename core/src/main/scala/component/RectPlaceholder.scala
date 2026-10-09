package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, Cell, CellStyle}
import geometry.Rect

/**
 * A rectangle filled with one style, standing in for an element the
 * terminal cannot represent yet: an image preview, a video frame, a chart
 * still to be drawn.
 *
 * It fills every cell of its area with a blank in `style`, so a background
 * colour shows the shape and size the real element would take. It has no
 * preferred height; give it a `Fixed` constraint in its container.
 */
final case class RectPlaceholder(style: CellStyle) extends Component:
  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if !area.isEmpty then canvas.fillRect(area, Cell(' ', style))
