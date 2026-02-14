package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.{BoxDrawing, DemoUtils}
import zio.ZIO

import java.io.IOException

/**
 * Title screen panel introducing the ws-console demo.
 */
object WelcomePanel:

  def show: ZIO[Any, IOException, Unit] =
    val width = 78
    val inner = width - 2
    val dh = BoxDrawing.doubleHorizontalLine(inner)
    val dv = BoxDrawing.DoubleVertical

    def centeredLine(text: String): String =
      dv + DemoUtils.centeredText(text, inner) + dv

    val emptyLine = centeredLine("")

    DemoUtils.printAnsi(
      AnsiBuilder()
        .clearScreen.home
        .newline
        // Top border
        .fg(FgColor.BrightCyan).text("  " + BoxDrawing.DoubleTopLeft + dh + BoxDrawing.DoubleTopRight).reset.newline
        // Empty line
        .fg(FgColor.BrightCyan).text("  " + emptyLine).reset.newline
        // Title
        .fg(FgColor.BrightCyan).text("  " + dv)
        .bold.fg(FgColor.BrightCyan).text(DemoUtils.centeredText("ws-console", inner))
        .reset.fg(FgColor.BrightCyan).text(dv).reset.newline
        // Empty line
        .fg(FgColor.BrightCyan).text("  " + emptyLine).reset.newline
        // Subtitle
        .fg(FgColor.BrightCyan).text("  " + dv)
        .italic.fg(FgColor.White).text(DemoUtils.centeredText("ZIO-Native Terminal Graphics Library", inner))
        .reset.fg(FgColor.BrightCyan).text(dv).reset.newline
        // Empty line
        .fg(FgColor.BrightCyan).text("  " + emptyLine).reset.newline
        // Phase info
        .fg(FgColor.BrightCyan).text("  " + dv)
        .dim.fg(FgColor.White).text(DemoUtils.centeredText("Phase 1: ANSI Primitives", inner))
        .reset.fg(FgColor.BrightCyan).text(dv).reset.newline
        // Empty line
        .fg(FgColor.BrightCyan).text("  " + emptyLine).reset.newline
        // Instructions
        .fg(FgColor.BrightCyan).text("  " + dv)
        .dim.fg(FgColor.BrightBlack).text(DemoUtils.centeredText("Auto-advancing demo  |  CTRL+C to exit", inner))
        .reset.fg(FgColor.BrightCyan).text(dv).reset.newline
        // Empty line
        .fg(FgColor.BrightCyan).text("  " + emptyLine).reset.newline
        // Bottom border
        .fg(FgColor.BrightCyan).text("  " + BoxDrawing.DoubleBottomLeft + dh + BoxDrawing.DoubleBottomRight).reset.newline
    )
