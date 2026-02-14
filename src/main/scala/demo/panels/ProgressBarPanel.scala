package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.{BoxDrawing, DemoUtils}
import terminal.Terminal
import zio.ZIO

import java.io.IOException

/**
 * Animated progress bar using Unicode block elements for sub-character precision.
 * Runs for approximately 3 seconds.
 */
object ProgressBarPanel:

  def show: ZIO[Terminal, IOException, Unit] =
    for
      _ <- DemoUtils.clearAndHeader("Progress Bar")
      _ <- animate
      _ <- complete
    yield ()

  private val barRow = 8
  private val barCol = 5
  private val barWidth = 60

  private val animate: ZIO[Terminal, IOException, Unit] =
    ZIO.foreachDiscard(0 to 100) { percent =>
      val totalUnits = barWidth * 8 // sub-character precision
      val filledUnits = (percent * totalUnits) / 100
      val fullBlocks = filledUnits / 8
      val partialIndex = filledUnits % 8
      val emptyBlocks = barWidth - fullBlocks - (if partialIndex > 0 then 1 else 0)

      val filledStr = BoxDrawing.BlockElements(8) * fullBlocks
      val partialStr = if partialIndex > 0 then BoxDrawing.BlockElements(partialIndex) else ""
      val emptyStr = " " * emptyBlocks

      DemoUtils.printAnsi(
        AnsiBuilder()
          .moveTo(barRow, barCol).clearLine
          .text("[")
          .fg(FgColor.BrightGreen).text(filledStr + partialStr).reset
          .text(emptyStr)
          .text("] ")
          .bold.text(f"$percent%3d%%").reset
      ) *>
      ZIO.sleep(zio.Duration.fromMillis(30))
    }

  private val complete: ZIO[Terminal, IOException, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .moveTo(barRow + 2, barCol)
        .fg(FgColor.BrightGreen).bold.text("Complete!").reset
        .moveTo(barRow + 4, barCol)
        .dim.text("60-char bar with 8-level sub-character precision (480 steps)").reset
    )
