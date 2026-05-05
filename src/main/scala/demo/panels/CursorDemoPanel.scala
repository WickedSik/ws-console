package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates absolute positioning, box drawing at specific coordinates,
 * and writes that appear at scattered positions in any order.
 *
 * Note: the original Layer 1 panel demonstrated cursor save/restore, which
 * is a stream-based concept. In Layer 2, all writes target absolute (x, y)
 * cells regardless of write order — order is irrelevant to the final image.
 * This is a deliberate change in *what* is demonstrated, even though the
 * visible output remains positional.
 */
object CursorDemoPanel:

  private val yellowStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow))

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val redHello     = CellStyle(fg = Foreground.Named(FgColor.BrightRed),     attributes = Set(Attribute.Bold))
  private val greenHello   = CellStyle(fg = Foreground.Named(FgColor.BrightGreen),   attributes = Set(Attribute.Bold))
  private val blueHello    = CellStyle(fg = Foreground.Named(FgColor.BrightBlue),    attributes = Set(Attribute.Bold))
  private val magentaHello = CellStyle(fg = Foreground.Named(FgColor.BrightMagenta), attributes = Set(Attribute.Bold))
  private val yellowHello  = CellStyle(fg = Foreground.Named(FgColor.BrightYellow),  attributes = Set(Attribute.Bold))

  private val cyanStyle  = CellStyle(fg = Foreground.Named(FgColor.Cyan))
  private val greenStyle = CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  def show: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Cursor Positioning Demo")

      val boxRect = Rect(4, 5, 30, 5)
      canvas.drawBox(boxRect, BoxStyle.Single, None, yellowStyle)
      canvas.putText(7, 7, "Drawn via cell coords", labelStyle)
      canvas.putText(4, 11, "Box drawn at absolute coordinates (x=4, y=5)", DemoUtils.DimStyle)

      // Scattered "Hello" writes — order in code does not affect output.
      canvas.putText(44, 6,  "Hello", redHello)
      canvas.putText(49, 8,  "Hello", greenHello)
      canvas.putText(54, 10, "Hello", blueHello)
      canvas.putText(59, 7,  "Hello", magentaHello)
      canvas.putText(41, 9,  "Hello", yellowHello)
      canvas.putText(47, 11, "(5 positions, 5 colors)", DemoUtils.DimStyle)

      // The "interleaved" demonstration: writes to row 14 and row 16 in any order
      // produce the same final image — Layer 2 is positional, not stream-based.
      canvas.putText(4,  14, "Writing here... ",            cyanStyle)
      canvas.putText(20, 14, "...continued after restore!", greenStyle)
      canvas.putText(19, 16, "[Jumped away!]",              CellStyle(fg = Foreground.Named(FgColor.BrightRed)))
      canvas.putText(4,  18, "All positions written; order in code is irrelevant.", DemoUtils.DimStyle)
    }
