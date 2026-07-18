package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.{Alignment, Component, HBox, Panel, RenderContext, Spacer, Text, VBox}
import geometry.Rect

import zio.{UIO, ZIO}

/**
 * Layer 7 demonstration: a row of focusable boxes. The Application's
 * `RenderLoop` produces every frame; Tab cycling is routed through
 * the Application's `FocusManager` at the demo's `onEvent` layer.
 *
 * The boxes read their focused state from the per-frame
 * [[RenderContext]] — they hold no local cache and the application
 * does not need to push focus state into them. Adding boxes to
 * [[Boxes.items]] requires zero changes to `DemoApp.handleEvent` —
 * the focus cycle, the visual state, and the Tab order all derive
 * from the rendered tree on each frame.
 */
object FocusDemoPanel:

  // ===== Visual styles =====

  private val unfocusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  private val focusedStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val titleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val instructionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  // ===== Focusable box component =====

  /**
   * A bordered box that participates in the focus cycle. Renders its
   * focused style when `ctx.focus.isFocused(this.id)` is true; no local
   * cache, no push pattern. The framework's render-loop snapshot is the
   * single source of truth.
   */
  final class FocusableBox(val label: String, val description: String) extends Component:
    override val focusable: Boolean = true

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      if area.width < 4 || area.height < 3 then return
      val style = if ctx.focus.isFocused(this.id) then focusedStyle else unfocusedStyle
      canvas.drawBox(area, BoxStyle.Single, Some(s" $label "), style)
      val inner = area.inner(1)
      if inner.height >= 1 then
        canvas.putText(
          inner.x + math.max(0, (inner.width - description.length) / 2),
          inner.y + math.max(0, (inner.height - 1) / 2),
          if description.length > inner.width then description.take(inner.width) else description,
          style
        )

  object FocusableBox:
    /** Construct a fresh box. UIO-shaped for symmetry with other widget factories. */
    def make(label: String, description: String): UIO[FocusableBox] =
      ZIO.succeed(new FocusableBox(label, description))

  /**
   * The set of focusable boxes the FocusDemo panel renders. Extensible
   * by construction — append to `items` and the new box is in the Tab
   * cycle automatically, with no DemoApp.handleEvent changes required.
   */
  final case class Boxes(items: Vector[FocusableBox])

  /**
   * Allocate a fresh box trio. Three boxes (not two) is a deliberate
   * AC-4 validation per the RenderContext ADT: it confirms that adding
   * a focusable widget requires zero changes to the application's
   * event handler. Each demo run gets its own instances.
   */
  def makeBoxes: UIO[Boxes] =
    for
      left   <- FocusableBox.make("Left",   "I am the left box")
      middle <- FocusableBox.make("Middle", "I am the middle box")
      right  <- FocusableBox.make("Right",  "I am the right box")
    yield Boxes(Vector(left, middle, right))

  // ===== Tree =====

  /** Build the focus-demo component tree for the given boxes. */
  def treeFor(boxes: Boxes): Component = buildTree(boxes)

  private def buildTree(boxes: Boxes): Component =
    VBox(
      Panel(
        title  = Some(" Layer 7 — Application + FocusManager "),
        border = BoxStyle.Double,
        style  = titleStyle,
        child  = VBox(
          Spacer,
          Text("Press Tab to switch focus, Enter / Space to continue, q to exit",
               instructionStyle, Alignment.Center),
          Spacer
        )
      ),
      HBox(boxes.items*),
      Text("FocusManager → EventDispatcher → RenderLoop end-to-end",
           instructionStyle, Alignment.Center)
    )

  /** Build a Layer 7 panel bound to the supplied boxes. */
  def panelFor(boxes: Boxes, bounds: Rect = Rect(0, 0, 80, 24)): AppPanel =
    AppPanel.of(buildTree(boxes), bounds)
