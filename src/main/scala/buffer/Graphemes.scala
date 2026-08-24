package io.github.wickedsik.wsconsole
package buffer

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
 * ready to be stored in a single [[Cell]].
 */
private[buffer] object Graphemes:

  /**
   * Invoke `f` once per grapheme cluster in `text`, in left-to-right order.
   * A fresh `BreakIterator` is constructed per call — the class is not
   * thread-safe, and reuse across threads is a documented footgun.
   */
  def foreach(text: String)(f: String => Unit): Unit =
    if text.isEmpty then return
    val it = BreakIterator.getCharacterInstance(Locale.ROOT)
    it.setText(text)
    var start = it.first()
    var end = it.next()
    while end != BreakIterator.DONE do
      f(text.substring(start, end))
      start = end
      end = it.next()

  /** The grapheme clusters of `text` as a `Seq[String]`, left to right. */
  def toSeq(text: String): Seq[String] =
    val builder = Seq.newBuilder[String]
    foreach(text)(builder += _)
    builder.result()
