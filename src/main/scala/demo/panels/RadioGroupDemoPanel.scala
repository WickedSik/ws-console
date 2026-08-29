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
 * Showcase panel for [[RadioGroup]] — two independent radio groups
 * demonstrating the "one Tab stop per group" contract.
 *
 * Tab between the groups; arrow keys move the selection within a
 * group. Each group's `onSelect` writes into a shared reference the
 * mirror label reads on render, so the visible summary updates on the
 * natural redraw following each selection change.
 */
object RadioGroupDemoPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val groupStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val mirrorStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  private val labelHeadingStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val themes: Seq[String] = Seq("Light", "Dark", "System (auto)")
  private val editors: Seq[String] = Seq("Vim", "Emacs", "Standard", "Ed (for the brave)")

  /** Custom leaf: renders a live summary of two indices. */
  final private class MirrorLabel(
    themeRef: AtomicReference[Int],
    editorRef: AtomicReference[Int]
  ) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val summary =
        s"Theme: ${themes(themeRef.get())}  ·  Editor: ${editors(editorRef.get())}"
      val truncated = if summary.length > area.width then summary.take(area.width) else summary
      canvas.putText(area.x, area.y, truncated, mirrorStyle)

  def make: UIO[AppPanel] =
    for
      themeRef <- ZIO.succeed(new AtomicReference[Int](0))
      editorRef <- ZIO.succeed(new AtomicReference[Int](2))
      themeGroup <- RadioGroup.make(
        options = themes,
        selected = themeRef.get(),
        style = groupStyle,
        onSelect = (i: Int) => ZIO.succeed { themeRef.set(i); () }
      )
      editorGroup <- RadioGroup.make(
        options = editors,
        selected = editorRef.get(),
        style = groupStyle,
        onSelect = (i: Int) => ZIO.succeed { editorRef.set(i); () }
      )
    yield
      val leftColumn = VBox(
        Constraint.Fixed(1) -> Text("Theme", labelHeadingStyle),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(themes.size) -> themeGroup,
        Constraint.Fill -> Spacer
      )
      val rightColumn = VBox(
        Constraint.Fixed(1) -> Text("Editor mode", labelHeadingStyle),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(editors.size) -> editorGroup,
        Constraint.Fill -> Spacer
      )

      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("RadioGroup — mutually exclusive selection", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Tab switches groups (each is one focus stop). Arrows / Home / End move within a group.",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fill -> HBox(
          Constraint.Fixed(30) -> leftColumn,
          Constraint.Fill -> rightColumn
        ),
        Constraint.Fixed(1) -> new MirrorLabel(themeRef, editorRef),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(
          "onSelect fires per change; no wrap-around at the ends.",
          bodyStyle
        )
      )
      AppPanel.of(tree)
