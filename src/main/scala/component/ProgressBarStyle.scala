package io.github.wickedsik.wsconsole
package component

/**
 * Visual variant of a [[ProgressBar]]. Each variant carries its glyph
 * table; the widget picks between them based on its `bar` argument and
 * consumes only the glyphs its rendering strategy needs.
 *
 * Kept as data rather than as a `render` method — mirrors [[buffer.BoxStyle]]
 * vs [[buffer.Canvas.drawBox]]. The style is portable and comparable;
 * the rendering strategy is the widget's responsibility.
 */
sealed trait ProgressBarStyle

object ProgressBarStyle:

  /**
   * Sub-cell precision fill with block eighths.
   *
   * `eighths(i)` renders `(i + 1) / 8` fill of a cell — index `0` is one
   * eighth (`▏`), index `7` is a full block (`█`). The widget draws full
   * cells with `full` and picks a boundary glyph from `eighths` based on
   * the fractional part; when the fractional part is zero, no boundary
   * cell is drawn.
   */
  case object Fill extends ProgressBarStyle:
    val eighths: IndexedSeq[Char] = "▏▎▍▌▋▊▉█".toVector
    val full:    Char = '█'
    val empty:   Char = ' '

  /**
   * Shaded fill with a softened leading edge.
   *
   * `shades` steps from lightest to fullest — the widget uses the shades
   * as a gradient at the fill boundary, then `full` for the interior and
   * `empty` for the track. Cell-resolution fill, no sub-cell precision.
   */
  case object Shade extends ProgressBarStyle:
    val shades: IndexedSeq[Char] = "░▒▓█".toVector
    val full:   Char = '█'
    val empty:  Char = ' '

  /**
   * Discrete pips — best when the underlying quantity is naturally
   * counted (e.g. "3 of 8 steps complete"). No partial cells; each pip
   * is either `filled` or `empty`.
   */
  case object Segmented extends ProgressBarStyle:
    val filled: Char = '■'
    val empty:  Char = '□'
