package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Application, Panel as AppPanel}
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Frame}
import component.*
import demo.DemoUtils
import layout.Constraint
import terminal.Terminal

import zio.*

import java.io.IOException

/**
 * Showcase panel for [[Spinner]] — three named cycles animating in
 * lockstep off the wall-clock timestamp.
 *
 * The Spinner widget picks its frame from `ctx.timestamp`, so all three
 * cycles on this panel advance from the same clock without any per-panel
 * frame counter. The forked ticker calls `Application.requestRedraw` at
 * a modest cadence — that keeps the render loop turning; the Spinner
 * does the rest. Framework-level "component declares animation cadence"
 * is a follow-up outside the styleguide campaign.
 */
object SpinnerPanel:

  private val RedrawTick = Duration.fromMillis(80L)

  private val braille =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))
  private val line =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen), attributes = Set(Attribute.Bold))
  private val circle =
    CellStyle(fg = Foreground.Named(FgColor.BrightMagenta), attributes = Set(Attribute.Bold))

  private val labelStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  /** Construct the panel. Requires `Application` for the redraw signal. */
  def make(app: Application): UIO[AppPanel] =
    for
      fiberRef <- Ref.make[Option[Fiber.Runtime[?, ?]]](None)
    yield new AppPanel:
      def root: Component = tree

      override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
        for
          fiber <- app.requestRedraw.repeat(Schedule.spaced(RedrawTick)).fork
          _     <- fiberRef.set(Some(fiber))
        yield ()

      override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
        fiberRef.get.flatMap {
          case Some(fiber) => fiber.interrupt.unit
          case None        => ZIO.unit
        }

  private def cell(name: String, glyphStyle: CellStyle, cycle: SpinnerStyle): Component =
    Panel(
      border = BoxStyle.Single,
      style = CellStyle(fg = Foreground.Named(FgColor.BrightBlack)),
      padding = geometry.Insets.symmetric(horizontal = 2, vertical = 1),
      child = VBox(
        Constraint.Fixed(1) -> Text(name, labelStyle, Alignment.Center),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> HBox(
          Constraint.Fill     -> Spacer,
          Constraint.Fixed(1) -> Spinner(cycle, glyphStyle),
          Constraint.Fill     -> Spacer
        ),
        Constraint.Fill -> Spacer
      )
    )

  private val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style = DemoUtils.HeaderStyle,
      child = Text("Spinner — three cycles off one clock", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Text(
      "Frame index derives from ctx.timestamp; the tick fiber only drives redraws.",
      DemoUtils.DimStyle
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill -> HBox(
      cell("Braille (10 frames, 80ms)", braille, SpinnerStyle.Braille),
      cell("Line (4 frames, 200ms)", line, SpinnerStyle.Line),
      cell("Circle (6 frames, 160ms)", circle, SpinnerStyle.Circle)
    )
  )
