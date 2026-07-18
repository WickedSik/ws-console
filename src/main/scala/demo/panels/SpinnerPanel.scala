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
 * Animated braille-dot spinner demonstration.
 *
 * Each frame writes the same cells; only the spinner glyph differs from the
 * previous frame. The Layer 2 diff engine emits a single RenderOp.Cell per
 * frame, proving "in-place cell updates" against the surrounding static text.
 */
object SpinnerPanel:

  private val Frames = 60
  private val FrameDelayMs = 80L

  private[panels] val spinnerCol = 4
  private[panels] val spinnerRow = 7
  private val labelCol   = spinnerCol + 4

  /**
   * Bounding box this panel may write into:
   *   - Header: rows 0–2 (`DemoUtils.drawHeader`)
   *   - Spinner glyph + label: row 7
   *   - Completion checkmark + dim footer: rows 7 and 9
   *
   * Cleared by [[clearBox]] on interrupt so the next panel inherits a
   * blank slate without depending on the diff engine to catch every
   * stale cell.
   */
  private val PanelBox: Rect = Rect(0, 0, 80, 10)

  private val spinnerStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val checkStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Bold))

  def show: ZIO[Frame, IOException, Unit] =
    (drawHeader *> animate *> complete).onInterrupt(clearBox.ignore)

  /**
   * Wipes the panel's drawing region and flushes the cleared frame. Runs
   * as the `onInterrupt` finalizer of `show` so a keypress mid-animation
   * leaves no partial spinner behind.
   */
  private val clearBox: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      canvas.fillRect(PanelBox, Cell.Empty)
    }

  /** Render the panel header. Lives on its own frame so the diff engine
   *  doesn't repeatedly compute it during animation. */
  private val drawHeader: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Spinner Animation")
    }

  /** Drive 60 frames at 80ms each. Per frame: rewrite the whole panel content,
   *  let the diff engine emit only the spinner-glyph change. */
  private val animate: ZIO[Frame, IOException, Unit] =
    ZIO.foreachDiscard(0 until Frames) { frame =>
      Frame.run { canvas =>
        DemoUtils.drawHeader(canvas, "Spinner Animation")
        renderFrame(canvas, frame)
      }.zipLeft(ZIO.sleep(zio.Duration.fromMillis(FrameDelayMs)))
    }

  /**
   * Pure per-frame seam: draw the spinner glyph for animation `frame` (the raw
   * frame counter — the displayed glyph is `frame % Spinner.length`) plus its
   * label. The header is the caller's responsibility. Extracted so a test can
   * render frame N directly, without stepping the clock — symmetric with
   * [[ProgressBarPanel.drawBar]].
   */
  private[panels] def renderFrame(canvas: Canvas, frame: Int): Unit =
    val spinChar = SequencedDrawing.Spinner(frame % SequencedDrawing.Spinner.length)
    drawSpinnerLine(canvas, spinChar, "Processing data...")

  private val complete: ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Spinner Animation")
      canvas.putChar(spinnerCol, spinnerRow, '✓', checkStyle)
      canvas.putText(labelCol,   spinnerRow, "Done!", DemoUtils.DimStyle)
      canvas.putText(spinnerCol, spinnerRow + 2, "60 frames at 80ms using braille dot characters", DemoUtils.DimStyle)
    }

  private def drawSpinnerLine(canvas: Canvas, spinChar: Char, label: String): Unit =
    canvas.putChar(spinnerCol, spinnerRow, spinChar, spinnerStyle)
    canvas.putText(labelCol,   spinnerRow, label,    DemoUtils.DimStyle)
