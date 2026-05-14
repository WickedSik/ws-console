package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground}
import component.{Alignment, Component, HBox, Panel, Spacer, Text, VBox}
import geometry.Rect

/**
 * Layer 7 demonstration: two focusable boxes side by side. The
 * Application's `RenderLoop` produces every frame; Tab cycling is
 * routed through the Application's `FocusManager` at the demo's
 * `onEvent` layer.
 *
 * Migration changes vs. the Layer 6 shape:
 *   - No bespoke `RenderLoop` — `Application.run` owns the loop.
 *   - No internal `Promise[KeyEvent]` — exit keys handled by
 *     `Application` (q / Ctrl+C) and the demo's advance machinery
 *     (Enter / Space).
 *   - `show` returns nothing; the panel is a passive `AppPanel`
 *     whose `root` carries the component tree.
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
   * A bordered box that participates in the focus cycle. Style updates
   * when [[setFocused]] flips; render reads the latest flag at frame
   * time. The flag is `@volatile` so updates from the event-handler
   * fiber are visible to the render fiber.
   */
  final class FocusableBox(val label: String, val description: String) extends Component:
    override val focusable: Boolean = true
    @volatile private var focusedFlag: Boolean = false

    def setFocused(b: Boolean): Unit = focusedFlag = b
    def isFocused:  Boolean          = focusedFlag

    def render(area: Rect, canvas: Canvas): Unit =
      if area.width < 4 || area.height < 3 then return
      val style = if focusedFlag then focusedStyle else unfocusedStyle
      canvas.drawBox(area, BoxStyle.Single, Some(s" $label "), style)
      val inner = area.inner(1)
      if inner.height >= 1 then
        canvas.putText(
          inner.x + math.max(0, (inner.width - description.length) / 2),
          inner.y + math.max(0, (inner.height - 1) / 2),
          if description.length > inner.width then description.take(inner.width) else description,
          style
        )

  /** Mutable pair returned to the demo so it can sync the visual flag with `FocusManager`. */
  final case class Boxes(left: FocusableBox, right: FocusableBox)

  /** Allocate a fresh box pair. Each demo run gets its own instances. */
  def makeBoxes: Boxes =
    Boxes(
      FocusableBox("Left",  "I am the left box"),
      FocusableBox("Right", "I am the right box")
    )

  // ===== Tree =====

  /** Build the focus-demo component tree for the given boxes. */
  def treeFor(boxes: Boxes): Component = buildTree(boxes.left, boxes.right)

  private def buildTree(left: FocusableBox, right: FocusableBox): Component =
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
      HBox(left, right),
      Text("FocusManager → EventDispatcher → RenderLoop end-to-end",
           instructionStyle, Alignment.Center)
    )

  /** Build a Layer 7 panel bound to the supplied boxes. */
  def panelFor(boxes: Boxes, bounds: Rect = Rect(0, 0, 80, 24)): AppPanel =
    AppPanel.of(buildTree(boxes.left, boxes.right), bounds)
