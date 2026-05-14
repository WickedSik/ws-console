package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.{Alignment, Panel, Spacer, Text, VBox}
import geometry.Rect

/**
 * Title screen panel introducing the ws-console demo.
 *
 * Migrated to Layer 7: the existing Layer 4 component tree becomes the
 * `root` of an [[AppPanel]] occupying the full demo region. Default
 * `onUnload` (region clear) replaces the manual cleanup boilerplate.
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

  /** The component tree — preserved verbatim from the pre-migration shape. */
  val tree: Panel = Panel(
    border = BoxStyle.Double,
    style  = borderStyle,
    child  = VBox(
      Spacer,
      Text("ws-console", titleStyle, Alignment.Center),
      Spacer,
      Text("ZIO-Native Terminal Graphics Library", subtitleStyle, Alignment.Center),
      Spacer,
      Text("Layer 7: Application Framework", phaseStyle, Alignment.Center),
      Spacer,
      Text("Press any key to advance  |  q or Ctrl+C to exit", instructionStyle, Alignment.Center),
      Spacer
    )
  )

  /** Bounds matching the pre-migration `Rect(2, 1, 78, 11)` layout. */
  val bounds: Rect = Rect(2, 1, 78, 11)

  /** Layer 7 panel — full default lifecycle (no-op mount, region-clear unload). */
  val panel: AppPanel = AppPanel.of(tree, bounds)
