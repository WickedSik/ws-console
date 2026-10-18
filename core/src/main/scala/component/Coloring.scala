package io.github.wickedsik.wsconsole
package component

import buffer.{CellStyle, Foreground}
import raster.{Braille, Polarity, RasterizedBitmap}

/**
 * How a [[BrailleImage]] colours its cells.
 *
 * Going from one colour to the picture's own colours changes what the image
 * is, so it is a choice the consumer makes, never a default: [[Uniform]] is
 * what `BrailleImage.make` uses unless told otherwise.
 */
enum Coloring:

  /** Every cell in the image's `style`. */
  case Uniform

  /**
   * Every cell in the colour of its region of the bitmap, as truecolor
   * (`Foreground.Rgb`), laid over the image's `style` so its attributes and
   * background still apply. See [[raster.Braille.colors]] for how a region
   * becomes one colour.
   */
  case Source

  /**
   * The style of each cell of a `cols × rows` block over `bitmap`: exactly
   * `rows` rows of exactly `cols` styles.
   */
  private[component] def styles(
    bitmap: RasterizedBitmap,
    cols: Int,
    rows: Int,
    polarity: Polarity,
    style: CellStyle
  ): IndexedSeq[IndexedSeq[CellStyle]] =
    this match
      case Uniform => IndexedSeq.fill(rows)(IndexedSeq.fill(cols)(style))
      case Source =>
        Braille.colors(bitmap, cols, rows, polarity).map(_.map { rgb =>
          CellStyle(fg = Foreground.Rgb((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF)).over(style)
        })
