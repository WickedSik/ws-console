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

  /** Lit dots across all `rows`. */
  private def litDots(rows: IndexedSeq[String]): Int = rows.map(_.map(c => Integer.bitCount(mask(c))).sum).sum

  /** A 32 × 32 bitmap of flat `ink` under `BrightInk`: 16 × 8 cells, one pixel per dot, 1024 dots. */
  private def flat(ink: Double): RasterizedBitmap = RasterizedBitmap.tabulate(32, 32)((_, _) => Pixel(ink))

  private val Dithering = Seq(Dither.Ordered, Dither.FloydSteinberg, Dither.Atkinson)

  /** A pixel region of a square photo, `[x0, x1) × [y0, y1)`. */
  final private case class Region(x0: Int, y0: Int, x1: Int, y1: Int)

  /** Mean luminance over `r`. */
  private def luminance(b: RasterizedBitmap, r: Region): Double =
    val values = for y <- r.y0 until r.y1; x <- r.x0 until r.x1 yield b.luminance(x, y).toDouble
    values.sum / values.size

  /** Share of lit dots over `r`, for `rows` encoded from a `side × side` photo onto a `dots × dots` grid. */
  private def litShare(rows: IndexedSeq[String], side: Int, dots: Int, r: Region): Double =
    val f = dots.toDouble / side
    val lit =
      for dy <- (r.y0 * f).toInt until (r.y1 * f).toInt; dx <- (r.x0 * f).toInt until (r.x1 * f).toInt yield
        if (mask(rows(dy / 4)(dx / 2)) & Braille.bit(dx % 2, dy % 4)) != 0 then 1 else 0
    lit.sum.toDouble / lit.size

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
        val rng = scala.util.Random(42)
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
    suite("dither")(
      test("None is the default and matches the threshold cut") {
        val rng = scala.util.Random(7)
        val b = RasterizedBitmap.fromArgb(53, 41, Array.fill(53 * 41)(rng.nextInt()))
        assertTrue(
          Braille.encode(b, 11, 6, Polarity.DarkInk, 0.4) ==
            Braille.encode(b, 11, 6, Polarity.DarkInk, 0.4, Dither.None)
        )
      },
      test("flat mid-grey: the threshold cut lights all or nothing, every dither lights about half") {
        val grey = flat(0.5)
        val dithered = Dithering.map(d => litDots(Braille.encode(grey, 16, 8, Polarity.BrightInk, 0.3, d)))
        assertTrue(
          litDots(Braille.encode(grey, 16, 8, Polarity.BrightInk, 0.3)) == 1024,
          litDots(Braille.encode(grey, 16, 8, Polarity.BrightInk, 0.6)) == 0,
          dithered.head == 512,
          dithered.forall(n => n >= 460 && n <= 564)
        )
      },
      test("dot density follows the tone under Ordered and Floyd–Steinberg") {
        val levels = Seq(0.125, 0.25, 0.75, 0.875)
        val results =
          for d <- Seq(Dither.Ordered, Dither.FloydSteinberg); ink <- levels yield
            val share = litDots(Braille.encode(flat(ink), 16, 8, Polarity.BrightInk, 0.3, d)) / 1024.0
            math.abs(share - ink) <= 0.07
        assertTrue(results.forall(identity))
      },
      test("Ordered lights the exact Bayer share of a flat tone") {
        // Cut-offs sit at (k + 0.5) / 16, so a tone of n / 16 lights n of every 16 dots.
        val exact = (0 to 16).map(n => litDots(Braille.encode(flat(n / 16.0), 16, 8, Polarity.BrightInk, 0.3, Dither.Ordered)))
        assertTrue(exact == (0 to 16).map(_ * 64))
      },
      test("black lights nothing and white lights everything under every mode") {
        val modes = Dither.values.toSeq
        assertTrue(
          modes.forall(d => litDots(Braille.encode(flat(0.0), 16, 8, Polarity.BrightInk, 0.3, d)) == 0),
          modes.forall(d => litDots(Braille.encode(flat(1.0), 16, 8, Polarity.BrightInk, 0.3, d)) == 1024)
        )
      },
      test("the threshold plays no part under dithering") {
        val rng = scala.util.Random(3)
        val b = RasterizedBitmap.fromArgb(40, 40, Array.fill(40 * 40)(rng.nextInt()))
        assertTrue(
          Dithering.forall(d =>
            Braille.encode(b, 10, 5, Polarity.DarkInk, 0.1, d) == Braille.encode(b, 10, 5, Polarity.DarkInk, 0.9, d)
          )
        )
      },
      test("each mode is deterministic") {
        val rng = scala.util.Random(42)
        val b = RasterizedBitmap.fromArgb(61, 47, Array.fill(61 * 47)(rng.nextInt()))
        assertTrue(
          Dither.values.forall(d =>
            Braille.encode(b, 13, 5, Polarity.DarkInk, 0.4, d) == Braille.encode(b, 13, 5, Polarity.DarkInk, 0.4, d)
          )
        )
      },
      test("Atkinson drops a quarter of the error, so flat tones near black or white clip") {
        def share(ink: Double, d: Dither): Int = litDots(Braille.encode(flat(ink), 16, 8, Polarity.BrightInk, 0.3, d))
        assertTrue(
          share(0.125, Dither.Atkinson) == 0,
          share(0.125, Dither.FloydSteinberg) > 0,
          share(0.875, Dither.Atkinson) == 1024,
          share(0.875, Dither.FloydSteinberg) < 1024,
          share(0.25, Dither.Atkinson) < 256,
          share(0.75, Dither.Atkinson) > 768
        )
      },
      test("minInk is the threshold, the lowest Bayer cut-off, or any ink at all") {
        assertTrue(
          Dither.None.minInk(0.3) == 0.3,
          Dither.Ordered.minInk(0.3) == 1.0 / 32,
          Dither.FloydSteinberg.minInk(0.3) == Double.MinPositiveValue,
          Dither.Atkinson.minInk(0.3) == Double.MinPositiveValue
        )
      }
    ),
    suite("colors")(
      test("one colour per cell: exactly rows rows of exactly cols colours; nothing for a non-positive size") {
        val b = RasterizedBitmap.fromArgb(4, 8, Array.fill(32)(0xFF336699))
        val out = Braille.colors(b, 2, 2, Polarity.BrightInk)
        assertTrue(
          out.size == 2,
          out.forall(_.size == 2),
          out.flatten.forall(_ == 0x336699),
          Braille.colors(b, 0, 2, Polarity.BrightInk).isEmpty,
          Braille.colors(b, 2, 0, Polarity.BrightInk).isEmpty
        )
      },
      test("pixels count by their ink: a cell half pink, half black is pink under BrightInk") {
        val b = RasterizedBitmap.fromArgb(2, 4, Array.tabulate(8)(i => if i % 2 == 0 then 0xFFFF80C0 else 0xFF000000))
        assertTrue(Braille.colors(b, 1, 1, Polarity.BrightInk) == IndexedSeq(IndexedSeq(0xFF80C0)))
      },
      test("under DarkInk the dark pixels set the colour: half white, half navy is navy") {
        val b = RasterizedBitmap.fromArgb(2, 4, Array.tabulate(8)(i => if i % 2 == 0 then 0xFFFFFFFF else 0xFF000080))
        assertTrue(Braille.colors(b, 1, 1, Polarity.DarkInk) == IndexedSeq(IndexedSeq(0x000080)))
      },
      test("a cell without ink takes the plain mean of its pixels") {
        val b = RasterizedBitmap.fromArgb(2, 4, Array.tabulate(8)(i => if i % 2 == 0 then 0x00FF0000 else 0x000000FF))
        assertTrue(
          Braille.colors(b, 1, 1, Polarity.BrightInk) == IndexedSeq(IndexedSeq(0x800080)),
          Braille.colors(b, 1, 1, Polarity.DarkInk) == IndexedSeq(IndexedSeq(0x800080))
        )
      },
      test("each cell averages only its own region") {
        // 4 × 4: left half red, right half blue, onto 2 × 1 cells.
        val b = RasterizedBitmap.fromArgb(4, 4, Array.tabulate(16)(i => if i % 4 < 2 then 0xFFFF0000 else 0xFF0000FF))
        assertTrue(Braille.colors(b, 2, 1, Polarity.BrightInk) == IndexedSeq(IndexedSeq(0xFF0000, 0x0000FF)))
      }
    ),
    suite("photo")(
      test("smiley-xp.png: dithering keeps face, sky and grass apart; the threshold cut merges them") {
        // 1064 × 1064 at 60 × 30 cells: 120 × 120 dots. A pale yellow face
        // (luminance about 0.85), blue sky (0.46) and green grass (0.33).
        val face = Region(560, 300, 720, 400)
        val sky = Region(100, 50, 300, 150)
        val grass = Region(100, 850, 900, 1000)
        for photo <- ImageLoader.loadResource("assets/smiley-xp.png", getClass.getClassLoader)
        yield
          def shares(d: Dither): Seq[Double] =
            val rows = Braille.encode(photo, 60, 30, Polarity.BrightInk, 0.3, d)
            Seq(face, sky, grass).map(litShare(rows, 1064, 120, _))
          val tones = Seq(face, sky, grass).map(luminance(photo, _))
          val cut = shares(Dither.None)
          assertTrue(
            photo.width == 1064,
            photo.height == 1064,
            cut.take(2) == Seq(1.0, 1.0),
            Seq(Dither.Ordered, Dither.FloydSteinberg).forall(d =>
              shares(d).zip(tones).forall((lit, tone) => math.abs(lit - tone) <= 0.05)
            ),
            Dithering.forall { d =>
              val s = shares(d)
              s(0) > s(1) && s(1) > s(2)
            }
          )
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
