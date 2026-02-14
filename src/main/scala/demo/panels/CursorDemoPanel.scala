package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.{BoxDrawing, DemoUtils}
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates cursor positioning, box drawing via absolute coordinates,
 * and cursor save/restore functionality.
 */
object CursorDemoPanel:

  def show: ZIO[Any, IOException, Unit] =
    for
      _ <- DemoUtils.clearAndHeader("Cursor Positioning Demo")
      _ <- boxDrawingDemo
      _ <- multiPositionText
      _ <- saveRestoreDemo
    yield ()

  /** Draw a single-line box at specific coordinates with text inside */
  private val boxDrawingDemo: ZIO[Any, Nothing, Unit] =
    val boxTop = 6
    val boxLeft = 5
    val boxWidth = 30
    val boxHeight = 5

    val topLine = BoxDrawing.TopLeft + BoxDrawing.horizontalLine(boxWidth - 2) + BoxDrawing.TopRight
    val botLine = BoxDrawing.BottomLeft + BoxDrawing.horizontalLine(boxWidth - 2) + BoxDrawing.BottomRight
    val emptyLine = BoxDrawing.Vertical + " " * (boxWidth - 2) + BoxDrawing.Vertical

    var builder = AnsiBuilder()
      .moveTo(boxTop, boxLeft).fg(FgColor.BrightYellow).text(topLine).reset

    for row <- 1 until boxHeight - 1 do
      builder = builder.moveTo(boxTop + row, boxLeft).fg(FgColor.BrightYellow).text(emptyLine).reset

    builder = builder
      .moveTo(boxTop + boxHeight - 1, boxLeft).fg(FgColor.BrightYellow).text(botLine).reset
      // Place text inside the box
      .moveTo(boxTop + 2, boxLeft + 3).fg(FgColor.BrightWhite).bold.text("Drawn via moveTo()").reset

    // Label
    builder = builder.moveTo(boxTop + boxHeight + 1, boxLeft)
      .dim.text("Box drawn with absolute cursor positioning").reset

    DemoUtils.printAnsi(builder)

  /** Write text at scattered positions in different colors */
  private val multiPositionText: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .moveTo(7, 45).fg(FgColor.BrightRed).bold.text("Hello").reset
        .moveTo(9, 50).fg(FgColor.BrightGreen).bold.text("Hello").reset
        .moveTo(11, 55).fg(FgColor.BrightBlue).bold.text("Hello").reset
        .moveTo(8, 60).fg(FgColor.BrightMagenta).bold.text("Hello").reset
        .moveTo(10, 42).fg(FgColor.BrightYellow).bold.text("Hello").reset
        .moveTo(12, 48).dim.text("(5 positions, 5 colors)").reset
    )

  /** Demonstrate cursor save and restore */
  private val saveRestoreDemo: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .moveTo(15, 5).fg(FgColor.Cyan).text("Writing here... ").reset
        .saveCursor
        .moveTo(17, 20).fg(FgColor.BrightRed).text("[Jumped away!]").reset
        .restoreCursor
        .fg(FgColor.BrightGreen).text("...continued after restore!").reset
        .moveTo(19, 5).dim.text("Cursor saved, jumped to row 17, then restored to continue on row 15").reset
    )
