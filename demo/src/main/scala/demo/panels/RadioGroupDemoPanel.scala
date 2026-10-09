package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Widths}
import component.*
import demo.DemoUtils
import geometry.Rect
import layout.Constraint

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.AtomicReference

/**
 * Showcase panel for [[component.RadioGroup]] — two vertical and two
 * horizontal groups demonstrating the "one Tab stop per group" contract
 * and the per-axis keys.
 *
 * Tab between the groups. A vertical group moves with Up / Down, a
 * horizontal one with Left / Right; the other pair bubbles. Each group's
 * `onSelect` writes into a reference the mirror label reads on render,
 * so the visible summary updates on the natural redraw following each
 * selection change. The language group carries CJK labels, laid out by
 * cells.
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
  private val ratings: Seq[String] = Seq("Safe", "Mature")
  private val languages: Seq[String] = Seq("English", "日本語", "中文", "Nederlands")

  /** Custom leaf: renders a live summary of every group's selection. */
  final private class MirrorLabel(
    themeRef: AtomicReference[Int],
    editorRef: AtomicReference[Int],
    ratingRef: AtomicReference[Int],
    languageRef: AtomicReference[Int]
  ) extends Component:
    override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.isEmpty then return
      val summary =
        s"Theme: ${themes(themeRef.get())}  ·  Editor: ${editors(editorRef.get())}  ·  " +
          s"Rating: ${ratings(ratingRef.get())}  ·  Language: ${languages(languageRef.get())}"
      canvas.putText(area.x, area.y, Widths.truncate(summary, area.width), mirrorStyle)

  /** A heading cell and a horizontal group on one row. */
  private def labelledRow(label: String, group: RadioGroup): Component =
    HBox(
      Constraint.Fixed(12) -> Text(label, labelHeadingStyle),
      Constraint.Fill      -> group
    )

  def make: UIO[AppPanel] =
    for
      themeRef  <- ZIO.succeed(new AtomicReference[Int](0))
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
      ratingRef   <- ZIO.succeed(new AtomicReference[Int](0))
      languageRef <- ZIO.succeed(new AtomicReference[Int](0))
      ratingGroup <- RadioGroup.make(
                       options = ratings,
                       selected = ratingRef.get(),
                       style = groupStyle,
                       layout = RadioGroup.Layout.Horizontal(),
                       onSelect = (i: Int) => ZIO.succeed { ratingRef.set(i); () }
                     )
      languageGroup <- RadioGroup.make(
                         options = languages,
                         selected = languageRef.get(),
                         style = groupStyle,
                         layout = RadioGroup.Layout.Horizontal(gap = 3),
                         onSelect = (i: Int) => ZIO.succeed { languageRef.set(i); () }
                       )
    yield
      val leftColumn = VBox(
        Constraint.Fixed(1)           -> Text("Theme", labelHeadingStyle),
        Constraint.Fixed(1)           -> Spacer,
        Constraint.Fixed(themes.size) -> themeGroup,
        Constraint.Fill               -> Spacer
      )
      val rightColumn = VBox(
        Constraint.Fixed(1)            -> Text("Editor mode", labelHeadingStyle),
        Constraint.Fixed(1)            -> Spacer,
        Constraint.Fixed(editors.size) -> editorGroup,
        Constraint.Fill                -> Spacer
      )

      val tree = VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("RadioGroup — mutually exclusive selection", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Tab: next group. ↑/↓ move a vertical group, ←/→ a horizontal one.",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fill -> HBox(
          Constraint.Fixed(30) -> leftColumn,
          Constraint.Fill      -> rightColumn
        ),
        Constraint.Fixed(1) -> labelledRow("Rating", ratingGroup),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> labelledRow("Language", languageGroup),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> new MirrorLabel(themeRef, editorRef, ratingRef, languageRef),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(
          "onSelect fires per change; no wrap-around at the ends.",
          bodyStyle
        )
      )
      AppPanel.of(tree)
