package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{AnsiBuilder, FgColor, BgColor}
import demo.DemoUtils
import zio.ZIO

import java.io.IOException

/**
 * Showcases the full color capabilities: 16-color, 256-color palette,
 * and true-color RGB gradients.
 */
object ColorGalleryPanel:

  def show: ZIO[Any, IOException, Unit] =
    for
      _ <- DemoUtils.clearAndHeader("Color Gallery")
      _ <- standardForeground
      _ <- standardBackground
      _ <- palette256
      _ <- rgbGradient
    yield ()

  private val standardForeground: ZIO[Any, Nothing, Unit] =
    val standardColors = Array(
      ("Black",   FgColor.Black),
      ("Red",     FgColor.Red),
      ("Green",   FgColor.Green),
      ("Yellow",  FgColor.Yellow),
      ("Blue",    FgColor.Blue),
      ("Magenta", FgColor.Magenta),
      ("Cyan",    FgColor.Cyan),
      ("White",   FgColor.White)
    )
    val brightColors = Array(
      ("BrightBlk", FgColor.BrightBlack),
      ("BrightRed", FgColor.BrightRed),
      ("BrightGrn", FgColor.BrightGreen),
      ("BrightYel", FgColor.BrightYellow),
      ("BrightBlu", FgColor.BrightBlue),
      ("BrightMag", FgColor.BrightMagenta),
      ("BrightCyn", FgColor.BrightCyan),
      ("BrightWht", FgColor.BrightWhite)
    )

    val builder = standardColors.foldLeft(DemoUtils.sectionLabel("Standard Foreground (16 colors)").newline.text("  ")) {
      case (b, (name, color)) =>
        b.fg(color).text(f"$name%-9s ").reset
    }.newline.text("  ")

    val withBright = brightColors.foldLeft(builder) {
      case (b, (name, color)) =>
        b.fg(color).text(f"$name%-10s").reset
    }.newline.newline

    DemoUtils.printAnsi(withBright)

  private val standardBackground: ZIO[Any, Nothing, Unit] =
    val bgColors = Array(
      ("Blk", BgColor.Black),    ("Red", BgColor.Red),
      ("Grn", BgColor.Green),    ("Yel", BgColor.Yellow),
      ("Blu", BgColor.Blue),     ("Mag", BgColor.Magenta),
      ("Cyn", BgColor.Cyan),     ("Wht", BgColor.White),
      ("BBlk", BgColor.BrightBlack),  ("BRed", BgColor.BrightRed),
      ("BGrn", BgColor.BrightGreen),  ("BYel", BgColor.BrightYellow),
      ("BBlu", BgColor.BrightBlue),   ("BMag", BgColor.BrightMagenta),
      ("BCyn", BgColor.BrightCyan),   ("BWht", BgColor.BrightWhite)
    )

    val builder = bgColors.foldLeft(DemoUtils.sectionLabel("Standard Background (16 colors)").newline.text("  ")) {
      case (b, (name, color)) =>
        b.bg(color).fg(FgColor.White).text(f" $name%-4s").reset
    }.newline.newline

    DemoUtils.printAnsi(builder)

  private val palette256: ZIO[Any, Nothing, Unit] =
    // Show the 216-color RGB cube (indices 16-231)
    var builder = DemoUtils.sectionLabel("256-Color Palette (216 RGB cube)").newline

    // 6 rows of 36 columns
    for row <- 0 until 6 do
      builder = builder.text("  ")
      for col <- 0 until 36 do
        val index = 16 + row * 36 + col
        builder = builder.bg256(index).text("  ").reset
      builder = builder.newline

    builder = builder.newline
    DemoUtils.printAnsi(builder)

  private val rgbGradient: ZIO[Any, Nothing, Unit] =
    // HSV hue sweep: red -> yellow -> green -> cyan -> blue -> magenta -> red
    var builder = DemoUtils.sectionLabel("True Color RGB Gradient").newline.text("  ")

    for i <- 0 until 78 do
      val hue = (i.toDouble / 78.0) * 360.0
      val (r, g, b) = hsvToRgb(hue, 1.0, 1.0)
      builder = builder.bgRgb(r, g, b).text(" ").reset

    builder = builder.newline
    DemoUtils.printAnsi(builder)

  /** Convert HSV (hue 0-360, saturation 0-1, value 0-1) to RGB (0-255 each) */
  private def hsvToRgb(h: Double, s: Double, v: Double): (Int, Int, Int) =
    val c = v * s
    val x = c * (1.0 - math.abs((h / 60.0) % 2.0 - 1.0))
    val m = v - c
    val (r1, g1, b1) =
      if h < 60 then (c, x, 0.0)
      else if h < 120 then (x, c, 0.0)
      else if h < 180 then (0.0, c, x)
      else if h < 240 then (0.0, x, c)
      else if h < 300 then (x, 0.0, c)
      else (c, 0.0, x)
    (((r1 + m) * 255).toInt, ((g1 + m) * 255).toInt, ((b1 + m) * 255).toInt)
