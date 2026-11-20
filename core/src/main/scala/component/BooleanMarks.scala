package io.github.wickedsik.wsconsole
package component

import glyph.Widths

/**
 * The glyph pair a two-state control draws before its label: `on` when
 * checked or selected, `off` otherwise. Used by [[Checkbox]],
 * [[RadioGroup]] and the option list of [[TagInput]].
 *
 * The pair may differ in width, for example `BooleanMarks("✔", "[ ]")`.
 * A control reserves [[width]] columns for whichever mark it shows, so
 * toggling never moves the label.
 */
final case class BooleanMarks(on: String, off: String):

  /** Columns reserved for either mark: the wider of the two. */
  val width: Int = math.max(Widths.stringWidth(on), Widths.stringWidth(off))

  /** The mark for `state`. */
  def apply(state: Boolean): String = if state then on else off
