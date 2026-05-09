package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Renderer}
import component.{Alignment, Component, Panel, RawCanvas, Spacer, Text, VBox}
import demo.DemoUtils
import geometry.Rect
import layout.Constraint
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates absolute positioning, box drawing at specific coordinates,
 * and writes that appear at scattered positions in any order.
 *
 * The point of this panel is to demonstrate a Layer 2 invariant — writes
 * to (x, y) cells are positional, not stream-based, so order in code is
 * irrelevant to the final image. That property is independent of Layer
 * 4's structural decomposition; expressing each scattered "Hello" as its
 * own component would *obscure* the very thing the panel demonstrates.
 *
 * The body is wrapped in a `RawCanvas` so the panel lives inside the
 * component tree (consistent with the rest of the demo) while preserving
 * the positional-write narrative.
 */
object CursorDemoPanel:

  private val yellowStyle = CellStyle(fg = Foreground.Named(FgColor.BrightYellow))
  private val labelStyle  = CellStyle(fg = Foreground.Named(FgColor.BrightWhite),  attributes = Set(Attribute.Bold))

  private val redHello     = CellStyle(fg = Foreground.Named(FgColor.BrightRed),     attributes = Set(Attribute.Bold))
  private val greenHello   = CellStyle(fg = Foreground.Named(FgColor.BrightGreen),   attributes = Set(Attribute.Bold))
  private val blueHello    = CellStyle(fg = Foreground.Named(FgColor.BrightBlue),    attributes = Set(Attribute.Bold))
  private val magentaHello = CellStyle(fg = Foreground.Named(FgColor.BrightMagenta), attributes = Set(Attribute.Bold))
  private val yellowHello  = CellStyle(fg = Foreground.Named(FgColor.BrightYellow),  attributes = Set(Attribute.Bold))

  private val cyanStyle  = CellStyle(fg = Foreground.Named(FgColor.Cyan))
  private val greenStyle = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  private val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style  = DemoUtils.HeaderStyle,
      child  = Text("Cursor Positioning Demo", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill     -> RawCanvas { canvas =>
      val boxRect = Rect(4, 1, 30, 5)
      canvas.drawBox(boxRect, BoxStyle.Single, None, yellowStyle)
      canvas.putText(7, 3, "Drawn via cell coords", labelStyle)
      canvas.putText(4, 7, "Box drawn at sub-canvas-relative coords (4, 1)", DemoUtils.DimStyle)

      // Scattered "Hello" writes — order in code does not affect output.
      canvas.putText(44, 2,  "Hello", redHello)
      canvas.putText(49, 4,  "Hello", greenHello)
      canvas.putText(54, 6,  "Hello", blueHello)
      canvas.putText(59, 3,  "Hello", magentaHello)
      canvas.putText(41, 5,  "Hello", yellowHello)
      canvas.putText(47, 7,  "(5 positions, 5 colors)", DemoUtils.DimStyle)

      // Writes to row 10 and row 12 in any order produce the same final image.
      canvas.putText(4,  10, "Writing here... ",            cyanStyle)
      canvas.putText(20, 10, "...continued elsewhere!",     greenStyle)
      canvas.putText(19, 12, "[Jumped away!]",              CellStyle(fg = Foreground.Named(FgColor.BrightRed)))
      canvas.putText(4,  14, "All positions written; order in code is irrelevant.", DemoUtils.DimStyle)
    }
  )

  def show: ZIO[Renderer, IOException, Unit] =
    Renderer.frame(tree)
