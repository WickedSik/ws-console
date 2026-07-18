package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, Canvas, Cell, CellStyle, Foreground, Frame}
import demo.DemoUtils
import geometry.Rect
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

  private[panels] val barRow   = 7
  private[panels] val barCol   = 2
  private val barWidth = 60

  /**
   * The bounding box this panel may write into across its full lifecycle:
   *   - Header: rows 0–2 (drawn by `DemoUtils.drawHeader`)
   *   - Bar + percentage label: row 7
   *   - Completion / footer text: rows 9 and 11
   *
   * Cleared by [[clearBox]] when the animation is interrupted, so the
   * next panel inherits a blank slate without depending on the diff
   * engine catching every stale cell.
   */
  private val PanelBox: Rect = Rect(0, 0, 80, 12)

  private val filledStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  private val percentStyle =
    CellStyle(attributes = Set(Attribute.Bold))

  def show: ZIO[Frame, IOException, Unit] =
    (animate *> complete).onInterrupt(clearBox.ignore)

  /**
   * Wipes the panel's drawing region with empty cells and flushes the
   * cleared frame. Runs as the `onInterrupt` finalizer of `show` so a
   * keypress mid-animation leaves no partial bar behind.
   */
  private val clearBox: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      canvas.fillRect(PanelBox, Cell.Empty)
    }

  private val animate: ZIO[Frame, IOException, Unit] =
    ZIO.foreachDiscard(0 to Steps) { percent =>
      Frame.run { canvas =>
        DemoUtils.drawHeader(canvas, "Progress Bar")
        drawBar(canvas, percent)
      }.zipLeft(ZIO.sleep(zio.Duration.fromMillis(StepDelayMs)))
    }

  private val complete: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Progress Bar")
      drawBar(canvas, 100)
      canvas.putText(barCol, barRow + 2, "Complete!",
        CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Bold)))
      canvas.putText(barCol, barRow + 4, "60-char bar with 8-level sub-character precision (480 steps)",
        DemoUtils.DimStyle)
    }

  /**
   * Pure per-frame seam: draw the bar for animation index `percent` (0–100).
   * Already index-driven and glyph-resolving internally — package-private so a
   * test can render a specific step directly, without stepping the clock.
   */
  private[panels] def drawBar(canvas: Canvas, percent: Int): Unit =
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
