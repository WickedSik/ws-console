package io.github.wickedsik.wsconsole
package raster

import raster.RasterizedBitmap.Pixel

import zio.Scope
import zio.test.*

object BrailleSpec extends ZIOSpecDefault:

  private val White = Pixel(1.0)
  private val Black = Pixel(0.0)
  private val Clear = Pixel(1.0, alpha = 0.0)

  /** A bitmap of `fill`, with the pixels in `marked` set to `mark`. */
  private def bitmap(width: Int, height: Int, fill: Pixel, mark: Pixel, marked: Set[(Int, Int)]): RasterizedBitmap =
    RasterizedBitmap.tabulate(width, height)((x, y) => if marked((x, y)) then mark else fill)

  private def mask(glyph: Char): Int = if glyph == ' ' then 0 else glyph - 0x2800

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Braille")(
    suite("glyph")(
      test("masks 1–255 map onto U+2801–U+28FF and back") {
        val masks = 1 to 255
        assertTrue(
          masks.forall(m => Braille.glyph(m) == (0x2800 + m).toChar),
          masks.forall(m => mask(Braille.glyph(m)) == m)
        )
      },
      test("an empty mask is a space, not U+2800") {
        assertTrue(Braille.glyph(0) == ' ')
      },
      test("every mask is the OR of its dot bits") {
        val dots = for dy <- 0 until 4; dx <- 0 until 2 yield Braille.bit(dx, dy)
        assertTrue(
          dots.sorted == Seq(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80),
          (0 to 255).forall(m => dots.filter(b => (m & b) != 0).foldLeft(0)(_ | _) == m)
        )
      },
      test("rejects a mask outside 0–255") {
        assertTrue(scala.util.Try(Braille.glyph(256)).isFailure, scala.util.Try(Braille.glyph(-1)).isFailure)
      }
    ),
    suite("encode")(
      test("one lit pixel per dot position lights exactly that dot's bit") {
        val results =
          for dy <- 0 until 4; dx <- 0 until 2 yield
            val b = bitmap(2, 4, Black, White, Set((dx, dy)))
            Braille.encode(b, 1, 1, Polarity.BrightInk, 0.5) == IndexedSeq(Braille.glyph(Braille.bit(dx, dy)).toString)
        assertTrue(results.forall(identity))
      },
      test("produces exactly rows strings of exactly cols glyphs") {
        val b = bitmap(37, 23, Black, White, Set.empty)
        val out = Braille.encode(b, 7, 3, Polarity.BrightInk, 0.5)
        assertTrue(out.size == 3, out.forall(_.length == 7), out.forall(_.forall(_ == ' ')))
      },
      test("a non-positive size encodes nothing") {
        val b = bitmap(4, 4, White, White, Set.empty)
        assertTrue(
          Braille.encode(b, 0, 2, Polarity.BrightInk, 0.5).isEmpty,
          Braille.encode(b, 2, 0, Polarity.BrightInk, 0.5).isEmpty
        )
      },
      test("edge pixels of a size that is not a multiple of the block are kept, not cropped") {
        // 3 × 5 onto a 4 × 8 dot grid: the last pixel covers the last dot entirely.
        val b = bitmap(3, 5, Black, White, Set((2, 4)))
        val out = Braille.encode(b, 2, 2, Polarity.BrightInk, 0.5)
        assertTrue((mask(out.last.last) & Braille.bit(1, 3)) != 0)
      },
      test("a dot lights when the mean ink over its area reaches the threshold") {
        // Each dot covers a 2 × 2 block holding one white column: mean ink 0.5.
        val b = RasterizedBitmap.tabulate(4, 8)((x, _) => if x % 2 == 0 then White else Black)
        assertTrue(
          Braille.encode(b, 1, 1, Polarity.BrightInk, 0.5) == IndexedSeq("⣿"),
          Braille.encode(b, 1, 1, Polarity.BrightInk, 0.51) == IndexedSeq(" ")
        )
      },
      test("DarkInk draws dark pixels and BrightInk draws bright ones") {
        val dark = bitmap(2, 4, White, Black, Set((0, 0)))
        assertTrue(
          Braille.encode(dark, 1, 1, Polarity.DarkInk, 0.5) == IndexedSeq("⠁"),
          Braille.encode(dark, 1, 1, Polarity.BrightInk, 0.5) == IndexedSeq("⣾")
        )
      },
      test("a transparent pixel lights no dot under either polarity") {
        val clear = bitmap(2, 4, Clear, Clear, Set.empty)
        val blackClear = RasterizedBitmap.tabulate(2, 4)((_, _) => Pixel(0.0, alpha = 0.0))
        assertTrue(
          Braille.encode(clear, 1, 1, Polarity.BrightInk, 0.01) == IndexedSeq(" "),
          Braille.encode(blackClear, 1, 1, Polarity.DarkInk, 0.01) == IndexedSeq(" ")
        )
      },
      test("the same bitmap and options always produce the same rows") {
        val rng = new scala.util.Random(42)
        val noise = Array.fill(61 * 47)(rng.nextInt())
        val b = RasterizedBitmap.fromArgb(61, 47, noise)
        assertTrue(
          Braille.encode(b, 13, 5, Polarity.DarkInk, 0.4) == Braille.encode(b, 13, 5, Polarity.DarkInk, 0.4)
        )
      },
      test("unicode rows survive a round trip to masks and back") {
        val b = RasterizedBitmap.tabulate(6, 8)((x, y) => if (x + y) % 3 == 0 then White else Black)
        val out = Braille.encode(b, 3, 2, Polarity.BrightInk, 0.5)
        assertTrue(out.forall(row => row.map(c => Braille.glyph(mask(c))) == row))
      }
    ),
    suite("sizing")(
      test("rowsFor keeps the aspect: aquilla.png at 60 columns is 13 rows") {
        val aquila = RasterizedBitmap.tabulate(2128, 878)((_, _) => Black)
        assertTrue(
          Braille.rowsFor(aquila, 60) == 13,
          Braille.rowsFor(aquila, 0) == 0,
          Braille.rowsFor(aquila, -3) == 0
        )
      },
      test("rowsFor follows the cell aspect") {
        val square = RasterizedBitmap.tabulate(10, 10)((_, _) => Black)
        assertTrue(Braille.rowsFor(square, 20) == 10, Braille.rowsFor(square, 20, cellAspect = 1.0) == 20)
      },
      test("fit is width-bound when the natural height fits") {
        val wide = RasterizedBitmap.tabulate(4, 2)((_, _) => Black)
        assertTrue(Braille.fit(wide, 10, 10) == (10, 3))
      },
      test("fit is height-bound when the natural height does not fit") {
        val tall = RasterizedBitmap.tabulate(2, 4)((_, _) => Black)
        assertTrue(Braille.fit(tall, 10, 2) == (2, 2))
      },
      test("fit yields nothing when no column fits or the area is empty") {
        val sliver = RasterizedBitmap.tabulate(1, 100)((_, _) => Black)
        val any = RasterizedBitmap.tabulate(4, 4)((_, _) => Black)
        assertTrue(
          Braille.fit(sliver, 5, 1) == (0, 0),
          Braille.fit(any, 0, 5) == (0, 0),
          Braille.fit(any, 5, 0) == (0, 0)
        )
      }
    )
  )
