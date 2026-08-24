package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.Panel as AppPanel
import buffer.{Attribute, BoxStyle, CellStyle, Foreground}
import component.{Alignment, Panel, Spacer, Text, VBox}
import demo.DemoLayout
import geometry.{Insets, Rect}

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

  /** The component tree — Panel now demonstrates explicit padding. */
  val tree: Panel = Panel(
    border = BoxStyle.Double,
    style = borderStyle,
    padding = Insets.symmetric(horizontal = 3, vertical = 1),
    child = VBox(
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

  /** Bounds normalised to the demo's shared content region (Q2 ratification). */
  val bounds: Rect = DemoLayout.contentBounds

  /** Layer 7 panel — full default lifecycle (no-op mount, region-clear unload). */
  val panel: AppPanel = AppPanel.of(tree, bounds)
