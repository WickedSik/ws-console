package io.github.wickedsik.wsconsole
package demo

import ansi.FgColor
import app.{Application, Panel as AppPanel, PanelHost}
import buffer.{CellStyle, Foreground, Frame}
import component.{Button, HBox, VBox}
import demo.panels.*
import demo.widgets.GlobalShortcuts
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
 * Component tree: `GlobalShortcuts` wrapping a `VBox` of
 * `host.root` (Fill) and a Prev/Next/Quit toolbar (Fixed 3).
 *
 * Toolbar buttons bind `Perform` actions at construction; activation
 * fires on the render-loop fiber. `GlobalShortcuts` binds `p`/`n`/`q`
 * and Tab/Shift+Tab into `Perform` actions on the wrapper — every
 * shortcut is answered by a component, so a focused text field can
 * bind any letter without losing it to the toolbar.
 *
 * Focus cycling runs through every focusable in the rendered tree.
 */
object DemoApp:

  def run: ZIO[Terminal & Frame, IOException, Unit] =
    for
      app        <- Application.make
      host       <- PanelHost.make(app.requestRedraw)
      boxes      <- FocusDemoPanel.makeBoxes
      spinner    <- SpinnerPanel.make(app)
      progress   <- ProgressBarPanel.make(app)
      inspector  <- EventInspectorPanel.make(app)
      textInput  <- TextInputDemoPanel.make
      textArea   <- TextAreaDemoPanel.make
      checkboxes <- CheckboxDemoPanel.make
      radios     <- RadioGroupDemoPanel.make
      sliders    <- SliderDemoPanel.make
      selects    <- SelectDemoPanel.make
      character  <- CharacterFormPanel.make
      scrollView <- ScrollViewDemoPanel.make
      textFlow   <- TextFlowPanel.make
      modal      <- ModalDemoPanel.make(host)
      vox        <- VoxTransmissionPanel.make
      braille    <- BrailleImagePanel.make
      panels = Vector(
                 "Welcome"          -> WelcomePanel.panel,
                 "Color Gallery"    -> ColorGalleryPanel.panel,
                 "Style Showcase"   -> StyleShowcasePanel.panel,
                 "Cursor Demo"      -> CursorDemoPanel.panel,
                 "Layout Demo"      -> LayoutDemoPanel.panel,
                 "Border Styles"    -> BorderStylesPanel.panel,
                 "Text Input"       -> textInput,
                 "Text Area"        -> textArea,
                 "Checkboxes"       -> checkboxes,
                 "Radio Groups"     -> radios,
                 "Sliders"          -> sliders,
                 "Selects"          -> selects,
                 "Character Form"   -> character,
                 "Scroll View"      -> scrollView,
                 "Text Flow"        -> textFlow,
                 "Focus Demo"       -> FocusDemoPanel.panelFor(boxes),
                 "Modal"            -> modal,
                 "Spinner"          -> spinner,
                 "Progress"         -> progress,
                 "Event Inspector"  -> inspector.panel,
                 "Vox Transmission" -> vox,
                 "Braille Image"    -> braille.panel,
                 "Farewell"         -> FarewellPanel.panel
               )

      indexRef <- Ref.make(0)

      // `indexRef` is read at effect-execution time, so the target
      // index reflects the panel stack at activation.
      navigate = (delta: Int) => moveTo(delta, panels, indexRef, host)

      toolbarStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

      prevBtn <- Button.make("Previous (p)", navigate(-1), style = toolbarStyle)
      nextBtn <- Button.make("Next (n)", navigate(+1), style = toolbarStyle)
      quitBtn <- Button.make("Quit (q)", app.quit, style = toolbarStyle)

      content = VBox(
                  Constraint.Fill     -> host.root,
                  Constraint.Fixed(3) -> HBox.evenly(prevBtn, nextBtn, quitBtn)
                )

      root <- GlobalShortcuts.make(
                content,
                Keymap[ZIO[Frame, IOException, Unit]](
                  Keymap.plain('p')                -> navigate(-1),
                  Keymap.plain('n')                -> navigate(+1),
                  Keymap.plain('q')                -> app.quit,
                  Keymap.plain(SpecialKeyCode.Tab) -> app.focusManager.focusNext,
                  Keymap.shift(SpecialKeyCode.Tab) -> app.focusManager.focusPrevious
                )
              )

      // Seed the FocusManager so `focus(nextBtn.id)` succeeds before
      // the first render's tree walk installs the real order. The
      // first frame's `setOrder` overwrites with real rects; focus
      // survives the swap because `nextBtn.id` is still in the cycle.
      seedOrder = FocusOrder(Vector(
                    FocusableEntry(prevBtn.id, Rect(0, 0, 0, 0)),
                    FocusableEntry(nextBtn.id, Rect(0, 0, 0, 0)),
                    FocusableEntry(quitBtn.id, Rect(0, 0, 0, 0))
                  ))
      _ <- app.focusManager.setOrder(seedOrder)
      _ <- app.focusManager.focus(nextBtn.id)

      // Mount the first panel before entering the loop so the initial
      // render walks a non-empty stack.
      _ <- host.push(panels.head._2)

      // Inspector observes every event that reaches `onEvent`; always
      // returns `keep=true`. Quit lives in `Application`'s `quitOn`.
      onEvent = (event: Event, result: EventResult) =>
                  inspector.observe(event, result).as(true)

      _ <- app.run(root, onEvent)
    yield ()

  /**
   * Advance the panel index by `delta`, clamped to `[0, panels.size - 1]`.
   *
   * Overlays a panel pushed above itself (the Modal demo's dialog) are
   * popped first, so `replace` swaps the demo panel rather than the
   * overlay sitting on top of it.
   *
   * `host.replace` fires the redraw signal bound at `PanelHost.make`.
   * The diff is sufficient: `BufferManager` clears `current` on swap,
   * the composite root repaints the whole tree, and the diff against
   * `previous` emits every changed cell including erasures.
   */
  private def moveTo(
    delta: Int,
    panels: Vector[(String, AppPanel)],
    indexRef: Ref[Int],
    host: PanelHost
  ): ZIO[Frame, IOException, Unit] =
    for
      current <- indexRef.get
      next = math.max(0, math.min(panels.size - 1, current + delta))
      _ <- ZIO.when(next != current) {
             for
               _ <- indexRef.set(next)
               _ <- dismissOverlays(host)
               _ <- host.replace(panels(next)._2)
             yield ()
           }
    yield ()

  /** Pop the stack down to its bottom panel. */
  private def dismissOverlays(host: PanelHost): ZIO[Frame, IOException, Unit] =
    host.visible.flatMap { stack =>
      ZIO.when(stack.size > 1)(host.pop *> dismissOverlays(host)).unit
    }
