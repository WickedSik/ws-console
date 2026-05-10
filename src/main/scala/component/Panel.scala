package io.github.wickedsik.wsconsole
package component

import buffer.{BoxStyle, Canvas, CellStyle}
import geometry.Rect

/**
 * A bordered container with optional title around a single child.
 *
 * Renders the border on the outer edge of `area`, then renders `child`
 * into `area.inner(1)` (one-cell inset on every side). If `area` is
 * smaller than 2x2, rendering is a no-op.
 *
 * Title placement follows the Layer 2 [[Canvas.drawBox]] convention —
 * starting at column `x + 2` on the top edge, truncated if it would
 * exceed the box width.
 */
final case class Panel(
  child:  Component                 = Spacer,
  title:  Option[String]            = None,
  border: BoxStyle                  = BoxStyle.Single,
  style:  CellStyle                 = CellStyle.Empty
) extends Component:

  override def childLayouts(area: Rect): Seq[(Component, Rect)] =
    // The child always appears in the layout result; if the panel is too
    // small to draw a border, the child gets a zero-size rect at the
    // panel's origin. The render method short-circuits on undersized areas
    // and leaves the child undrawn.
    if area.width < 2 || area.height < 2 then
      Seq((child, Rect(area.x, area.y, 0, 0)))
    else
      Seq((child, area.inner(1)))

  override def render(area: Rect, canvas: Canvas): Unit =
    if area.isEmpty || area.width < 2 || area.height < 2 then return
    canvas.drawBox(area, border, title, style)
    childLayouts(area).foreach { case (c, r) => c.render(r, canvas) }
