package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import geometry.Rect
import layout.{Constraint, Layout, LayoutEngine, split}
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates Layer 3 constraint-based layout.
 *
 * Outer horizontal split: [Fixed(20) sidebar | Fill content | Fixed(15) palette]
 * Inner vertical split inside content: [Fixed(3) header | Fill body | Fixed(3) footer]
 *
 * Each resolved sub-rect is rendered as a labelled bordered box, proving the
 * engine produces the expected geometry through the existing Layer 2 stack.
 */
object LayoutDemoPanel:

  private val regionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  def show: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Layout Engine — Constraint-Based Geometry")

      // Description line just above the working area
      canvas.putText(
        0,
        DemoUtils.ContentStartY,
        "Outer Horizontal: [Fixed(20) | Fill | Fixed(15)]   Inner Vertical: [Fixed(3) | Fill | Fixed(3)]",
        DemoUtils.DimStyle
      )

      val workArea = Rect(0, DemoUtils.ContentStartY + 2, 78, 18)
      val outer = Layout.horizontal(
        Constraint.Fixed(20),
        Constraint.Fill,
        Constraint.Fixed(15)
      )
      val inner = Layout.vertical(
        Constraint.Fixed(3),
        Constraint.Fill,
        Constraint.Fixed(3)
      )

      val outerRects = LayoutEngine.split(outer, workArea)
      val sidebar    = outerRects.head
      val centre     = outerRects(1)
      val palette    = outerRects(2)

      val innerRects   = centre.split(inner)
      val centreHeader = innerRects.head
      val centreBody   = innerRects(1)
      val centreFooter = innerRects(2)

      drawLabelledRegion(canvas, sidebar,      "Sidebar",       "Fixed(20)")
      drawLabelledRegion(canvas, palette,      "Palette",       "Fixed(15)")
      drawLabelledRegion(canvas, centreHeader, "Centre Header", "Fixed(3)")
      drawLabelledRegion(canvas, centreBody,   "Content",       "Fill")
      drawLabelledRegion(canvas, centreFooter, "Centre Footer", "Fixed(3)")
    }

  private def drawLabelledRegion(
    canvas:     Canvas,
    rect:       Rect,
    label:      String,
    constraint: String
  ): Unit =
    if rect.isEmpty || rect.width < 2 || rect.height < 2 then return

    canvas.drawBox(rect, BoxStyle.Single, None, regionStyle)

    val maxText = math.max(0, rect.width - 4)

    if rect.height >= 3 && maxText > 0 then
      canvas.putText(rect.x + 2, rect.y + 1, label.take(maxText), labelStyle)

    if rect.height >= 5 && maxText > 0 then
      val dimsLine = s"$constraint  ${rect.width}x${rect.height}"
      canvas.putText(rect.x + 2, rect.y + 2, dimsLine.take(maxText), DemoUtils.DimStyle)
