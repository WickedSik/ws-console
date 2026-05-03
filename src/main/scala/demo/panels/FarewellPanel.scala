package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Final panel: summary of what was showcased and preview of future phases.
 *
 * Static one-shot panel rendered as a single frame.
 */
object FarewellPanel:

  private val borderStyle    = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))
  private val titleStyle     = CellStyle(fg = Foreground.Named(FgColor.BrightGreen),  attributes = Set(Attribute.Bold))
  private val highlightStyle = CellStyle(fg = Foreground.Named(FgColor.White))
  private val futureStyle    = CellStyle(fg = Foreground.Named(FgColor.BrightYellow))
  private val exitStyle      = CellStyle(fg = Foreground.Named(FgColor.BrightBlack),  attributes = Set(Attribute.Dim))

  private val showcased = List(
    "16-color, 256-color, and true-color RGB",
    "Text styles: bold, dim, italic, underline, strikethrough",
    "Cursor positioning, save/restore",
    "Scroll regions with fixed status bar",
    "Braille spinner and block progress bar"
  )

  private val future = List(
    "Pattern parsing and colorization",
    "Text wrapping with ANSI preservation",
    "Terminal capability detection",
    "Full Terminal trait implementation"
  )

  def show: ZIO[Renderer, IOException, Unit] =
    val width = 78
    val inner = width - 2
    val boxX  = 2
    val boxY  = 1
    // 2 borders + 1 empty + title + empty + heading + 5 items + empty + heading + 4 items + empty + exit + empty
    val boxH  = 2 + 1 + 1 + 1 + 1 + showcased.size + 1 + 1 + future.size + 1 + 1 + 1
    val rect  = Rect(boxX, boxY, width, boxH)

    Renderer.frame { canvas =>
      canvas.drawBox(rect, BoxStyle.Double, None, borderStyle)

      val contentX = boxX + 1
      var y = boxY + 2

      canvas.putText(contentX, y, DemoUtils.centeredText("Demo Complete", inner), titleStyle); y += 2
      canvas.putText(contentX, y, DemoUtils.centeredText("Showcased in this demo:", inner), highlightStyle); y += 1
      showcased.foreach { item =>
        canvas.putText(contentX, y, DemoUtils.centeredText(item, inner), DemoUtils.DimStyle)
        y += 1
      }
      y += 1
      canvas.putText(contentX, y, DemoUtils.centeredText("Future phases will add:", inner), futureStyle); y += 1
      future.foreach { item =>
        canvas.putText(contentX, y, DemoUtils.centeredText(item, inner), DemoUtils.DimStyle)
        y += 1
      }
      y += 1
      canvas.putText(contentX, y, DemoUtils.centeredText("Exiting in 3 seconds...", inner), exitStyle)
    }.unit
