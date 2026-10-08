package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Panel as AppPanel, PanelHost}
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Frame}
import component.*
import demo.DemoUtils
import geometry.{Insets, Rect}
import layout.Constraint
import terminal.Terminal

import zio.{UIO, ZIO, ZLayer}

import java.io.IOException

/**
 * Showcase panel for a modal dialog built from shipped parts: an
 * overlay [[app.Panel]] pushed onto the [[app.PanelHost]] stack, holding
 * a [[component.Button]] whose activation pops it again.
 *
 * Modals are not a first-order component. The dialog is a plain
 * bordered [[component.Panel]] whose bounds are centred in the host
 * area; the host's z-order and the panel's opaque fill do the rest.
 *
 * Known limitation: focus is not confined to the dialog. Layer 6 has
 * no focus scope yet, so Tab still reaches the panel underneath and the
 * toolbar, and opening the dialog does not move focus into it. See
 * `.claude/tasks/focus-scope.md`; this panel is the migration target.
 */
object ModalDemoPanel:

  private val bodyStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val buttonStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val dialogStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow))

  private val dialogTitleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightYellow), attributes = Set(Attribute.Bold))

  /** Preferred dialog size; shrinks to fit smaller host areas. */
  val DialogPaddingWidth: Int = 20
  val DialogPaddingHeight: Int = 4
  val DialogWidth: Int = 44 + (DialogPaddingWidth * 2)
  val DialogHeight: Int = 8 + (DialogPaddingHeight * 2)

  /** Centre a [[DialogWidth]] × [[DialogHeight]] rect in `hostArea`, clamped to it. */
  def dialogBounds(hostArea: Rect): Rect =
    val w = math.min(DialogWidth, hostArea.width)
    val h = math.min(DialogHeight, hostArea.height)
    Rect(hostArea.x + (hostArea.width - w) / 2, hostArea.y + (hostArea.height - h) / 2, w, h)

  /** Lay `button` out at a fixed width, centred horizontally. */
  private def centred(button: Component, width: Int): Component =
    HBox(
      Constraint.Fill         -> Spacer,
      Constraint.Fixed(width) -> button,
      Constraint.Fill         -> Spacer
    )

  /**
   * Build the panel. `host` is the stack the dialog is pushed onto;
   * `terminal` is captured so the push / pop effects fit the
   * `ZIO[Frame, IOException, Unit]` shape `Button` requires, the same
   * way `DemoApp` types its navigation effects.
   */
  def make(host: PanelHost, terminal: Terminal): UIO[AppPanel] =
    val withTerminal = ZLayer.succeed(terminal)

    // Close is only rendered, and therefore only focusable, while the
    // dialog is on the stack — and nothing is pushed above it — so the
    // dialog is the top panel whenever Close can be activated.
    val close: ZIO[Frame, IOException, Unit] =
      host.pop.provideSomeLayer[Frame](withTerminal)

    for
      closeBtn <- Button.make("Close", close, style = dialogStyle)

      dialog = new AppPanel:
                 override def bounds(hostArea: Rect): Rect = dialogBounds(hostArea)
                 val root: Component = Panel(
                   title = Some(" Modal "),
                   border = BoxStyle.Double,
                   style = dialogTitleStyle,
                   padding = Insets.symmetric(DialogPaddingWidth, DialogPaddingHeight),
                   child = VBox(
                     Constraint.Fixed(1) -> Text("Enter or Space on Close dismisses this.", dialogStyle),
                     Constraint.Fixed(1) -> Text("Tab still escapes: no focus scope yet.", dialogStyle),
                     Constraint.Fill     -> Spacer,
                     Constraint.Fixed(3) -> centred(closeBtn, 13)
                   )
                 )

      // Without a focus scope the opener stays focusable under the
      // dialog, so a second activation must not stack a second copy.
      open = host.visible
               .flatMap(stack => ZIO.when(!stack.contains(dialog))(host.push(dialog)))
               .unit
               .provideSomeLayer[Frame](withTerminal)

      openBtn <- Button.make("Open modal", open, style = buttonStyle)
    yield AppPanel.of(
      VBox(
        Constraint.Fixed(3) -> Panel(
          border = BoxStyle.Double,
          style = DemoUtils.HeaderStyle,
          child = Text("Modal — overlay panel with a dismiss button", DemoUtils.HeaderStyle, Alignment.Center)
        ),
        Constraint.Fixed(1) -> Text(
          "Tab to 'Open modal' and press Enter. One more Tab reaches the dialog's Close button.",
          DemoUtils.DimStyle
        ),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(3) -> centred(openBtn, 18),
        Constraint.Fixed(1) -> Spacer,
        Constraint.Fixed(1) -> Text(
          "The dialog is an overlay pushed onto the PanelHost stack; Close pops it.",
          bodyStyle
        ),
        Constraint.Fill -> Spacer
      )
    )
