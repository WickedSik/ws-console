package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Application, Panel as AppPanel}
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Frame}
import component.*
import demo.DemoUtils
import geometry.Rect
import layout.Constraint
import terminal.Terminal

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Showcase panel for [[ProgressBar]] — one animated bar plus a static
 * side-by-side of the three [[ProgressBarStyle]] variants at the same
 * host-supplied progress value.
 *
 * The animated bar's progress ref advances 0 → 1 → 0 on a forked ticker
 * that also fires `Application.requestRedraw`; the static cells below
 * mirror the same value so all four visualise the same underlying
 * quantity at different fidelities.
 */
object ProgressBarPanel:

  private val StepInterval = Duration.fromMillis(30L)
  private val PercentCycle = 101

  private val fillStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))
  private val percentStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))
  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  /** Construct the panel. Requires `Application` for the redraw signal. */
  def make(app: Application): UIO[AppPanel] =
    for
      percent  <- ZIO.succeed(new AtomicInteger(0))
      fiberRef <- Ref.make[Option[Fiber.Runtime[?, ?]]](None)
    yield new AppPanel:
      def root: Component = buildTree(percent)

      override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
        val tick =
          ZIO.succeed(percent.updateAndGet(p => (p + 1) % PercentCycle)) *>
            app.requestRedraw
        for
          fiber <- tick.repeat(Schedule.spaced(StepInterval)).fork
          _     <- fiberRef.set(Some(fiber))
        yield ()

      override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
        fiberRef.get.flatMap {
          case Some(fiber) => fiber.interrupt.unit
          case None        => ZIO.unit
        }

  /** Custom leaf: reads a host-owned percent counter and wraps the library ProgressBar. */
  final private class AnimatedBar(
    percent: AtomicInteger,
    barStyle: ProgressBarStyle
  ) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      ProgressBar(percent.get() / 100.0, barStyle, fillStyle).render(area, canvas, ctx)

  /** Custom leaf: live percent label. */
  final private class PercentLabel(percent: AtomicInteger) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val text = f"${percent.get()}%3d%%"
      canvas.putText(area.x, area.y, text, percentStyle)

  private def buildTree(percent: AtomicInteger): Component =
    VBox(
      Constraint.Fixed(3) -> Panel(
        border = BoxStyle.Double,
        style = DemoUtils.HeaderStyle,
        child = Text("Progress Bar — one value, three styles", DemoUtils.HeaderStyle, Alignment.Center)
      ),
      Constraint.Fixed(1) -> Text(
        "Host owns the progress ref; each cell renders it at a different fidelity.",
        DemoUtils.DimStyle
      ),
      Constraint.Fixed(1) -> Spacer,
      Constraint.Fixed(1) -> row("Fill (sub-cell 8ths)", new AnimatedBar(percent, ProgressBarStyle.Fill), percent),
      Constraint.Fixed(1) -> Spacer,
      Constraint.Fixed(1) -> row("Shade (4-step gradient)", new AnimatedBar(percent, ProgressBarStyle.Shade), percent),
      Constraint.Fixed(1) -> Spacer,
      Constraint.Fixed(1) -> row(
        "Segmented (discrete pips)",
        new AnimatedBar(percent, ProgressBarStyle.Segmented),
        percent
      ),
      Constraint.Fill -> Spacer
    )

  private def row(label: String, bar: Component, percent: AtomicInteger): Component =
    HBox(
      Constraint.Fixed(28) -> Text(label, labelStyle),
      Constraint.Fill      -> bar,
      Constraint.Fixed(6)  -> new PercentLabel(percent)
    )
