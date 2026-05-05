package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Title screen panel introducing the ws-console demo.
 *
 * Static one-shot panel: builds the entire frame, renders once.
 */
object WelcomePanel:

  private val borderStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val titleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val subtitleStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Italic))

  private val phaseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Dim))

  private val instructionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  def show: ZIO[Renderer, IOException, Unit] =
    val width   = 78
    val inner   = width - 2
    val boxX    = 2
    val boxY    = 1
    val boxRect = Rect(boxX, boxY, width, 11)

    Renderer.frame { canvas =>
      canvas.drawBox(boxRect, BoxStyle.Double, None, borderStyle)

      val contentX = boxX + 1
      canvas.putText(contentX, boxY + 2, DemoUtils.centeredText("ws-console", inner), titleStyle)
      canvas.putText(contentX, boxY + 4, DemoUtils.centeredText("ZIO-Native Terminal Graphics Library", inner), subtitleStyle)
      canvas.putText(contentX, boxY + 6, DemoUtils.centeredText("Phase 2: Buffer & Cell Management", inner), phaseStyle)
      canvas.putText(contentX, boxY + 8, DemoUtils.centeredText("Auto-advancing demo  |  CTRL+C to exit", inner), instructionStyle)
    }
