package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.{BoxDrawing, DemoUtils}
import terminal.Terminal
import zio.ZIO

import java.io.IOException

/**
 * Final panel: summary of what was showcased and preview of future phases.
 */
object FarewellPanel:

  def show: ZIO[Terminal, IOException, Unit] =
    val width = 78
    val inner = width - 2
    val dh = BoxDrawing.doubleHorizontalLine(inner)
    val dv = BoxDrawing.DoubleVertical

    def framedLine(content: AnsiBuilder): AnsiBuilder =
      AnsiBuilder().fg(FgColor.BrightCyan).text("  " + dv).reset
        ++ content
        ++ AnsiBuilder().fg(FgColor.BrightCyan).text(dv).reset.newline

    def textLine(text: String): AnsiBuilder =
      framedLine(AnsiBuilder().text(DemoUtils.centeredText(text, inner)))

    def emptyLine: AnsiBuilder =
      framedLine(AnsiBuilder().text(" " * inner))

    DemoUtils.printAnsi(
      AnsiBuilder().clearScreen.home.newline
        // Top border
        .fg(FgColor.BrightCyan).text("  " + BoxDrawing.DoubleTopLeft + dh + BoxDrawing.DoubleTopRight).reset.newline
        ++ emptyLine
        ++ framedLine(AnsiBuilder().bold.fg(FgColor.BrightGreen).text(DemoUtils.centeredText("Demo Complete", inner)).reset)
        ++ emptyLine
        ++ framedLine(AnsiBuilder().fg(FgColor.White).text(DemoUtils.centeredText("Showcased in this demo:", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("16-color, 256-color, and true-color RGB", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Text styles: bold, dim, italic, underline, strikethrough", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Cursor positioning, save/restore", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Scroll regions with fixed status bar", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Braille spinner and block progress bar", inner)).reset)
        ++ emptyLine
        ++ framedLine(AnsiBuilder().fg(FgColor.BrightYellow).text(DemoUtils.centeredText("Future phases will add:", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Pattern parsing and colorization", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Text wrapping with ANSI preservation", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Terminal capability detection", inner)).reset)
        ++ framedLine(AnsiBuilder().dim.text(DemoUtils.centeredText("Full Terminal trait implementation", inner)).reset)
        ++ emptyLine
        ++ framedLine(AnsiBuilder().dim.fg(FgColor.BrightBlack).text(DemoUtils.centeredText("Exiting in 3 seconds...", inner)).reset)
        ++ emptyLine
        ++ AnsiBuilder().fg(FgColor.BrightCyan).text("  " + BoxDrawing.DoubleBottomLeft + dh + BoxDrawing.DoubleBottomRight).reset.newline
    )
