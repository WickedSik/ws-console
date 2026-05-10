package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, BoxStyle, CellStyle, Foreground, Renderer}
import component.{Alignment, Panel, Spacer, Text, VBox}
import geometry.Rect
import zio.ZIO

import java.io.IOException

/**
 * Title screen panel introducing the ws-console demo.
 *
 * Migrated to the Layer 4 component model: a `Panel` with a double-line
 * border wraps a `VBox` of vertically-spaced, centered `Text` rows.
 * No manual coordinate arithmetic, no per-row `putText` calls.
 */
object WelcomePanel:

  private val borderStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private val titleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold))

  private val subtitleStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Italic))

  private val phaseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Dim))

  private val instructionStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Dim))

  private val tree: Panel = Panel(
    border = BoxStyle.Double,
    style  = borderStyle,
    child  = VBox(
      Spacer,
      Text("ws-console", titleStyle, Alignment.Center),
      Spacer,
      Text("ZIO-Native Terminal Graphics Library", subtitleStyle, Alignment.Center),
      Spacer,
      Text("Layer 5: Event System", phaseStyle, Alignment.Center),
      Spacer,
      Text("Press any key to advance  |  q or Ctrl+C to exit", instructionStyle, Alignment.Center),
      Spacer
    )
  )

  def show: ZIO[Renderer, IOException, Unit] =
    Renderer.frame { canvas =>
      tree.render(Rect(2, 1, 78, 11), canvas)
    }
