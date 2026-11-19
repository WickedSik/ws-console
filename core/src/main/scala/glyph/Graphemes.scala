package io.github.wickedsik.wsconsole
package glyph

import java.text.BreakIterator
import java.util.Locale

/**
 * Grapheme-cluster iteration over a `String`.
 *
 * "One `Char` = one cell" is wrong for anything outside the BMP: a supplementary
 * codepoint is a surrogate pair (two `Char`s = one codepoint), and a grapheme
 * cluster can span multiple codepoints (`⚠` + U+FE0F for the emoji-presentation
 * variant of the warning sign; ZWJ sequences that combine multiple people
 * into a single family glyph). Iterating `Char`-by-`Char` splits these into
 * fragments the terminal cannot render.
 *
 * `foreach` iterates the source string by Unicode grapheme cluster per the
 * built-in `BreakIterator.getCharacterInstance(Locale.ROOT)` — Java's canonical
 * implementation of UAX #29. Each cluster is delivered as its own `String`,
 * ready to be stored in a single [[buffer.Cell]].
 *
 * [[count]] is the unit of a text field's `maxLength`: a host building a
 * "Characters: n/max" counter counts with it so its number agrees with
 * what the field refuses.
 */
object Graphemes:

  /**
   * Invoke `f` once per grapheme cluster in `text`, in left-to-right order.
   * A fresh `BreakIterator` is constructed per call — the class is not
   * thread-safe, and reuse across threads is a documented footgun.
   */
  def foreach(text: String)(f: String => Unit): Unit =
    if text.nonEmpty then
      val it = BreakIterator.getCharacterInstance(Locale.ROOT)
      it.setText(text)
      var start = it.first()
      var end = it.next()
      while end != BreakIterator.DONE do
        f(text.substring(start, end))
        start = end
        end = it.next()

  /**
   * The cluster boundary before `offset`: the start of the cluster that
   * ends at or contains `offset`. `0` at or before the start.
   */
  def preceding(text: String, offset: Int): Int =
    if offset <= 0 || text.isEmpty then 0
    else if offset > text.length then text.length
    else iterator(text).preceding(offset)

  /**
   * The cluster boundary after `offset`: the end of the cluster that
   * starts at or contains `offset`. `text.length` at or past the end.
   */
  def following(text: String, offset: Int): Int =
    if offset >= text.length then text.length
    else if offset < 0 then 0
    else iterator(text).following(offset)

  /** `offset` itself when it is a cluster boundary, else the boundary before it. */
  def floor(text: String, offset: Int): Int =
    if offset <= 0 || text.isEmpty then 0
    else if offset >= text.length then text.length
    else
      val it = iterator(text)
      if it.isBoundary(offset) then offset else it.preceding(offset)

  /** `offset` itself when it is a cluster boundary, else the boundary after it. */
  def ceiling(text: String, offset: Int): Int =
    if offset <= 0 || text.isEmpty then 0
    else if offset >= text.length then text.length
    else
      val it = iterator(text)
      if it.isBoundary(offset) then offset else it.following(offset)

  private def iterator(text: String): BreakIterator =
    val it = BreakIterator.getCharacterInstance(Locale.ROOT)
    it.setText(text)
    it

  /**
   * The number of grapheme clusters in `text`: what a reader counts as
   * characters. A surrogate pair, a base with its combining marks, and an
   * emoji ZWJ sequence each count once; `\n` counts once.
   */
  def count(text: String): Int =
    if text.isEmpty then 0
    else
      val it = iterator(text)
      var n = 0
      while it.next() != BreakIterator.DONE do n += 1
      n

  /** The grapheme clusters of `text` as a `Seq[String]`, left to right. */
  def toSeq(text: String): Seq[String] =
    val builder = Seq.newBuilder[String]
    foreach(text)(builder += _)
    builder.result()
