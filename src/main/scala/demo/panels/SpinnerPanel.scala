package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Application, Panel as AppPanel}
import buffer.{Attribute, Canvas, CellStyle, Foreground, Frame}
import component.{Component, RenderContext}
import demo.{DemoLayout, DemoUtils}
import geometry.Rect
import terminal.Terminal
import unicode.SequencedDrawing

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Animated braille-dot spinner (AN-c pattern: forked tick fiber +
 * `AtomicInteger` frame counter + `Application.requestRedraw`).
 *
 * Lifecycle:
 *   - `onMount`  forks a tick fiber that advances the frame counter
 *                every 80ms and calls `Application.requestRedraw`.
 *   - `onUnload` interrupts the tick fiber and clears the panel bounds.
 *
 * The `Component` renders the current frame — read synchronously from
 * the `AtomicInteger`. Layer 2's diff engine emits a single
 * `RenderOp.Cell` per frame, proving in-place cell updates against
 * surrounding static text.
 */
object SpinnerPanel:

  val bounds: Rect          = DemoLayout.contentBounds
  private val FrameInterval = Duration.fromMillis(80L)
  private val FrameCount    = SequencedDrawing.Spinner.length

  private[panels] val spinnerCol = 4
  private[panels] val spinnerRow = 7
  private val labelCol           = spinnerCol + 4

  private val spinnerStyle: CellStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  /** Construct an animated spinner panel. Requires `Application` for the redraw signal. */
  def make(app: Application): UIO[AppPanel] =
    for
      frame    <- ZIO.succeed(new AtomicInteger(0))
      fiberRef <- Ref.make[Option[Fiber.Runtime[?, ?]]](None)
    yield new AppPanel:
      def bounds: Rect      = SpinnerPanel.bounds
      def root:   Component = spinnerComponent(frame)

      override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
        val tick =
          ZIO.succeed(frame.updateAndGet(f => (f + 1) % FrameCount)) *>
            app.requestRedraw
        for
          fiber <- tick.repeat(Schedule.spaced(FrameInterval)).fork
          _     <- fiberRef.set(Some(fiber))
        yield ()

      override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
        for
          fiberOpt <- fiberRef.get
          _        <- fiberOpt.fold(ZIO.unit)(_.interrupt)
          _        <- AppPanel.clearBounds(bounds)
        yield ()

  private def spinnerComponent(frame: AtomicInteger): Component =
    new Component:
      def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        DemoUtils.drawHeader(canvas, "Spinner Animation")
        renderFrame(canvas, frame.get())

  /**
   * Pure per-frame seam: draw the spinner glyph for animation index
   * `frame` (raw counter — displayed glyph is
   * `frame % SequencedDrawing.Spinner.length`). Symmetric with
   * [[ProgressBarPanel.drawBar]]; ratified per
   * `visual-integration-testing.md` Q4.
   */
  private[panels] def renderFrame(canvas: Canvas, frame: Int): Unit =
    val spinChar = SequencedDrawing.Spinner(math.floorMod(frame, SequencedDrawing.Spinner.length))
    canvas.putChar(spinnerCol, spinnerRow, spinChar, spinnerStyle)
    canvas.putText(labelCol,   spinnerRow, "Processing data...", DemoUtils.DimStyle)
