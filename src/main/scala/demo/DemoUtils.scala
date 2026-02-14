package io.github.wickedsik.wsconsole
package demo

import ansi.{AnsiBuilder, FgColor}
import zio.ZIO

/**
 * Shared rendering utilities used across all demo panels.
 */
object DemoUtils:

  /** Print an AnsiBuilder's output and flush stdout */
  def printAnsi(builder: AnsiBuilder): ZIO[Any, Nothing, Unit] =
    ZIO.succeed {
      print(builder.build)
      System.out.flush()
    }

  /**
   * Clear the screen, home the cursor, and draw a double-line bordered title box.
   * Leaves the cursor on the line below the box (row 5).
   */
  def clearAndHeader(title: String, width: Int = 78): ZIO[Any, Nothing, Unit] =
    val innerWidth = width - 2
    val padded = centeredText(title, innerWidth)
    val top = BoxDrawing.DoubleTopLeft + BoxDrawing.doubleHorizontalLine(innerWidth) + BoxDrawing.DoubleTopRight
    val mid = BoxDrawing.DoubleVertical + padded + BoxDrawing.DoubleVertical
    val bot = BoxDrawing.DoubleBottomLeft + BoxDrawing.doubleHorizontalLine(innerWidth) + BoxDrawing.DoubleBottomRight

    printAnsi(
      AnsiBuilder()
        .clearScreen.home
        .moveTo(1, 1).fg(FgColor.BrightCyan).bold.text(top).reset.newline
        .fg(FgColor.BrightCyan).bold.text(mid).reset.newline
        .fg(FgColor.BrightCyan).bold.text(bot).reset.newline
        .newline
    )

  /** Return a styled section label builder (bold + cyan + underline) */
  def sectionLabel(label: String): AnsiBuilder =
    AnsiBuilder().bold.fg(FgColor.Cyan).underline.text(label).reset

  /** Sleep for the given number of seconds */
  def pause(seconds: Int): ZIO[Any, Nothing, Unit] =
    ZIO.sleep(zio.Duration.fromSeconds(seconds.toLong))

  /** Center text within the given width by padding with spaces */
  def centeredText(text: String, width: Int): String =
    if text.length >= width then text.take(width)
    else
      val leftPad = (width - text.length) / 2
      val rightPad = width - text.length - leftPad
      " " * leftPad + text + " " * rightPad
