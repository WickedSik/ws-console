package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.{BoxDrawing, DemoUtils}
import zio.ZIO

import java.io.IOException

/**
 * Animated braille-dot spinner demonstration.
 * Runs for approximately 5 seconds then shows completion.
 */
object SpinnerPanel:

  def show: ZIO[Any, IOException, Unit] =
    for
      _ <- DemoUtils.clearAndHeader("Spinner Animation")
      _ <- animate
      _ <- complete
    yield ()

  private val spinnerRow = 8
  private val spinnerCol = 10

  private val animate: ZIO[Any, IOException, Unit] =
    ZIO.foreachDiscard(0 until 60) { frame =>
      val spinChar = BoxDrawing.Spinner(frame % BoxDrawing.Spinner.length)
      DemoUtils.printAnsi(
        AnsiBuilder()
          .moveTo(spinnerRow, spinnerCol).clearLine
          .text("  ").fg(FgColor.BrightCyan).bold.text(spinChar).reset
          .text("  Processing data...")
      ) *>
      ZIO.sleep(zio.Duration.fromMillis(80))
    }

  private val complete: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .moveTo(spinnerRow, spinnerCol).clearLine
        .text("  ").fg(FgColor.BrightGreen).bold.text("✓").reset
        .text("  Done!")
        .moveTo(spinnerRow + 2, spinnerCol)
        .dim.text("  60 frames at 80ms using braille dot characters").reset
    )
