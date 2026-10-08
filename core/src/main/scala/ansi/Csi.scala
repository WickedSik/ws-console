package io.github.wickedsik.wsconsole
package ansi

/**
 * The C0 control characters this library names.
 *
 * Single source of truth: nothing else in the codebase may define or
 * inline these bytes. A raw 0x00 or 0x1B in a source file is invisible in
 * most editors and survives review unnoticed, so `ControlByteHygieneSpec`
 * fails the build if one appears outside this file.
 *
 * Both a `String` and a `Char` form exist for ESC because the two are not
 * interchangeable: sequence construction concatenates, while parsers and
 * `match` arms compare a single character.
 */
object Csi:
  val ESC: String = "\u001B" // Escape character

  /** ESC (0x1B) as a single character, for parsing and pattern matching. */
  val EscChar: Char = '\u001B'

  /**
   * NUL (0x00) — the C0 null character.
   *
   * Invariant across every supported terminal because it is fixed by the
   * character encoding, not by any terminal's capabilities. Never
   * renderable: no component may place it in a [[buffer.Cell]] and no
   * flusher may emit it. A NUL reaching the wire always means buffer
   * state leaked somewhere it should not have.
   */
  val NUL: Char = '\u0000'
