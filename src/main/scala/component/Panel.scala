package io.github.wickedsik.wsconsole
package component

import buffer.{BoxStyle, Canvas, Cell, CellStyle}
import geometry.{Insets, Rect}

/**
 * A bordered container with optional title around a single child.
 *
 * Renders an opaque background fill across `area` (a space cell carrying
 * `style`), then the border on the outer edge (short-circuited for
 * [[BoxStyle.Borderless]]), then `child` into
 * `area.inner(border.inset).inner(padding)`. The `border.inset` is the
 * self-describing frame cost; `padding` is per-edge content spacing
 * layered on top. A borderless panel with zero padding therefore hands
 * the child the panel's full area.
 *
 * The opaque fill ensures the panel owns every cell in its bounded area,
 * so a panel rendered over a lower panel in the same frame (`PanelHost`
 * stacking) does not leak the lower panel's content through gaps the
 * child does not cover.
 *
 * Title placement follows the Layer 2 [[Canvas.drawBox]] convention —
 * starting at column `x + 2` on the top edge, truncated if it would
 * exceed the box width. A [[BoxStyle.Borderless]] panel draws no title.
 */
final case class Panel(
  child: Component = Spacer,
  title: Option[String] = None,
  border: BoxStyle = BoxStyle.Single,
  style: CellStyle = CellStyle.Empty,
  padding: Insets = Insets.zero
) extends Component:

  /** True when the area is too small to fit this panel's border glyphs. */
  private def undersizedForBorder(area: Rect): Boolean =
    border.inset > 0 && (area.width < 2 || area.height < 2)

  override def childLayouts(area: Rect): Seq[(Component, Rect)] =
    // The child always appears in the layout result; if the panel is
    // empty or too small to draw its border, the child gets a zero-size
    // rect at the panel's origin. The render method short-circuits on
    // those cases and leaves the child undrawn.
    if area.isEmpty || undersizedForBorder(area) then
      Seq((child, Rect(area.x, area.y, 0, 0)))
    else
      Seq((child, area.inner(border.inset).inner(padding)))

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty || undersizedForBorder(area) then return
    canvas.fillRect(area, Cell(' ', style))
    canvas.drawBox(area, border, title, style)
    childLayouts(area).foreach { case (c, r) => c.render(r, canvas, ctx) }
