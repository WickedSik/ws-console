package io.github.wickedsik.wsconsole
package demo

import ansi.{AnsiBuilder, FgColor}
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import geometry.Rect
import terminal.Terminal
import unicode.BoxDrawing
import zio.ZIO

import java.io.IOException

/**
 * Shared rendering utilities used across all demo panels.
 *
 * Two families of helpers live here:
 *   - Canvas-based helpers (`drawHeader`, `drawSectionLabel`, style constants)
 *     used by panels migrated to Layer 2.
 *   - Terminal-based helpers (`printAnsi`, `clearAndHeader`, `sectionLabel`)
 *     retained for ScrollRegionPanel which deliberately uses Layer 1 directly.
 */
object DemoUtils:

  // ===== Common Cell Styles =====

  val HeaderStyle: CellStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  val SectionLabelStyle: CellStyle =
    CellStyle(fg = Foreground.Named(FgColor.Cyan), attributes = Set(Attribute.Bold, Attribute.Underline))

  val DimStyle: CellStyle =
    CellStyle(attributes = Set(Attribute.Dim))

  /** Y-coordinate where panel content can begin (just below the header box). */
  val ContentStartY: Int = 4

  // ===== Canvas helpers =====

  /**
   * Draw a double-line bordered title box at the top of `canvas`.
   * Box occupies rows 0-2; row 3 is left blank as a separator.
   */
  def drawHeader(canvas: Canvas, title: String, width: Int = 78): Unit =
    val innerWidth = width - 2
    canvas.drawBox(Rect(0, 0, width, 3), BoxStyle.Double, None, HeaderStyle)
    canvas.putText(1, 1, centeredText(title, innerWidth), HeaderStyle)

  /** Draw a styled section label (bold + cyan + underline) at (x, y). */
  def drawSectionLabel(canvas: Canvas, x: Int, y: Int, label: String): Unit =
    canvas.putText(x, y, label, SectionLabelStyle)

  // ===== Terminal helpers (used by ScrollRegionPanel) =====

  /** Write an AnsiBuilder's output via the Terminal service */
  def printAnsi(builder: AnsiBuilder): ZIO[Terminal, IOException, Unit] =
    Terminal.writeBuilder(builder)

  /**
   * Clear the screen, home the cursor, and draw a double-line bordered title box.
   * Leaves the cursor on the line below the box (row 5).
   */
  def clearAndHeader(title: String, width: Int = 78): ZIO[Terminal, IOException, Unit] =
    val innerWidth = width - 2
    val padded = centeredText(title, innerWidth)
    val style = BoxDrawing.DoubleLine
    val top = s"${style.topLeft}${style.horizontalLine(innerWidth)}${style.topRight}"
    val mid = s"${style.vertical}$padded${style.vertical}"
    val bot = s"${style.bottomLeft}${style.horizontalLine(innerWidth)}${style.bottomRight}"

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

  // ===== Pure utilities =====

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
