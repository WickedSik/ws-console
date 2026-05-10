package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Frame}
import component.{Alignment, Component, HBox, Panel, Spacer, Text, VBox}
import demo.DemoUtils
import geometry.Rect
import layout.Constraint
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates Layer 3 constraint-based layout, expressed via the Layer 4
 * component tree.
 *
 * The previous incarnation of this panel had to manually call
 * `LayoutEngine.split` and pluck rects out by index, then call
 * `drawLabelledRegion(canvas, sidebar, ...)` for each piece. The
 * declarative tree below produces the same geometry with no per-region
 * boilerplate — the constraint and the component for each cell live as
 * a single pair, so they cannot drift.
 */
object LayoutDemoPanel:

  private val regionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  /** A bordered region with a title and a constraint label inside. */
  private def region(name: String, constraint: String): Component =
    Panel(
      border = BoxStyle.Single,
      style  = regionStyle,
      child  = VBox(
        Constraint.Fixed(1) -> Text(name, labelStyle),
        Constraint.Fixed(1) -> Text(constraint, DemoUtils.DimStyle),
        Constraint.Fill     -> Spacer
      )
    )

  private val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style  = DemoUtils.HeaderStyle,
      child  = Text("Layout Engine — Constraint-Based Geometry", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Text(
      "Outer Horizontal: [Fixed(20) | Fill | Fixed(15)]   Inner Vertical: [Fixed(3) | Fill | Fixed(3)]",
      DemoUtils.DimStyle
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill     -> HBox(
      Constraint.Fixed(20) -> region("Sidebar", "Fixed(20)"),
      Constraint.Fill      -> Panel(
        border = BoxStyle.Single,
        style  = regionStyle,
        title  = Some("Content"),
        child  = VBox(
          Constraint.Fixed(3) -> region("Centre Header", "Fixed(3)"),
          Constraint.Fill     -> region("Body",          "Fill"),
          Constraint.Fixed(3) -> region("Centre Footer", "Fixed(3)")
        )
      ),
      Constraint.Fixed(15) -> region("Palette", "Fixed(15)")
    )
  )

  def show: ZIO[Frame, IOException, Unit] =
    Frame.run(tree)
