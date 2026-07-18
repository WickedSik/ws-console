package io.github.wickedsik.wsconsole
package demo

import app.Application
import buffer.{Canvas, Frame}
import component.{Component, HBox, RenderContext, VBox}
import demo.panels.*
import demo.widgets.ToolbarButton
import event.KeyEvent.{CharKey, SpecialKey}
import event.*
import geometry.Rect
import layout.Constraint
import render.{FocusOrder, FocusableEntry}
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

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      active.get().render(area, canvas, ctx)

  // ===== Entry =====

  def run: ZIO[Terminal & Frame, IOException, Unit] =
    for
      app   <- Application.make
      boxes <- FocusDemoPanel.makeBoxes
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

      prevBtn <- ToolbarButton.make("Previous", 'p')
      nextBtn <- ToolbarButton.make("Next", 'n')
      quitBtn <- ToolbarButton.make("Quit", 'q')

      root = VBox(
        Constraint.Fill -> PanelArea(activeRef),
        Constraint.Fixed(3) -> HBox(prevBtn, nextBtn, quitBtn)
      )

      // Initial focus: Next button (the most common forward path).
      // Seed the FocusManager with a synthetic order so `focus(nextBtn.id)`
      // succeeds before the first render's tree walk installs the real
      // order. The first frame's `setOrder(layout0.focusOrder)` overwrites
      // this with real rects from the layout walk; with the default
      // `FocusPolicy.MoveToFirstOnRemoval`, focus survives the swap
      // because `nextBtn.id` is still in the new cycle.
      seedOrder = FocusOrder(Vector(
                    FocusableEntry(prevBtn.id, Rect(0, 0, 0, 0)),
                    FocusableEntry(nextBtn.id, Rect(0, 0, 0, 0)),
                    FocusableEntry(quitBtn.id, Rect(0, 0, 0, 0))
                  ))
      _ <- app.focusManager.setOrder(seedOrder)
      _ <- app.focusManager.focus(nextBtn.id)

      onEvent = (event: Event, _: EventResult) =>
        handleEvent(event, app, panels, indexRef, activeRef,
          prevBtn, nextBtn, quitBtn)

      _ <- app.run(root, onEvent)
    yield ()

  // ===== Event handling =====

  /**
   * Top-level dispatch:
   *   - Toolbar shortcuts `n` / `p` / (q is in `quitOn`)
   *   - Tab / Shift+Tab → cycle focus; visual state derived from ctx
   *   - Pending button activation (`consumePending` set by handleEvent)
   *   - Otherwise ignore — q / Ctrl+C are absorbed by `Application`
   *
   * No `boxes` parameter — the FocusDemo panel's focusables read their
   * focused state from `ctx.focus.isFocused` at render time, so the
   * application no longer needs handles to push state into them.
   */
  private def handleEvent(
                           event: Event,
                           app: Application,
                           panels: Vector[(String, Component)],
                           indexRef: Ref[Int],
                           activeRef: AtomicReference[Component],
                           prevBtn: ToolbarButton,
                           nextBtn: ToolbarButton,
                           quitBtn: ToolbarButton
                         ): UIO[Boolean] =
    event match
      // Direct shortcut — Previous
      case CharKey('p', mods) if mods.isEmpty =>
        moveTo(-1, panels, indexRef, activeRef, app)

      // Direct shortcut — Next
      case CharKey('n', mods) if mods.isEmpty =>
        moveTo(+1, panels, indexRef, activeRef, app)

      // Tab / Shift+Tab — shift focus and let the next frame render the
      // new visual state. All focusables read `ctx.focus.isFocused` at
      // render time, so no per-component state push is needed. The
      // redraw is scheduled by `FocusManager.focusNext` /
      // `focusPrevious` themselves (they fire the `onChange` callback
      // the render loop supplies, which enqueues on the redraw queue).
      case SpecialKey(SpecialKeyCode.Tab, mods) =>
        val cycle =
          if mods.contains(KeyModifier.Shift) then app.focusManager.focusPrevious()
          else app.focusManager.focusNext()
        cycle.as(true)

      // Button activation via Enter / Space (button's handleEvent set its flag)
      case _ =>
        for
          actedOnPrev <- prevBtn.consumePending
          actedOnNext <- nextBtn.consumePending
          actedOnQuit <- quitBtn.consumePending
          keep <- if actedOnPrev then moveTo(-1, panels, indexRef, activeRef, app)
                  else if actedOnNext then moveTo(+1, panels, indexRef, activeRef, app)
                  else if actedOnQuit then app.quit.as(false)
                  else ZIO.succeed(true)
        yield keep

  /**
   * Advance the panel index by `delta`, clamped to `[0, panels.size - 1]`.
   * Updates the active reference and the focusables (so Tab now sees the
   * new panel's focusables alongside the toolbar buttons).
   *
   * Panel swap uses `requestRefresh`: the diff baseline is wiped so the
   * full new frame is re-emitted to the terminal in one writeBuilder.
   * Necessary because the terminal display can drift from the buffer
   * model across layout-context transitions — cells the diff would
   * otherwise skip (e.g. the toolbar, identical between the old and
   * new frames) may have been lost from the terminal's display even
   * though our buffer still believes they are on screen.
   *
   * `requestRefresh` produces no flicker — no `\e[2J` is emitted. The
   * full frame's worth of cells reaches the terminal as one coherent
   * batch.
   */
  private def moveTo(
                      delta: Int,
                      panels: Vector[(String, Component)],
                      indexRef: Ref[Int],
                      activeRef: AtomicReference[Component],
                      app: Application
                    ): UIO[Boolean] =
    for
      current <- indexRef.get
      next = math.max(0, math.min(panels.size - 1, current + delta))
      _ <- ZIO.when(next != current) {
        for
          _ <- indexRef.set(next)
          _ <- ZIO.succeed(activeRef.set(panels(next)._2))
          // FocusableBox reads ctx.focus directly — no need to clear box
          // focus on panel swap. When the FocusDemo panel unmounts, the
          // next render's setOrder drops the boxes from the focus cycle
          // and the default FocusPolicy.MoveToFirstOnRemoval rolls focus
          // to the first surviving focusable (a toolbar button).
          _ <- app.requestRefresh
        yield ()
      }
    yield true
