package io.github.wickedsik.wsconsole
package raster

/**
 * Which pixels draw: dark ones or bright ones.
 *
 * A pixel's ink is its alpha times the chosen brightness, so a
 * transparent pixel carries no ink under either polarity.
 */
enum Polarity:

  /** Dark pixels draw — art on a light background. */
  case DarkInk

  /** Bright pixels draw — art on a dark background. */
  case BrightInk

  /** Ink in `[0, 1]` for a pixel of `luminance` and `alpha`. */
  def ink(luminance: Float, alpha: Float): Double = this match
    case DarkInk   => alpha * (1.0 - luminance)
    case BrightInk => alpha * luminance.toDouble
