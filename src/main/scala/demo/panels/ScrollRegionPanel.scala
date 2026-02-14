package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.DemoUtils
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates scroll regions: a fixed header and status bar with
 * content scrolling in between.
 */
object ScrollRegionPanel:

  def show: ZIO[Any, IOException, Unit] =
    for
      _ <- setup
      _ <- animate
      _ <- cleanup
    yield ()

  private val setup: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .clearScreen.home
        // Header (rows 1-3)
        .moveTo(1, 1).fg(FgColor.BrightCyan).bold
        .text("  Scroll Region Demo").reset
        .moveTo(2, 1).fg(FgColor.BrightCyan)
        .text("  " + "─" * 76).reset
        // Status bar (row 24)
        .moveTo(24, 1).reverse.fg(FgColor.BrightWhite)
        .text(f"${"  Status: Starting..."}%-78s").reset
        // Set scroll region (rows 4-22)
        .setScrollRegion(4, 22)
        .moveTo(4, 1)
    )

  private val animate: ZIO[Any, IOException, Unit] =
    ZIO.foreachDiscard(1 to 40) { lineNum =>
      val color = lineNum % 6 match
        case 0 => FgColor.BrightRed
        case 1 => FgColor.BrightGreen
        case 2 => FgColor.BrightYellow
        case 3 => FgColor.BrightBlue
        case 4 => FgColor.BrightMagenta
        case _ => FgColor.BrightCyan

      val content = f"  Line $lineNum%3d: " + "░▒▓█" * (lineNum % 8 + 2)

      DemoUtils.printAnsi(
        AnsiBuilder()
          .fg(color).text(content).reset.newline
      ) *>
      // Update status bar without affecting scroll region
      DemoUtils.printAnsi(
        AnsiBuilder()
          .saveCursor
          .moveTo(24, 1).reverse.fg(FgColor.BrightWhite)
          .text(f"${"  Status: Lines printed: " + lineNum + " / 40"}%-78s").reset
          .restoreCursor
      ) *>
      ZIO.sleep(zio.Duration.fromMillis(150))
    }

  private val cleanup: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .resetScrollRegion
        .saveCursor
        .moveTo(24, 1).reverse.fg(FgColor.BrightGreen)
        .text(f"${"  Status: Scroll demo complete!"}%-78s").reset
        .restoreCursor
    )
