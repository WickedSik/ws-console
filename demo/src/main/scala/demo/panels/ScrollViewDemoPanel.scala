package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Widths}
import component.*
import demo.DemoUtils
import geometry.{Insets, Rect}
import layout.Constraint

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for [[component.ScrollView]] and [[layout.Constraint.Fit]].
 *
 * A settings-style form of about sixty rows — far taller than the demo's
 * content region — inside a ScrollView. Every row is sized by `Fit`, so
 * each control takes its natural height and the ScrollView measures the
 * whole column through `preferredHeight`.
 *
 * Tab walks every control and the view follows focus (the reveal pass);
 * Ctrl+PgUp / Ctrl+PgDn page, Ctrl+Up / Ctrl+Down scroll a row, and
 * Ctrl+Home / Ctrl+End jump to the ends. Bare keys stay with the focused
 * control. A status line outside the view reports the offset each frame.
 */
object ScrollViewDemoPanel:

  private val sectionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  private val fieldStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val statusStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  /** Reads the view's offset and the last action on every render. */
  final private class StatusLine(view: ScrollView, lastAction: AtomicReference[String]) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val line = s"Scrolled ${view.offset} rows · ${lastAction.get()}"
      canvas.putText(area.x, area.y, Widths.truncate(line, area.width), statusStyle)

  private def fit(c: Component): (Constraint, Component) = Constraint.Fit -> c
  private def gap: (Constraint, Component) = Constraint.Fixed(1)          -> Spacer
  private def section(title: String): (Constraint, Component) = fit(Text(title, sectionStyle))

  private def field(placeholder: String): UIO[TextInput] =
    TextInput.make(placeholder = placeholder, style = fieldStyle)

  def make: UIO[AppPanel] =
    for
      lastAction <- ZIO.succeed(new AtomicReference[String]("nothing submitted yet"))
      account    <- ZIO.foreach(Seq("Full name", "Email address", "Password"))(field)
      address    <- ZIO.foreach(Seq("Street and number", "City", "Postal code", "Country"))(field)
      prefs <- ZIO.foreach(
                 Seq(
                   "Email me about new releases",
                   "Email me about security notices",
                   "Show tips on startup",
                   "Remember open panels",
                   "Use 24-hour clock",
                   "Enable telemetry",
                   "Compact layout"
                 )
               )(label => Checkbox.make(label, style = fieldStyle))
      frequency <- RadioGroup.make(Seq("Daily digest", "Weekly digest", "Never"), selected = 1, style = fieldStyle)
      save <- Button.make(
                "Save",
                ZIO.succeed(lastAction.set(s"saved '${account.head.value}'")),
                style = fieldStyle
              )
      reset <- Button.make(
                 "Reset",
                 ZIO.succeed {
                   (account ++ address).foreach(_.reset())
                   lastAction.set("form reset")
                 },
                 style = fieldStyle
               )
      form = VBox(
               (Seq(section("Account")) ++ account.map(fit) ++
                 Seq(gap, section("Preferences")) ++ prefs.map(fit) ++
                 Seq(
                   gap,
                   // A bordered block: scroll it past either edge to see the border cut, not shifted.
                   fit(
                     Panel(
                       title = Some("Shipping address"),
                       padding = Insets.symmetric(horizontal = 1, vertical = 0),
                       child = VBox(address.map(fit)*)
                     )
                   ),
                   gap,
                   section("Digest frequency"),
                   fit(frequency),
                   gap,
                   section("Notes"),
                   fit(Text(
                     "Rows are measured with preferredHeight, so every control keeps its own height.",
                     bodyStyle
                   )),
                   fit(Text("Offscreen controls stay in the Tab cycle; focusing one scrolls it into view.", bodyStyle)),
                   fit(Text(
                     "Paging away from the focused field sticks: the view only follows focus changes.",
                     bodyStyle
                   )),
                   fit(Text("Unicode clips on cell boundaries: 中文字符 · Кириллица · 日本語のテキスト", bodyStyle)),
                   gap,
                   fit(HBox(Constraint.Fixed(12) -> save, Constraint.Fixed(12) -> reset, Constraint.Fill -> Spacer)),
                   gap,
                   fit(Text("End of form.", DemoUtils.DimStyle))
                 ))*
             )
      view <- ScrollView.make(form, style = fieldStyle)
    yield
      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Scroll View — a viewport taller than the screen", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Tab: view follows focus · Ctrl+PgUp/PgDn page · Ctrl+Up/Down row · Ctrl+Home/End",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> new StatusLine(view, lastAction),
        Constraint.Fill     -> view
      )
      AppPanel.of(tree)
