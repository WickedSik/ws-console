package io.github.wickedsik.wsconsole
package demo

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import event.{KeyEvent, KeyModifier}
import event.KeyEvent.CharKey
import geometry.Rect
import terminal.Terminal
import zio.ZIO

import java.io.IOException

/**
 * Shared rendering utilities for demo panels.
 *
 * All helpers operate on a Layer 2 [[Canvas]]. No panel reaches for the
 * `Terminal` service or `AnsiBuilder` directly.
 */
object DemoUtils:

  // ===== Common Cell Styles =====

  val HeaderStyle: CellStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  val SectionLabelStyle: CellStyle =
    CellStyle(fg = Foreground.Named(FgColor.Cyan), attributes = Set(Attribute.Bold, Attribute.Underline))

  val DimStyle: CellStyle =
    CellStyle(attributes = Set(Attribute.Dim))

  /** Row offset (from `area.y`) where panel content can begin — one blank row below the header box. */
  val ContentStartY: Int = 4

  // ===== Canvas helpers =====

  /**
   * Draw a double-line bordered title box across the top of `area`.
   * The box occupies `area.y` through `area.y + 2`; row `area.y + 3`
   * is left blank as a separator before content.
   */
  def drawHeader(canvas: Canvas, area: Rect, title: String): Unit =
    if area.width < 2 || area.height < 3 then return
    val innerWidth = area.width - 2
    canvas.drawBox(Rect(area.x, area.y, area.width, 3), BoxStyle.Double, style = HeaderStyle)
    canvas.putText(area.x + 1, area.y + 1, centeredText(title, innerWidth), HeaderStyle)

  /** Draw a styled section label (bold + cyan + underline) at (x, y). */
  def drawSectionLabel(canvas: Canvas, x: Int, y: Int, label: String): Unit =
    canvas.putText(x, y, label, SectionLabelStyle)

  // ===== Pure utilities =====

  /**
   * Sleep for the given number of seconds. Retained for animated panels'
   *  internal frame timing; not used between static panels post-Layer 5.
   */
  def pause(seconds: Int): ZIO[Any, Nothing, Unit] =
    ZIO.sleep(zio.Duration.fromSeconds(seconds.toLong))

  /**
   * Block until the user presses a key. Consumes one [[KeyEvent]] from
   * [[Terminal.events]] and returns it.
   *
   * Raw mode must already be active for this to receive raw bytes - see
   * `DemoApp.run`'s scoped acquire of `Terminal.enterRawMode`.
   */
  val waitForKey: ZIO[Terminal, IOException, KeyEvent] =
    Terminal.events.collect { case k: KeyEvent => k }.runHead.flatMap {
      case Some(k) => ZIO.succeed(k)
      case None    => ZIO.fail(new IOException("Terminal input ended before keypress"))
    }

  /**
   * Is this key one of the demo's exit triggers? `q` for graceful quit, or
   * `Ctrl+C` (which in raw mode is a parsed event, not a SIGINT).
   */
  def isExitKey(key: KeyEvent): Boolean =
    key match
      case CharKey('q', _)                              => true
      case CharKey('c', mods) if mods(KeyModifier.Ctrl) => true
      case _                                            => false

  /** Center text within the given width by padding with spaces */
  def centeredText(text: String, width: Int): String =
    if text.length >= width then text.take(width)
    else
      val leftPad = (width - text.length) / 2
      val rightPad = width - text.length - leftPad
      " " * leftPad + text + " " * rightPad
