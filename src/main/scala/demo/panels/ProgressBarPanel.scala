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
 * Animated progress bar (AN-c pattern: forked tick fiber +
 * `AtomicInteger` percent counter + `Application.requestRedraw`).
 *
 * The counter loops 0 → 100 → 0 continuously — visually, the bar fills
 * from empty to full and repeats. Each step rewrites the whole bar;
 * the diff engine emits only the cells that actually changed
 * (typically the trailing edge plus the percentage label).
 *
 * Lifecycle mirrors [[SpinnerPanel]] — `onMount` forks the tick fiber
 * (30ms interval), `onUnload` interrupts it and clears bounds.
 */
object ProgressBarPanel:

  val bounds: Rect         = DemoLayout.contentBounds
  private val StepInterval = Duration.fromMillis(30L)
  private val PercentCycle = 101  // 0..100 inclusive; wraps to 0 on the next tick

  private[panels] val barRow = 7
  private[panels] val barCol = 2
  private val barWidth       = 60

  private val filledStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))
  private val percentStyle =
    CellStyle(attributes = Set(Attribute.Bold))

  /** Construct an animated progress bar panel. Requires `Application` for the redraw signal. */
  def make(app: Application): UIO[AppPanel] =
    for
      percent  <- ZIO.succeed(new AtomicInteger(0))
      fiberRef <- Ref.make[Option[Fiber.Runtime[?, ?]]](None)
    yield new AppPanel:
      def bounds: Rect      = ProgressBarPanel.bounds
      def root:   Component = progressComponent(percent)

      override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
        val tick =
          ZIO.succeed(percent.updateAndGet(p => (p + 1) % PercentCycle)) *>
            app.requestRedraw
        for
          fiber <- tick.repeat(Schedule.spaced(StepInterval)).fork
          _     <- fiberRef.set(Some(fiber))
        yield ()

      override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
        for
          fiberOpt <- fiberRef.get
          _        <- fiberOpt.fold(ZIO.unit)(_.interrupt)
          _        <- AppPanel.clearBounds(bounds)
        yield ()

  private def progressComponent(percent: AtomicInteger): Component =
    new Component:
      def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        DemoUtils.drawHeader(canvas, "Progress Bar")
        drawBar(canvas, percent.get())

  /**
   * Pure per-frame seam: draw the bar for animation index `percent` (0–100).
   * Already index-driven and glyph-resolving internally — package-private so
   * a test can render a specific step directly, without stepping the clock.
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
