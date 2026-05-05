package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, Canvas, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates all available text styles and style combinations.
 *
 * Static panel rendered as a single frame.
 */
object StyleShowcasePanel:

  def show: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Style Showcase")
      drawIndividualStyles(canvas, DemoUtils.ContentStartY)
      drawCombinedStyles(canvas, DemoUtils.ContentStartY + 10)
    }

  private def drawIndividualStyles(canvas: Canvas, startY: Int): Unit =
    DemoUtils.drawSectionLabel(canvas, 0, startY, "Individual Styles")

    val rows = List(
      "Bold text"          -> Set(Attribute.Bold),
      "Dim text"           -> Set(Attribute.Dim),
      "Italic text"        -> Set(Attribute.Italic),
      "Underline text"     -> Set(Attribute.Underline),
      "Strikethrough text" -> Set(Attribute.Strikethrough),
      "Reverse video"      -> Set(Attribute.Reverse),
      "Blink text"         -> Set(Attribute.Blink)
    )

    rows.zipWithIndex.foreach { case ((label, attrs), i) =>
      canvas.putText(2, startY + 1 + i, label, CellStyle(attributes = attrs))
    }
    // Note next to "Blink text" — terminal support varies
    canvas.putText(2 + "Blink text".length, startY + 7, "  (terminal support varies)", DemoUtils.DimStyle)

  private def drawCombinedStyles(canvas: Canvas, startY: Int): Unit =
    DemoUtils.drawSectionLabel(canvas, 0, startY, "Style Combinations")

    val combos = List(
      ("Bold + Italic",                              CellStyle(attributes = Set(Attribute.Bold, Attribute.Italic))),
      ("Bold + Underline + Cyan",                    CellStyle(fg = Foreground.Named(FgColor.Cyan), attributes = Set(Attribute.Bold, Attribute.Underline))),
      ("Dim + Italic + Magenta",                     CellStyle(fg = Foreground.Named(FgColor.Magenta), attributes = Set(Attribute.Dim, Attribute.Italic))),
      ("Bold + Yellow + Underline + Strikethrough",  CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold, Attribute.Underline, Attribute.Strikethrough))),
      ("Italic + Underline + Green",                 CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Italic, Attribute.Underline)))
    )

    combos.zipWithIndex.foreach { case ((label, style), i) =>
      canvas.putText(2, startY + 1 + i, label, style)
    }
