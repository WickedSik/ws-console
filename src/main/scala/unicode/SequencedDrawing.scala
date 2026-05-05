package io.github.wickedsik.wsconsole
package unicode

/**
 * Frame sequences for animated terminal glyphs.
 *
 * Each entry is a single character (BMP code point) intended for direct
 * placement in a [[buffer.Cell]]. Frames are indexed by integer; consumers
 * cycle through them modulo `length`.
 */
object SequencedDrawing:
  /** Eighths-block elements for sub-character precision progress bars. */
  val ProgressBar: Seq[Char] = Seq(' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█')

  /** Braille-dot frames for a rotating spinner. */
  val Spinner: Seq[Char] = Seq('⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏')
