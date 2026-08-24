package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, Canvas, Cell, CellStyle, Foreground, Line, Frame}
import demo.DemoUtils
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates scroll regions: a fixed header + status bar with content
 * scrolling in between.
 *
 * Migrated to the Layer 2 buffer-coherent flow: panel imports `Frame` and
 * `Canvas` only, no direct `Terminal` or `AnsiBuilder`. Per-tick the panel
 * calls `scroller.appendLine(...)` for the new line and `canvas.putText(...)`
 * for the row-23 status bar update; the `Frame` performs the hardware
 * scroll via DECSTBM and mirrors `previous` so cell coherence is preserved
 * across frames.
 */
object ScrollRegionPanel:

  private val Lines = 40
  private val FrameDelayMs = 150L

  // 0-indexed buffer coordinates.
  private val regionTop = 4
  private val regionBottom = 21
  private val statusRow = 23
  private val rowWidth = 78

  /**
   * Header rows (above the scroll region) that the panel writes into.
   * Cleared on interrupt.
   */
  private val HeaderBox: Rect = Rect(0, 0, 80, regionTop)

  /**
   * Status-bar row (below the scroll region). Cleared on interrupt.
   */
  private val StatusBarBox: Rect = Rect(0, statusRow, 80, 1)

  private val statusBarStyle: CellStyle = CellStyle(
    fg = Foreground.Named(FgColor.BrightWhite),
    attributes = Set(Attribute.Reverse)
  )

  private val completeStatusStyle: CellStyle = CellStyle(
    fg = Foreground.Named(FgColor.BrightGreen),
    attributes = Set(Attribute.Reverse)
  )

  def show: ZIO[Frame, IOException, Unit] =
    (setup *> animate *> complete).onInterrupt(clearBox.ignore)

  private val setup: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Scroll Region Demo")
      canvas.scrollRegion(regionTop, regionBottom)
      drawStatusBar(canvas, "  Status: Starting...", statusBarStyle)
    }

  private val animate: ZIO[Frame, IOException, Unit] =
    ZIO.foreachDiscard(1 to Lines) { lineNum =>
      Frame.run { canvas =>
        DemoUtils.drawHeader(canvas, "Scroll Region Demo")
        val scroller = canvas.scrollRegion(regionTop, regionBottom)
        val style = CellStyle(fg = Foreground.Named(colorFor(lineNum)))
        scroller.appendLine(Line.text(contentFor(lineNum), style))
        drawStatusBar(canvas, f"  Status: Lines printed: $lineNum / $Lines", statusBarStyle)
      }.zipLeft(ZIO.sleep(zio.Duration.fromMillis(FrameDelayMs)))
    }

  private val complete: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Scroll Region Demo")
      val scroller = canvas.scrollRegion(regionTop, regionBottom)
      scroller.clear()
      drawStatusBar(canvas, "  Status: Scroll demo complete!", completeStatusStyle)
    }

  /**
   * Wipes the panel's drawing region *and* tears down the active scroll
   * region. Runs as the `onInterrupt` finalizer of `show`, so a keypress
   * mid-animation does not leak DECSTBM scroll-region state into the
   * next panel.
   *
   * `scroller.clear()` does the load-bearing work: it fills the region
   * cells with empties and calls `buffer.clearScrollRegion()`, which
   * causes the next render's diff to emit a `ResetScrollRegion` op.
   * The header and status-bar rows are wiped separately as they sit
   * outside the scroll region.
   */
  private val clearBox: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      val scroller = canvas.scrollRegion(regionTop, regionBottom)
      scroller.clear()
      canvas.fillRect(HeaderBox, Cell.Empty)
      canvas.fillRect(StatusBarBox, Cell.Empty)
    }

  private def colorFor(lineNum: Int): FgColor =
    lineNum % 6 match
      case 0 => FgColor.BrightRed
      case 1 => FgColor.BrightGreen
      case 2 => FgColor.BrightYellow
      case 3 => FgColor.BrightBlue
      case 4 => FgColor.BrightMagenta
      case _ => FgColor.BrightCyan

  private def contentFor(lineNum: Int): String =
    f"  Line $lineNum%3d: " + "░▒▓█" * (lineNum % 8 + 2)

  private def drawStatusBar(canvas: Canvas, text: String, style: CellStyle): Unit =
    val padded = f"$text%-78s".take(rowWidth)
    canvas.putText(0, statusRow, padded, style)
