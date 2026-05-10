package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, Canvas, CellStyle, Foreground, Frame}
import component.{Alignment, Component, HBox, Panel, Spacer, Text, VBox}
import demo.DemoUtils
import event.{Event, EventResult, KeyEvent, KeyModifier, SpecialKeyCode}
import event.KeyEvent.{CharKey, SpecialKey}
import geometry.Rect
import render.RenderLoop
import terminal.Terminal

import zio.{Promise, UIO, ZIO}

import java.io.IOException

/**
 * Layer 6 demonstration: two focusable boxes side by side. `Tab` cycles
 * focus, the focused box renders with a bright border. `q` / `Ctrl+C`
 * exit the demo; `Enter` / `Escape` advance to the next panel.
 *
 * Exercises the full Layer 6 pipeline:
 *   - `RenderLoop` drives the per-frame render
 *   - `FocusManager` tracks the focused [[FocusableBox]]
 *   - The application-level `onEvent` callback handles `Tab` / exit keys
 *     and calls `requestRedraw` after each focus change
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
   * when [[setFocused]] flips; render reads the latest flag at frame time.
   * The flag is a `@volatile` field so updates from the event-handler
   * fiber are visible to the render fiber (both run on the ZIO scheduler;
   * the `volatile` write is conservative but cheap).
   */
  final class FocusableBox(val label: String, val description: String) extends Component:
    override val focusable: Boolean = true
    @volatile private var focusedFlag: Boolean = false

    def setFocused(b: Boolean): Unit = focusedFlag = b

    override def handleEvent(event: Event): EventResult =
      // The demo-level callback owns Tab + exit handling; the box itself
      // does not consume keys. Returning Ignored bubbles to the parent.
      EventResult.Ignored

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

  // ===== Tree =====

  // Component instances must be stable across frames so FocusManager.focused
  // tracks them correctly. Constructed once at panel-show time and reused.
  private def buildTree(left: FocusableBox, right: FocusableBox): Component =
    VBox(
      // Header
      Panel(
        title  = Some(" Layer 6 — Focus + Dispatch "),
        border = BoxStyle.Double,
        style  = titleStyle,
        child  = VBox(
          Spacer,
          Text("Press Tab to switch focus, Enter to continue, q to exit",
               instructionStyle, Alignment.Center),
          Spacer
        )
      ),
      // Two focusable boxes side by side
      HBox(left, right),
      // Footer
      Text("FocusManager → EventDispatcher → RenderLoop end-to-end",
           instructionStyle, Alignment.Center)
    )

  /**
   * Run the focus demo until the user presses an exit / advance key.
   * Returns the key that ended the panel so `DemoApp` can decide whether
   * to advance or quit the whole demo.
   */
  def show: ZIO[Terminal & Frame, IOException, Option[KeyEvent]] =
    val left  = FocusableBox("Left",  "I am the left box")
    val right = FocusableBox("Right", "I am the right box")
    val tree  = buildTree(left, right)

    for
      loop      <- RenderLoop.make()
      exitKey   <- Promise.make[Nothing, KeyEvent]

      // Initial focus on left.
      _ <- loop.focusManager.updateFocusables(Vector(left, right))
      _ <- loop.focusManager.focus(left.id)
      _ <- ZIO.succeed(left.setFocused(true))

      onEvent = (event: Event, _: EventResult) => handleEvent(event, loop, left, right, exitKey)

      // RenderLoop.start blocks until stop. We race it against exitKey so
      // we can return the triggering key once the loop tears down.
      _   <- loop.start(tree, onEvent)
      key <- exitKey.await
    yield Some(key)

  private def handleEvent(
    event:    Event,
    loop:     RenderLoop,
    left:     FocusableBox,
    right:    FocusableBox,
    exitKey:  Promise[Nothing, KeyEvent]
  ): UIO[Boolean] =
    event match
      case k @ CharKey('q', _) =>
        exitKey.succeed(k) *> loop.stop.as(false)

      case k @ CharKey('c', mods) if mods.contains(KeyModifier.Ctrl) =>
        exitKey.succeed(k) *> loop.stop.as(false)

      case k @ SpecialKey(SpecialKeyCode.Enter, _) =>
        exitKey.succeed(k) *> loop.stop.as(false)

      case k @ SpecialKey(SpecialKeyCode.Escape, _) =>
        exitKey.succeed(k) *> loop.stop.as(false)

      case SpecialKey(SpecialKeyCode.Tab, mods) =>
        val cycle =
          if mods.contains(KeyModifier.Shift) then loop.focusManager.focusPrevious()
          else loop.focusManager.focusNext()
        for
          _       <- cycle
          focused <- loop.focusManager.focused
          _ = left.setFocused(focused.contains(left.id))
          _ = right.setFocused(focused.contains(right.id))
          _ <- loop.requestRedraw
        yield true

      case _ =>
        ZIO.succeed(true)
