package io.github.wickedsik.wsconsole
package demo

import app.{Application, Panel as AppPanel, PanelHost}
import buffer.Frame
import component.{HBox, VBox}
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

/**
 * Layer 7 demo entry point — persistent bottom toolbar driving a
 * `PanelHost`-managed swappable content area.
 *
 * Architecture:
 *   - The application's component tree is a single `VBox`:
 *     - Top region (`Fill`): `host.root` — the composite root that walks
 *       the `PanelHost` panel stack per render
 *     - Bottom region (`Fixed(3)`): a `Toolbar` of `ToolbarButton`s —
 *       Previous, Next, Quit
 *   - Panel navigation is `host.replace(panels(next)._2)` in `moveTo`,
 *     which fires the redraw signal bound at `PanelHost.make` — we
 *     bound it to `app.requestRefresh` per Q3 ratification so the diff
 *     baseline is wiped on transition. No flicker; the new frame's
 *     cells reach the terminal in one writeBuilder.
 *   - Focus cycling (Tab / Shift+Tab) runs through every focusable in
 *     the rendered tree — toolbar buttons always, plus panel-local
 *     focusables when the active panel exposes them.
 *   - Shortcuts: `n` → next, `p` → previous, `q` → quit (the latter
 *     handled by `Application`'s `quitOn`).
 */
object DemoApp:

  def run: ZIO[Terminal & Frame, IOException, Unit] =
    for
      app       <- Application.make
      host      <- PanelHost.make(app.requestRefresh)
      boxes     <- FocusDemoPanel.makeBoxes
      spinner   <- SpinnerPanel.make(app)
      progress  <- ProgressBarPanel.make(app)
      inspector <- EventInspectorPanel.make(app)
      panels = Vector(
        "Welcome"         -> WelcomePanel.panel,
        "Color Gallery"   -> ColorGalleryPanel.panel,
        "Style Showcase"  -> StyleShowcasePanel.panel,
        "Cursor Demo"     -> CursorDemoPanel.panel,
        "Layout Demo"     -> LayoutDemoPanel.panel,
        "Focus Demo"      -> FocusDemoPanel.panelFor(boxes),
        "Spinner"         -> spinner,
        "Progress"        -> progress,
        "Event Inspector" -> inspector,
        "Farewell"        -> FarewellPanel.panel
      )

      indexRef <- Ref.make(0)

      prevBtn <- ToolbarButton.make("Previous", 'p')
      nextBtn <- ToolbarButton.make("Next", 'n')
      quitBtn <- ToolbarButton.make("Quit", 'q')

      root = VBox(
        Constraint.Fill     -> host.root,
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

      // Mount the first panel before entering the render loop so the
      // first frame paints content, not an empty stack. host.push runs
      // the bound refresh signal internally, enqueuing on the loop's
      // redraw queue — the initial render walks the now-non-empty stack.
      _ <- host.push(panels.head._2)

      onEvent = (event: Event, _: EventResult) =>
        handleEvent(event, app, host, panels, indexRef, prevBtn, nextBtn, quitBtn)

      // `host.rawEventTap` bridges the active panel's Panel.onRawEvent
      // (if any) to the framework's pre-quitOn tap slot. Only the
      // EventInspectorPanel currently opts in; other panels see no
      // change in behaviour.
      _ <- app.run(root, onEvent, onRawEvent = host.rawEventTap)
    yield ()

  // ===== Event handling =====

  /**
   * Top-level dispatch:
   *   - Toolbar shortcuts `n` / `p` (`q` is in `quitOn`)
   *   - Tab / Shift+Tab → cycle focus; visual state derived from ctx
   *   - Pending button activation (`consumePending` set by handleEvent)
   *   - Otherwise ignore — `q` / `Ctrl+C` are absorbed by `Application`
   */
  private def handleEvent(
    event:    Event,
    app:      Application,
    host:     PanelHost,
    panels:   Vector[(String, AppPanel)],
    indexRef: Ref[Int],
    prevBtn:  ToolbarButton,
    nextBtn:  ToolbarButton,
    quitBtn:  ToolbarButton
  ): ZIO[Terminal & Frame, IOException, Boolean] =
    event match
      // Direct shortcut — Previous
      case CharKey('p', mods) if mods.isEmpty =>
        moveTo(-1, panels, indexRef, host)

      // Direct shortcut — Next
      case CharKey('n', mods) if mods.isEmpty =>
        moveTo(+1, panels, indexRef, host)

      // Tab / Shift+Tab — shift focus and let the next frame render the
      // new visual state. All focusables read `ctx.focus.isFocused` at
      // render time, so no per-component state push is needed. The
      // redraw is scheduled by FocusManager itself via the onChange
      // callback the render loop supplies.
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
          keep <- if actedOnPrev then moveTo(-1, panels, indexRef, host)
                  else if actedOnNext then moveTo(+1, panels, indexRef, host)
                  else if actedOnQuit then app.quit.as(false)
                  else ZIO.succeed(true)
        yield keep

  /**
   * Advance the panel index by `delta`, clamped to `[0, panels.size - 1]`.
   *
   * `host.replace` fires the redraw signal bound at `PanelHost.make` —
   * we bound it to `app.requestRefresh` (Q3) so the diff baseline is
   * wiped on transition. Necessary because the terminal display can
   * drift from the buffer model across layout-context transitions —
   * cells the diff would otherwise skip (e.g. the toolbar, identical
   * between the old and new frames) may have been lost from the
   * terminal's display even though our buffer still believes they are
   * on screen. `requestRefresh` produces no flicker — no `\e[2J` is
   * emitted; the full frame reaches the terminal as one coherent batch.
   */
  private def moveTo(
    delta:    Int,
    panels:   Vector[(String, AppPanel)],
    indexRef: Ref[Int],
    host:     PanelHost
  ): ZIO[Terminal & Frame, IOException, Boolean] =
    for
      current <- indexRef.get
      next = math.max(0, math.min(panels.size - 1, current + delta))
      _ <- ZIO.when(next != current) {
        for
          _ <- indexRef.set(next)
          _ <- host.replace(panels(next)._2)
        yield ()
      }
    yield true
