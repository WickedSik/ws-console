package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground}
import geometry.Rect
import raster.{Dither, Polarity, RasterizedBitmap}
import testkit.RenderHarness.*

import zio.Scope
import zio.test.*

object BrailleImageSpec extends ZIOSpecDefault:

  private val green = CellStyle(fg = Foreground.Named(FgColor.Green))

  // 4 × 8 pixels: one square cell of dots per cell at the default aspect, so
  // rowsFor(cols) == cols.
  private val solid = RasterizedBitmap.tabulate(4, 8)((_, _) => RasterizedBitmap.Pixel(1.0))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BrailleImage")(
    test("centres a height-bound block in a wider area") {
      for img <- BrailleImage.make(solid, green, Polarity.BrightInk)
      yield
        val buf = renderToBuffer(6, 2)(img)
        assertTrue(
          buf.glyphGrid == Vector("..⣿⣿..", "..⣿⣿.."),
          buf.styleAt(2, 0).contains(green)
        )
    },
    test("centres a width-bound block in a taller area") {
      for img <- BrailleImage.make(solid, polarity = Polarity.BrightInk)
      yield assertTrue(renderToBuffer(2, 6)(img).glyphGrid == Vector("..", "..", "⣿⣿", "⣿⣿", "..", ".."))
    },
    test("draws inside its area, offset by the area's origin") {
      for img <- BrailleImage.make(solid, polarity = Polarity.BrightInk)
      yield assertTrue(renderToBuffer(5, 4)(img, Rect(2, 1, 2, 2)).glyphGrid == Vector(
        ".....",
        "..⣿⣿.",
        "..⣿⣿.",
        "....."
      ))
    },
    test("an empty or too-small area draws nothing") {
      for
        img <- BrailleImage.make(solid, polarity = Polarity.BrightInk)
        sliver <- BrailleImage.make(
                    RasterizedBitmap.tabulate(1, 100)((_, _) => RasterizedBitmap.Pixel(1.0)),
                    polarity = Polarity.BrightInk
                  )
      yield assertTrue(
        renderToBuffer(4, 2)(img, Rect(0, 0, 0, 2)).glyphGrid.forall(_ == "...."),
        renderToBuffer(5, 1)(sliver).glyphGrid == Vector(".....")
      )
    },
    test("preferredHeight is the undistorted height for the width") {
      for img <- BrailleImage.make(solid, polarity = Polarity.BrightInk)
      yield assertTrue(
        img.preferredHeight(10).contains(10),
        img.preferredHeight(0).contains(0),
        renderToBuffer(3, 10)(img).glyphGrid.count(_ != "...") == 3
      )
    },
    test("trim crops the blank margin before fitting; off by default") {
      // 4 × 16: black top half, white bottom half. Untrimmed it is twice as
      // tall as it is wide; trimmed, the white half alone is square.
      val halves = RasterizedBitmap.tabulate(4, 16)((_, y) => RasterizedBitmap.Pixel(if y >= 8 then 1.0 else 0.0))
      for
        whole   <- BrailleImage.make(halves, polarity = Polarity.BrightInk)
        trimmed <- BrailleImage.make(halves, polarity = Polarity.BrightInk, trim = true)
      yield assertTrue(
        !whole.trim,
        whole.preferredHeight(2).contains(4),
        trimmed.preferredHeight(2).contains(2),
        renderToBuffer(2, 4)(whole).glyphGrid == Vector("..", "..", "⣿⣿", "⣿⣿"),
        renderToBuffer(2, 4)(trimmed).glyphGrid == Vector("..", "⣿⣿", "⣿⣿", ".."),
        trimmed.rasterizedBitmap eq halves
      )
    },
    test("trim under dithering keeps the dim margin a dither can draw") {
      // 4 × 16: dim top half (ink 0.1), white bottom half. The threshold cut
      // treats the dim half as blank; error diffusion lights dots in it.
      val dimTop = RasterizedBitmap.tabulate(4, 16)((_, y) => RasterizedBitmap.Pixel(if y >= 8 then 1.0 else 0.1))
      for
        cut <- BrailleImage.make(dimTop, polarity = Polarity.BrightInk, trim = true)
        diffused <-
          BrailleImage.make(dimTop, polarity = Polarity.BrightInk, dither = Dither.FloydSteinberg, trim = true)
      yield assertTrue(
        cut.dither == Dither.None,
        cut.preferredHeight(2).contains(2),
        diffused.preferredHeight(2).contains(4)
      )
    },
    test("Uniform is the default and draws every lit cell in style") {
      val halves =
        RasterizedBitmap.fromArgb(4, 8, Array.tabulate(32)(i => if i % 4 < 2 then 0xFFFF80C0 else 0xFF00FFFF))
      for img <- BrailleImage.make(halves, green, Polarity.BrightInk)
      yield
        val buf = renderToBuffer(2, 2)(img)
        assertTrue(
          img.coloring == Coloring.Uniform,
          buf.charAt(0, 0).contains('⣿'),
          buf.charAt(1, 1).contains('⣿'),
          buf.styleAt(0, 0).contains(green),
          buf.styleAt(1, 1).contains(green)
        )
    },
    test("Source paints each lit cell its region's colour over style; a blank cell stays in style") {
      // 6 × 8 at 3 × 2 cells: pink left third, cyan middle third, black right third.
      // Both colours are bright enough (luminance about 0.7) to light every dot.
      val bold = CellStyle(attributes = Set(Attribute.Bold))
      val thirds = RasterizedBitmap.fromArgb(
        6,
        8,
        Array.tabulate(48)(i =>
          i % 6 match
            case 0 | 1 => 0xFFFF80C0
            case 2 | 3 => 0xFF00FFFF
            case _     => 0xFF000000
        )
      )
      for img <- BrailleImage.make(thirds, bold, Polarity.BrightInk, coloring = Coloring.Source)
      yield
        val buf = renderToBuffer(3, 2)(img)
        assertTrue(
          buf.charAt(0, 0).contains('⣿'),
          buf.charAt(1, 1).contains('⣿'),
          buf.styleAt(0, 0).contains(CellStyle(fg = Foreground.Rgb(255, 128, 192), attributes = Set(Attribute.Bold))),
          buf.styleAt(1, 1).contains(CellStyle(fg = Foreground.Rgb(0, 255, 255), attributes = Set(Attribute.Bold))),
          buf.charAt(2, 0).contains(' '),
          buf.styleAt(2, 0).contains(bold)
        )
    },
    test("encodes once per size and again when the size changes") {
      for img <- BrailleImage.make(solid)
      yield
        val first = img.encoded(2, 2)
        val again = img.encoded(2, 2)
        val other = img.encoded(3, 3)
        val back = img.encoded(2, 2)
        assertTrue(first eq again, !(other eq first), back == first, !(back eq first))
    },
    test("rejects a threshold outside [0, 1] and a non-positive cell aspect") {
      assertTrue(
        scala.util.Try(BrailleImage.make(solid, threshold = 1.5)).isFailure,
        scala.util.Try(BrailleImage.make(solid, cellAspect = 0.0)).isFailure
      )
    },
    test("is not focusable") {
      for img <- BrailleImage.make(solid)
      yield assertTrue(!img.focusable)
    }
  )
