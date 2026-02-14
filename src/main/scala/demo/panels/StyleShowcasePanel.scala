package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor}
import demo.DemoUtils
import terminal.Terminal
import zio.ZIO

import java.io.IOException

/**
 * Demonstrates all available text styles and style combinations.
 */
object StyleShowcasePanel:

  def show: ZIO[Terminal, IOException, Unit] =
    for
      _ <- DemoUtils.clearAndHeader("Style Showcase")
      _ <- individualStyles
      _ <- combinedStyles
    yield ()

  private val individualStyles: ZIO[Terminal, IOException, Unit] =
    DemoUtils.printAnsi(
      DemoUtils.sectionLabel("Individual Styles").newline
        .text("  ").bold.text("Bold text").reset.newline
        .text("  ").dim.text("Dim text").reset.newline
        .text("  ").italic.text("Italic text").reset.newline
        .text("  ").underline.text("Underline text").reset.newline
        .text("  ").strikethrough.text("Strikethrough text").reset.newline
        .text("  ").reverse.text("Reverse video").reset.newline
        .text("  ").blink.text("Blink text").reset
        .dim.text("  (terminal support varies)").reset.newline
        .newline
    )

  private val combinedStyles: ZIO[Terminal, IOException, Unit] =
    DemoUtils.printAnsi(
      DemoUtils.sectionLabel("Style Combinations").newline
        .text("  ").bold.italic.text("Bold + Italic").reset.newline
        .text("  ").bold.underline.fg(FgColor.Cyan).text("Bold + Underline + Cyan").reset.newline
        .text("  ").dim.italic.fg(FgColor.Magenta).text("Dim + Italic + Magenta").reset.newline
        .text("  ").bold.fg(FgColor.BrightYellow).underline.strikethrough.text("Bold + Yellow + Underline + Strikethrough").reset.newline
        .text("  ").italic.underline.fg(FgColor.BrightGreen).text("Italic + Underline + Green").reset.newline
    )
