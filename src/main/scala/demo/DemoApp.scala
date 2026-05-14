package io.github.wickedsik.wsconsole
package demo

import app.Application
import buffer.{Canvas, Frame}
import component.{Component, HBox, VBox}
import demo.panels.*
import demo.widgets.ToolbarButton
import event.KeyEvent.{CharKey, SpecialKey}
import event.*
import geometry.Rect
import layout.Constraint
import terminal.Terminal

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Layer 7 demo entry point — restructured around a persistent bottom
 * toolbar driving a swappable panel area.
 *
 * Architecture:
 *   - The application's component tree is a single `VBox`:
 *     - Top region (`Fill`): the active demo panel's component tree
 *     - Bottom region (`Fixed(3)`): a `Toolbar` of `ToolbarButton`s —
 *       Previous, Next, Quit
 *   - The active panel is held in an `AtomicReference[Component]` read
 *     by a tiny `PanelArea` component each render. `replace` is a single
 *     atomic swap; the next redraw walks the new tree.
 *   - Focus cycling (Tab / Shift+Tab) runs through every focusable in
 *     the rendered tree — toolbar buttons always, plus the focus-demo
 *     boxes when that panel is active.
 *   - Shortcuts: `n` → next, `p` → previous, `q` → quit (the latter
 *     handled by `Application`'s `quitOn`).
 *
 * `PanelHost` (Layer 7) is not used here because each panel adapts to
 * the VBox-assigned region rather than declaring its own bounds — the
 * stack abstraction is reserved for full-screen / modal compositions.
 */
object DemoApp:

  // ===== Active-panel holder =====

  /** A `Component` that delegates to whatever is currently in the ref. */
  private final class PanelArea(active: AtomicReference[Component]) extends Component:
    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      Seq((active.get(), area))

    def render(area: Rect, canvas: Canvas): Unit =
      active.get().render(area, canvas)

  // ===== Entry =====

  def run: ZIO[Terminal & Frame, IOException, Unit] =
    for
      app <- Application.make
      boxes = FocusDemoPanel.makeBoxes
      panels = Vector(
        "Welcome" -> WelcomePanel.tree,
        "Color Gallery" -> ColorGalleryPanel.tree,
        "Style Showcase" -> StyleShowcasePanel.tree,
        "Cursor Demo" -> CursorDemoPanel.tree,
        "Layout Demo" -> LayoutDemoPanel.tree,
        "Focus Demo" -> FocusDemoPanel.treeFor(boxes),
        "Farewell" -> FarewellPanel.tree
      )

      indexRef <- Ref.make(0)
      activeRef = new AtomicReference[Component](panels.head._2)

      prevBtn = new ToolbarButton("Previous", 'p')
      nextBtn = new ToolbarButton("Next", 'n')
      quitBtn = new ToolbarButton("Quit", 'q')

      root = VBox(
        Constraint.Fill -> PanelArea(activeRef),
        Constraint.Fixed(3) -> HBox(prevBtn, nextBtn, quitBtn)
      )

      // Initial focus: Next button (the most common forward path)
      _ <- app.focusManager.updateFocusables(Vector(prevBtn, nextBtn, quitBtn))
      _ <- app.focusManager.focus(nextBtn.id)
      _ <- ZIO.succeed(nextBtn.setFocused(true))

      onEvent = (event: Event, _: EventResult) =>
        handleEvent(event, app, panels, indexRef, activeRef,
          prevBtn, nextBtn, quitBtn, boxes)

      _ <- app.run(root, onEvent)
    yield ()

  // ===== Event handling =====

  /**
   * Top-level dispatch:
   *   - Toolbar shortcuts `n` / `p` / (q is in `quitOn`)
   *   - Tab / Shift+Tab → cycle focus + sync visual flags
   *   - Pending button activation (`consumePending` set by handleEvent)
   *   - Otherwise ignore — q / Ctrl+C are absorbed by `Application`
   */
  private def handleEvent(
                           event: Event,
                           app: Application,
                           panels: Vector[(String, Component)],
                           indexRef: Ref[Int],
                           activeRef: AtomicReference[Component],
                           prevBtn: ToolbarButton,
                           nextBtn: ToolbarButton,
                           quitBtn: ToolbarButton,
                           boxes: FocusDemoPanel.Boxes
                         ): UIO[Boolean] =
    event match
      // Direct shortcut — Previous
      case CharKey('p', mods) if mods.isEmpty =>
        moveTo(-1, panels, indexRef, activeRef, boxes, app)

      // Direct shortcut — Next
      case CharKey('n', mods) if mods.isEmpty =>
        moveTo(+1, panels, indexRef, activeRef, boxes, app)

      // Tab / Shift+Tab — cycle focus, sync visual flags, full redraw.
      //
      // Why full redraw rather than diff: a focus transition that spans
      // large components (the FocusableBoxes occupy half the screen each)
      // empirically desyncs the terminal display from the buffer — cells
      // the diff correctly skips as unchanged (e.g. the toolbar) drop off
      // the display anyway. The same hazard `Frame.clearScreen` documents
      // for panel swaps. Tab is rare; the extra ANSI bytes are cheap.
      case SpecialKey(SpecialKeyCode.Tab, mods) =>
        val cycle =
          if mods.contains(KeyModifier.Shift) then app.focusManager.focusPrevious()
          else app.focusManager.focusNext()
        for
          _ <- cycle
          focused <- app.focusManager.focused
          _ = prevBtn.setFocused(focused.contains(prevBtn.id))
          _ = nextBtn.setFocused(focused.contains(nextBtn.id))
          _ = quitBtn.setFocused(focused.contains(quitBtn.id))
          _ = boxes.left.setFocused(focused.contains(boxes.left.id))
          _ = boxes.right.setFocused(focused.contains(boxes.right.id))
          _ <- app.requestFullRedraw
        yield true

      // Button activation via Enter / Space (button's handleEvent set its flag)
      case _ =>
        for
          actedOnPrev <- ZIO.succeed(prevBtn.consumePending())
          actedOnNext <- ZIO.succeed(nextBtn.consumePending())
          actedOnQuit <- ZIO.succeed(quitBtn.consumePending())
          keep <- if actedOnPrev then moveTo(-1, panels, indexRef, activeRef, boxes, app)
          else if actedOnNext then moveTo(+1, panels, indexRef, activeRef, boxes, app)
          else if actedOnQuit then app.quit.as(false)
          else ZIO.succeed(true)
        yield keep

  /**
   * Advance the panel index by `delta`, clamped to `[0, panels.size - 1]`.
   * Updates the active reference and the focusables (so Tab now sees the
   * new panel's focusables alongside the toolbar buttons).
   */
  private def moveTo(
                      delta: Int,
                      panels: Vector[(String, Component)],
                      indexRef: Ref[Int],
                      activeRef: AtomicReference[Component],
                      boxes: FocusDemoPanel.Boxes,
                      app: Application
                    ): UIO[Boolean] =
    for
      current <- indexRef.get
      next = math.max(0, math.min(panels.size - 1, current + delta))
      _ <- ZIO.when(next != current) {
        for {
          _ <- indexRef.set(next).as(activeRef.set(panels(next)._2))
          _ <- ZIO.succeed {
            if !panels(next)._1.contains("Focus") then
              boxes.left.setFocused(false)
              boxes.right.setFocused(false)
          }
          r <- app.requestFullRedraw
        } yield r
      }
    yield true
