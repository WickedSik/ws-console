package io.github.wickedsik.wsconsole
package component

import zio.Duration
import zio.durationInt

/**
 * Frame cycle for a [[Spinner]] — the ordered glyph sequence to loop
 * through, plus how long each glyph is shown.
 *
 * Frames are strings, not chars, so the same type accommodates
 * single-cell glyphs (braille dots, line-drawing) and any future
 * multi-codepoint frames without a shape change. The widget derives
 * its current frame index from the wall-clock reading on
 * [[RenderContext.timestamp]] as a pure function of real time — the
 * cycle therefore advances at [[frameInterval]] regardless of render
 * cadence.
 */
final case class SpinnerStyle(frames: IndexedSeq[String], frameInterval: Duration)

object SpinnerStyle:

  /** Default cadence for the named cycles — standard braille-spinner timing. */
  val DefaultInterval: Duration = 80.millis

  /** Custom cycle at the default cadence. */
  def apply(frames: IndexedSeq[String]): SpinnerStyle =
    SpinnerStyle(frames, DefaultInterval)

  /** 10-frame braille dot cycle — the default spinner glyph set. */
  val Braille: SpinnerStyle = SpinnerStyle(
    Vector("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"),
    DefaultInterval
  )

  /** 4-frame ASCII rotating line — the classic terminal spinner. */
  val Line: SpinnerStyle = SpinnerStyle(
    Vector("-", "\\", "|", "/"),
    DefaultInterval
  )

  /** 6-frame spinning ring — softer than the line, less dense than braille. */
  val Circle: SpinnerStyle = SpinnerStyle(
    Vector("◜", "◠", "◝", "◞", "◡", "◟"),
    DefaultInterval
  )
