package io.github.wickedsik.wsconsole
package raster

import raster.RasterizedBitmap.Pixel

import zio.Scope
import zio.test.*

object RasterizedBitmapSpec extends ZIOSpecDefault:

  private val White = Pixel(1.0)
  private val Black = Pixel(0.0)

  /** A `width × height` bitmap, white inside `box` (x, y, w, h) and `outside` elsewhere. */
  private def boxed(width: Int, height: Int, box: (Int, Int, Int, Int), outside: Pixel = Black): RasterizedBitmap =
    val (bx, by, bw, bh) = box
    RasterizedBitmap.tabulate(width, height)((x, y) =>
      if x >= bx && x < bx + bw && y >= by && y < by + bh then White else outside
    )

  private def size(b: RasterizedBitmap): (Int, Int) = (b.width, b.height)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Bitmap")(
    suite("fromArgb and tabulate")(
      test("weights luminance by BT.601 and keeps alpha") {
        val b = RasterizedBitmap.fromArgb(3, 1, Array(0xFFFF0000, 0x8000FF00, 0x000000FF))
        assertTrue(
          math.abs(b.luminance(0, 0) - 0.299f) < 1e-6f,
          math.abs(b.luminance(1, 0) - 0.587f) < 1e-6f,
          math.abs(b.alpha(1, 0) - 128 / 255f) < 1e-6f,
          b.alpha(2, 0) == 0.0f
        )
      },
      test("fromArgb keeps each pixel's colour") {
        val b = RasterizedBitmap.fromArgb(3, 1, Array(0xFFFF0000, 0x80FF80C0, 0x00000080))
        assertTrue(b.rgb(0, 0) == 0xFF0000, b.rgb(1, 0) == 0xFF80C0, b.rgb(2, 0) == 0x000080)
      },
      test("tabulate keeps a given colour and makes a colourless pixel the grey of its luminance") {
        val b = RasterizedBitmap.tabulate(3, 1)((x, _) =>
          x match
            case 0 => Pixel(0.5, rgb = Some(0x123456))
            case 1 => Pixel(0.5)
            case _ => Pixel(1.0)
        )
        assertTrue(b.rgb(0, 0) == 0x123456, b.rgb(1, 0) == 0x808080, b.rgb(2, 0) == 0xFFFFFF)
      },
      test("rejects a colour outside 0xRRGGBB") {
        assertTrue(
          scala.util.Try(Pixel(0.5, rgb = Some(0x1000000))).isFailure,
          scala.util.Try(Pixel(0.5, rgb = Some(-1))).isFailure
        )
      },
      test("clamps out-of-range values to [0, 1]") {
        val b = RasterizedBitmap.tabulate(1, 1)((_, _) => Pixel(1.5, alpha = -0.5))
        assertTrue(b.luminance(0, 0) == 1.0f, b.alpha(0, 0) == 0.0f)
      },
      test("rejects an empty size and a pixel count that does not match") {
        assertTrue(
          scala.util.Try(RasterizedBitmap.tabulate(0, 3)((_, _) => White)).isFailure,
          scala.util.Try(RasterizedBitmap.fromArgb(2, 2, Array(0, 0, 0))).isFailure
        )
      }
    ),
    suite("crop")(
      test("copies the region, top-left pixel first") {
        val b = RasterizedBitmap.tabulate(4, 3)((x, y) => Pixel((y * 4 + x) / 11.0))
        val c = b.crop(1, 1, 2, 2)
        val coloured = RasterizedBitmap.fromArgb(2, 2, Array(0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004))
        assertTrue(
          size(c) == (2, 2),
          c.luminance(0, 0) == b.luminance(1, 1),
          c.luminance(1, 1) == b.luminance(2, 2),
          coloured.crop(1, 1, 1, 1).rgb(0, 0) == 0x000004,
          coloured.crop(0, 1, 2, 1).rgb(0, 0) == 0x000003
        )
      },
      test("rejects a region outside the bitmap") {
        val b = RasterizedBitmap.tabulate(4, 3)((_, _) => White)
        assertTrue(
          scala.util.Try(b.crop(3, 0, 2, 1)).isFailure,
          scala.util.Try(b.crop(0, 0, 0, 1)).isFailure,
          scala.util.Try(b.crop(-1, 0, 1, 1)).isFailure
        )
      }
    ),
    suite("trim")(
      test("crops to the bounding box of the pixels that draw") {
        val b = boxed(10, 8, (2, 3, 4, 2))
        val t = b.trim(Polarity.BrightInk, 0.5)
        assertTrue(
          size(t) == (4, 2),
          (for y <- 0 until 2; x <- 0 until 4 yield t.luminance(x, y)).forall(_ == 1.0f)
        )
      },
      test("follows polarity: the same margin is ink under the other one") {
        val b = boxed(10, 8, (2, 3, 4, 2))
        assertTrue(b.trim(Polarity.DarkInk, 0.5) eq b)
      },
      test("crops a transparent margin under either polarity") {
        val clear = Pixel(0.0, alpha = 0.0)
        val bright = boxed(6, 6, (1, 1, 3, 3), clear)
        val dark = RasterizedBitmap.tabulate(6, 6)((x, y) => if x == 5 && y == 5 then Black else clear)
        assertTrue(
          size(bright.trim(Polarity.BrightInk, 0.5)) == (3, 3),
          size(dark.trim(Polarity.DarkInk, 0.5)) == (1, 1)
        )
      },
      test("counts a pixel as ink only when it reaches the threshold") {
        val faint = Pixel(0.2)
        val b = RasterizedBitmap.tabulate(6, 1)((x, _) => if x < 2 then faint else if x == 4 then White else Black)
        assertTrue(
          size(b.trim(Polarity.BrightInk, 0.5)) == (1, 1),
          size(b.trim(Polarity.BrightInk, 0.1)) == (5, 1)
        )
      },
      test("returns the bitmap unchanged when nothing draws or nothing is blank") {
        val dark = RasterizedBitmap.tabulate(5, 5)((_, _) => Black)
        val full = RasterizedBitmap.tabulate(5, 5)((_, _) => White)
        assertTrue(dark.trim(Polarity.BrightInk, 0.5) eq dark, full.trim(Polarity.BrightInk, 0.5) eq full)
      },
      test("trimming loses no dot: the trimmed picture encodes to the same glyphs") {
        // 4 × 8 of white inside an 8-pixel black border on every side: 1:1
        // pixel-to-dot either way, so the trimmed encoding is the untrimmed
        // one with the blank border cells removed.
        val b = boxed(20, 24, (8, 8, 4, 8))
        val whole = Braille.encode(b, 10, 6, Polarity.BrightInk, 0.5)
        val trimmed = Braille.encode(b.trim(Polarity.BrightInk, 0.5), 2, 2, Polarity.BrightInk, 0.5)
        assertTrue(whole.slice(2, 4).map(_.slice(4, 6)) == trimmed, trimmed == IndexedSeq("⣿⣿", "⣿⣿"))
      }
    )
  )
