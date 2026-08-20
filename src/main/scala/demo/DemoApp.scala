package io.github.wickedsik.wsconsole
package demo

import ansi.FgColor
import app.{Application, Panel as AppPanel, PanelHost}
import buffer.{CellStyle, Foreground, Frame}
import component.{Button, HBox, VBox}
import demo.panels.*
import demo.widgets.GlobalShortcuts
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
 *   - The component tree is a `GlobalShortcuts` wrapper around a
 *     `VBox`:
 *     - Top region (`Fill`): `host.root` — the composite root that walks
 *       the `PanelHost` panel stack per render
 *     - Bottom region (`Fixed(3)`): a `Toolbar` of library `Button`s —
 *       Previous, Next, Quit — each bound to a `Perform` action at
 *       construction. Activation (Enter / Space) fires the action on
 *       the render-loop fiber; no polling, no shared flag.
 *   - `GlobalShortcuts` binds `p` / `n` / `q` and `Tab` / `Shift+Tab`
 *     into `Perform` actions on the wrapper. Every shortcut is
 *     answered by a component, so a focused text field can bind any
 *     of these letters without losing them to the toolbar (§6.3).
 *   - Panel navigation is `host.replace(panels(next)._2)` in `moveTo`,
 *     which fires the redraw signal bound at `PanelHost.make` — plain
 *     `app.requestRedraw`. The diff emits exactly the cells the swap
 *     changed, erasures included; a panel swap needs no baseline wipe.
 *   - Focus cycling (Tab / Shift+Tab) runs through every focusable in
 *     the rendered tree — toolbar buttons always, plus panel-local
 *     focusables when the active panel exposes them.
 */
object DemoApp:

  def run: ZIO[Terminal & Frame, IOException, Unit] =
    for
      app       <- Application.make
      // Capture Terminal so panel-navigation effects can be typed as
      // ZIO[Frame, IOException, Unit] — the Perform payload contract.
      // Panel lifecycle hooks still require Terminal internally; we
      // bind it here so components stay Terminal-free.
      terminal  <- ZIO.service[Terminal]
      host      <- PanelHost.make(app.requestRedraw)
      boxes     <- FocusDemoPanel.makeBoxes
      spinner   <- SpinnerPanel.make(app)
      progress  <- ProgressBarPanel.make(app)
      inspector <- EventInspectorPanel.make(app)
      textInput <- TextInputDemoPanel.make
      panels = Vector(
        "Welcome"         -> WelcomePanel.panel,
        "Color Gallery"   -> ColorGalleryPanel.panel,
        "Style Showcase"  -> StyleShowcasePanel.panel,
        "Cursor Demo"     -> CursorDemoPanel.panel,
        "Layout Demo"     -> LayoutDemoPanel.panel,
        "Border Styles"   -> BorderStylesPanel.panel,
        "Text Input"      -> textInput,
        "Focus Demo"      -> FocusDemoPanel.panelFor(boxes),
        "Spinner"         -> spinner,
        "Progress"        -> progress,
        "Event Inspector" -> inspector.panel,
        "Farewell"        -> FarewellPanel.panel
      )

      indexRef <- Ref.make(0)

      // Panel-navigation actions typed for `Perform`. `indexRef` is
      // read at effect-execution time, so the target index reflects
      // the panel stack at the moment of activation.
      navigate = (delta: Int) =>
                   moveTo(delta, panels, indexRef, host)
                     .provideSomeLayer[Frame](ZLayer.succeed(terminal))

      // Base toolbar role — the `accent` hue. The Button widget adds Bold
      // when focused per §2.3 (state owns attributes, role owns hue), so
      // Tab lands on a Bold-BrightCyan button while its siblings render
      // as plain BrightCyan.
      toolbarStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

      prevBtn <- Button.make("Previous (p)", navigate(-1), style = toolbarStyle)
      nextBtn <- Button.make("Next (n)",     navigate(+1), style = toolbarStyle)
      quitBtn <- Button.make("Quit (q)",     app.quit,     style = toolbarStyle)

      content = VBox(
        Constraint.Fill     -> host.root,
        Constraint.Fixed(3) -> HBox(prevBtn, nextBtn, quitBtn)
      )

      root <- GlobalShortcuts.make(content) {
        case CharKey('p', mods) if mods.isEmpty => navigate(-1)
        case CharKey('n', mods) if mods.isEmpty => navigate(+1)
        case CharKey('q', mods) if mods.isEmpty => app.quit
        case SpecialKey(SpecialKeyCode.Tab, mods) =>
          if mods.contains(KeyModifier.Shift) then app.focusManager.focusPrevious()
          else app.focusManager.focusNext()
      }

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

      // The EventInspector observes every event that reaches `onEvent`
      // — including keys a component answered with Perform / RequestRedraw
      // / Consumed. Composed into `onEvent` as a side-effect that always
      // returns `keep=true`; quit still lives in `Application`'s `quitOn`.
      onEvent = (event: Event, result: EventResult) =>
                  inspector.observe(event, result).as(true)

      _ <- app.run(root, onEvent)
    yield ()

  /**
   * Advance the panel index by `delta`, clamped to `[0, panels.size - 1]`.
   *
   * `host.replace` fires the redraw signal bound at `PanelHost.make` —
   * plain `app.requestRedraw`. The diff is sufficient: `BufferManager`
   * clears `current` on every swap, the composite root repaints the
   * whole tree, and the diff against `previous` emits every changed
   * cell including the erasures where the outgoing panel had content.
   *
   * This binding was `app.requestRefresh` between 2026-05-16 and
   * 2026-07-31, on the belief that the terminal display drifts from the
   * buffer model across layout-context transitions. It does not. The
   * "drift" was `sbt` injecting `ED 0` into the shared TTY, erasing
   * everything below the cursor our last cell write left behind; a
   * full-frame re-emit merely ended at the bottom-right corner where
   * that erase had nothing to take. With the cause addressed
   * (`scripts/run-demo.sh`, plus the cursor park in `Frame.render`) the
   * baseline wipe buys nothing and costs a full frame per swap — 77 KB
   * at 36×141 against roughly 4 KB for the diff. Overturns Q3 of
   * `panel-opacity-and-panelhost-activation.md`, whose premise was the
   * misdiagnosis. See `.claude/tasks/demo-toolbar-disappearance.md`.
   */
  private def moveTo(
    delta:    Int,
    panels:   Vector[(String, AppPanel)],
    indexRef: Ref[Int],
    host:     PanelHost
  ): ZIO[Terminal & Frame, IOException, Unit] =
    for
      current <- indexRef.get
      next = math.max(0, math.min(panels.size - 1, current + delta))
      _ <- ZIO.when(next != current) {
        for
          _ <- indexRef.set(next)
          _ <- host.replace(panels(next)._2)
        yield ()
      }
    yield ()
