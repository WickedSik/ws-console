package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, Canvas, CellStyle, Foreground, Renderer}
import demo.DemoUtils
import unicode.SequencedDrawing

import zio.ZIO

import java.io.IOException

/**
 * Animated progress bar using Unicode block elements for sub-character precision.
 *
 * Each step rewrites the whole bar; the diff engine emits only the cells that
 * actually changed (typically just the trailing edge of the bar plus the
 * percentage label).
 */
object ProgressBarPanel:

  private val Steps        = 100
  private val StepDelayMs  = 30L

  private val barRow   = 7
  private val barCol   = 2
  private val barWidth = 60

  private val filledStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  private val percentStyle =
    CellStyle(attributes = Set(Attribute.Bold))

  def show: ZIO[Renderer, IOException, Unit] =
    for
      _ <- animate
      _ <- complete
    yield ()

  private val animate: ZIO[Renderer, IOException, Unit] =
    ZIO.foreachDiscard(0 to Steps) { percent =>
      Renderer.frame { canvas =>
        DemoUtils.drawHeader(canvas, "Progress Bar")
        drawBar(canvas, percent)
      }.zipLeft(ZIO.sleep(zio.Duration.fromMillis(StepDelayMs)))
    }

  private val complete: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Progress Bar")
      drawBar(canvas, 100)
      canvas.putText(barCol, barRow + 2, "Complete!",
        CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Bold)))
      canvas.putText(barCol, barRow + 4, "60-char bar with 8-level sub-character precision (480 steps)",
        DemoUtils.DimStyle)
    }

  private def drawBar(canvas: Canvas, percent: Int): Unit =
    val totalUnits   = barWidth * 8
    val filledUnits  = (percent * totalUnits) / 100
    val fullBlocks   = filledUnits / 8
    val partialIndex = filledUnits % 8

    canvas.putChar(barCol, barRow, '[')

    val fullChar    = SequencedDrawing.ProgressBar(8)
    val partialChar =
      if partialIndex > 0 then SequencedDrawing.ProgressBar(partialIndex) else ' '

    var x = 0
    while x < fullBlocks do
      canvas.putChar(barCol + 1 + x, barRow, fullChar, filledStyle)
      x += 1

    if partialIndex > 0 then
      canvas.putChar(barCol + 1 + fullBlocks, barRow, partialChar, filledStyle)

    val emptyStart = if partialIndex > 0 then fullBlocks + 1 else fullBlocks
    var e = emptyStart
    while e < barWidth do
      canvas.putChar(barCol + 1 + e, barRow, ' ')
      e += 1

    canvas.putChar(barCol + 1 + barWidth, barRow, ']')
    canvas.putText(barCol + 3 + barWidth, barRow, f"$percent%3d%%", percentStyle)
