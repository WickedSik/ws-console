package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, Canvas, CellStyle, Foreground, Renderer}
import demo.DemoUtils
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

  private val spinnerCol = 4
  private val spinnerRow = 7
  private val labelCol   = spinnerCol + 4

  private val spinnerStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val checkStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Bold))

  def show: ZIO[Renderer, IOException, Unit] =
    for
      _ <- drawHeader
      _ <- animate
      _ <- complete
    yield ()

  /** Render the panel header. Lives on its own frame so the diff engine
   *  doesn't repeatedly compute it during animation. */
  private val drawHeader: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Spinner Animation")
    }

  /** Drive 60 frames at 80ms each. Per frame: rewrite the whole panel content,
   *  let the diff engine emit only the spinner-glyph change. */
  private val animate: ZIO[Renderer, IOException, Unit] =
    ZIO.foreachDiscard(0 until Frames) { frame =>
      val spinChar = SequencedDrawing.Spinner(frame % SequencedDrawing.Spinner.length)
      Renderer.frame { canvas =>
        DemoUtils.drawHeader(canvas, "Spinner Animation")
        drawSpinnerLine(canvas, spinChar, "Processing data...")
      }.zipLeft(ZIO.sleep(zio.Duration.fromMillis(FrameDelayMs)))
    }

  private val complete: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      DemoUtils.drawHeader(canvas, "Spinner Animation")
      canvas.putChar(spinnerCol, spinnerRow, '✓', checkStyle)
      canvas.putText(labelCol,   spinnerRow, "Done!", DemoUtils.DimStyle)
      canvas.putText(spinnerCol, spinnerRow + 2, "60 frames at 80ms using braille dot characters", DemoUtils.DimStyle)
    }

  private def drawSpinnerLine(canvas: Canvas, spinChar: Char, label: String): Unit =
    canvas.putChar(spinnerCol, spinnerRow, spinChar, spinnerStyle)
    canvas.putText(labelCol,   spinnerRow, label,    DemoUtils.DimStyle)
