package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.*
import demo.DemoUtils
import geometry.Rect
import layout.Constraint

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for [[Checkbox]] — a small settings-style form.
 *
 * Three checkboxes and a live mirror label. Each checkbox's `onToggle`
 * writes into a shared reference that the mirror reads on render, so
 * the visible summary updates on the natural redraw following the
 * `Perform` signal.
 */
object CheckboxDemoPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val boxStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val mirrorStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  /** Setting definitions — label and initial state. */
  private val settings: Seq[(String, Boolean)] = Seq(
    "Notifications" -> true,
    "Sound effects" -> false,
    "Auto-save"     -> true,
    "Dark theme"    -> false
  )

  /** Custom leaf: reads the shared state on each render. */
  final private class MirrorLabel(state: AtomicReference[Map[String, Boolean]]) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val map = state.get()
      val enabled = settings.collect { case (name, _) if map.getOrElse(name, false) => name }
      val summary =
        if enabled.isEmpty then "Currently on: (none)"
        else s"Currently on: ${enabled.mkString(", ")}"
      val truncated = if summary.length > area.width then summary.take(area.width) else summary
      canvas.putText(area.x, area.y, truncated, mirrorStyle)

  def make: UIO[AppPanel] =
    for
      state <- ZIO.succeed(new AtomicReference[Map[String, Boolean]](settings.toMap))
      boxes <- ZIO.foreach(settings) { case (name, initial) =>
                 Checkbox.make(
                   label = name,
                   checked = initial,
                   style = boxStyle,
                   onToggle = (nowChecked: Boolean) =>
                     ZIO.succeed {
                       state.updateAndGet(_.updated(name, nowChecked))
                       ()
                     }
                 )
               }
    yield
      val checkboxRows: Seq[(Constraint, Component)] =
        boxes.map(Constraint.Fixed(1) -> _)

      val tree = VBox(
        (Seq[(Constraint, Component)](
          Constraint.Fixed(3) -> Panel(
            border = BoxStyle.Double,
            style = DemoUtils.HeaderStyle,
            child = Text("Checkbox — bistable toggles", DemoUtils.HeaderStyle, Alignment.Center)
          ),
          Constraint.Fixed(1) -> Text(
            "Tab to focus a checkbox; Space toggles it. Marks use the styleguide's ☑ / ☐ defaults.",
            DemoUtils.DimStyle
          ),
          Constraint.Fixed(1) -> Spacer
        ) ++ checkboxRows ++ Seq[(Constraint, Component)](
          Constraint.Fixed(1) -> Spacer,
          Constraint.Fixed(1) -> new MirrorLabel(state),
          Constraint.Fixed(1) -> Spacer,
          Constraint.Fixed(1) -> Text(
            "The widget owns the toggled state; onToggle is the signal the host sees per press.",
            bodyStyle
          ),
          Constraint.Fill -> Spacer
        ))*
      )
      AppPanel.of(tree)
