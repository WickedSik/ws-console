package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{CellStyle, Foreground}
import geometry.Rect
import raster.{RasterizedBitmap, Polarity}
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
