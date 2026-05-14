package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{BgColor, FgColor}
import app.Panel as AppPanel
import buffer.*
import component.*
import demo.DemoUtils
import geometry.Rect
import layout.Constraint

/**
 * Showcases the full color capabilities: 16-color, 256-color palette,
 * and true-color RGB gradients.
 *
 * The dense per-cell rendering (216-cell palette + 78-cell gradient + 32
 * named-colour swatches) does not benefit from a structural decomposition
 * into hundreds of nested components. The body is wrapped in a
 * `RawCanvas` escape hatch — the surrounding tree provides the header
 * and section structure; the leaf hands a sub-canvas to direct
 * cell-painting code.
 */
object ColorGalleryPanel:

  /** Component tree — public so demo orchestrators can mount it directly. */
  val tree: Component = VBox(
    Constraint.Fixed(3) -> Panel(
      border = BoxStyle.Double,
      style  = DemoUtils.HeaderStyle,
      child  = Text("Color Gallery", DemoUtils.HeaderStyle, Alignment.Center)
    ),
    Constraint.Fixed(1) -> Spacer,
    Constraint.Fill     -> RawCanvas { canvas =>
      drawStandardForeground(canvas, 0)
      drawStandardBackground(canvas, 5)
      draw256Palette(canvas, 9)
      drawRgbGradient(canvas, 17)
    }
  )

  /** Layer 7 panel — full-screen bounds, default lifecycle. */
  val panel: AppPanel = AppPanel.of(tree, Rect(0, 0, 80, 24))

  // ===== Cell-painting helpers (operate on RawCanvas's sub-canvas) =====

  private def drawStandardForeground(canvas: Canvas, startY: Int): Unit =
    val standardColors = Seq(
      "Black"     -> FgColor.Black,
      "Red"       -> FgColor.Red,
      "Green"     -> FgColor.Green,
      "Yellow"    -> FgColor.Yellow,
      "Blue"      -> FgColor.Blue,
      "Magenta"   -> FgColor.Magenta,
      "Cyan"      -> FgColor.Cyan,
      "White"     -> FgColor.White
    )
    val brightColors = Seq(
      "BrightBlk" -> FgColor.BrightBlack,
      "BrightRed" -> FgColor.BrightRed,
      "BrightGrn" -> FgColor.BrightGreen,
      "BrightYel" -> FgColor.BrightYellow,
      "BrightBlu" -> FgColor.BrightBlue,
      "BrightMag" -> FgColor.BrightMagenta,
      "BrightCyn" -> FgColor.BrightCyan,
      "BrightWht" -> FgColor.BrightWhite
    )

    canvas.putText(0, startY, "Standard Foreground (16 colors)", DemoUtils.SectionLabelStyle)

    var x = 2
    standardColors.foreach { (name, color) =>
      canvas.putText(x, startY + 1, f"$name%-9s ", CellStyle(fg = Foreground.Named(color)))
      x += 10
    }

    x = 2
    brightColors.foreach { (name, color) =>
      canvas.putText(x, startY + 2, f"$name%-10s", CellStyle(fg = Foreground.Named(color)))
      x += 10
    }

  private def drawStandardBackground(canvas: Canvas, startY: Int): Unit =
    val bgColors = Seq(
      "Blk"  -> BgColor.Black,         "Red"  -> BgColor.Red,
      "Grn"  -> BgColor.Green,         "Yel"  -> BgColor.Yellow,
      "Blu"  -> BgColor.Blue,          "Mag"  -> BgColor.Magenta,
      "Cyn"  -> BgColor.Cyan,          "Wht"  -> BgColor.White,
      "BBlk" -> BgColor.BrightBlack,   "BRed" -> BgColor.BrightRed,
      "BGrn" -> BgColor.BrightGreen,   "BYel" -> BgColor.BrightYellow,
      "BBlu" -> BgColor.BrightBlue,    "BMag" -> BgColor.BrightMagenta,
      "BCyn" -> BgColor.BrightCyan,    "BWht" -> BgColor.BrightWhite
    )

    canvas.putText(0, startY, "Standard Background (16 colors)", DemoUtils.SectionLabelStyle)

    var x = 2
    bgColors.foreach { (name, color) =>
      val style = CellStyle(
        fg = Foreground.Named(FgColor.White),
        bg = Background.Named(color)
      )
      canvas.putText(x, startY + 1, f" $name%-4s", style)
      x += 5
    }

  private def draw256Palette(canvas: Canvas, startY: Int): Unit =
    canvas.putText(0, startY, "256-Color Palette (216 RGB cube)", DemoUtils.SectionLabelStyle)

    var row = 0
    while row < 6 do
      var col = 0
      while col < 36 do
        val index = 16 + row * 36 + col
        val style = CellStyle(bg = Background.Indexed(index))
        canvas.putChar(2 + col * 2,     startY + 1 + row, ' ', style)
        canvas.putChar(2 + col * 2 + 1, startY + 1 + row, ' ', style)
        col += 1
      row += 1

  private def drawRgbGradient(canvas: Canvas, startY: Int): Unit =
    canvas.putText(0, startY, "True Color RGB Gradient", DemoUtils.SectionLabelStyle)

    var i = 0
    while i < 78 do
      val hue       = (i.toDouble / 78.0) * 360.0
      val (r, g, b) = hsvToRgb(hue, 1.0, 1.0)
      canvas.putChar(2 + i, startY + 1, ' ', CellStyle(bg = Background.Rgb(r, g, b)))
      i += 1

  /** Convert HSV (hue 0-360, saturation 0-1, value 0-1) to RGB (0-255 each) */
  private def hsvToRgb(h: Double, s: Double, v: Double): (Int, Int, Int) =
    val c = v * s
    val x = c * (1.0 - math.abs((h / 60.0) % 2.0 - 1.0))
    val m = v - c
    val (r1, g1, b1) =
      if h < 60       then (c, x, 0.0)
      else if h < 120 then (x, c, 0.0)
      else if h < 180 then (0.0, c, x)
      else if h < 240 then (0.0, x, c)
      else if h < 300 then (x, 0.0, c)
      else                 (c, 0.0, x)
    (((r1 + m) * 255).toInt, ((g1 + m) * 255).toInt, ((b1 + m) * 255).toInt)
