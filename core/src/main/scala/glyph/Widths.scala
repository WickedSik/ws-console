package io.github.wickedsik.wsconsole
package glyph

/**
 * Column-width classification for a grapheme cluster.
 *
 * A terminal renders most characters in one column, but East-Asian ideographs,
 * Hangul syllables, and most emoji occupy two columns. The buffer layer needs
 * this so a wide grapheme can reserve a "continuation" cell to its right —
 * without it, the cursor advances by one buffer position while the terminal
 * cursor advances by two, and the next text glyph overwrites the wide char's
 * shadow half (which most terminals silently absorb, producing the
 * emoji-crashes-into-next-word artifact this file exists to prevent).
 *
 * '''Source.''' A code point is wide when Unicode gives it East Asian Width
 * W (Wide) or F (Fullwidth); the table is generated from Unicode's
 * `EastAsianWidth.txt` into [[WideRanges]] by
 * `scripts/generate-wide-ranges.py`. Ambiguous-width (A) characters are
 * narrow, matching most modern terminals in Western locales. A cluster's
 * width follows its first code point, so a variation selector does not
 * widen it (`⚠` + U+FE0F stays one column), with one exception: a flag.
 * To follow a newer Unicode release, bump the script's version and rerun
 * it; do not patch the table by hand.
 */
object Widths:

  /**
   * Column width of a grapheme cluster, based on its first codepoint — except
   * a flag, whose two regional indicators are each narrow on their own but
   * draw two columns wide as a pair.
   */
  def cellsFor(grapheme: String): Int =
    if grapheme.isEmpty then 0
    else if isWideCodepoint(grapheme.codePointAt(0)) || isFlag(grapheme) then 2
    else 1

  /** Regional Indicator Symbols A–Z (U+1F1E6–U+1F1FF), the halves of a flag. */
  private def isRegionalIndicator(cp: Int): Boolean = cp >= 0x1F1E6 && cp <= 0x1F1FF

  /**
   * A cluster made of a regional-indicator pair (🇳🇱). `Graphemes` pairs them
   * per UAX #29, so a flag arrives as one cluster. A lone indicator stays
   * narrow, as Unicode's East Asian Width data has it.
   */
  private def isFlag(grapheme: String): Boolean =
    isRegionalIndicator(grapheme.codePointAt(0)) && grapheme.codePointCount(0, grapheme.length) >= 2

  /**
   * Column width of a whole string: the sum of [[cellsFor]] over its grapheme
   * clusters. This is how many columns `Canvas.putText` advances for `text`,
   * so callers laying out consecutive runs (styled spans, word wrap) should
   * measure with this rather than `String.length`, which over- or
   * under-counts for wide glyphs, surrogate pairs, and multi-codepoint
   * clusters.
   */
  def stringWidth(text: String): Int =
    var total = 0
    Graphemes.foreach(text)(g => total += cellsFor(g))
    total

  /**
   * The longest prefix of `text`, in whole grapheme clusters, whose
   * [[stringWidth]] is at most `columns`. A wide grapheme that would
   * straddle the limit is left out rather than split.
   */
  def truncate(text: String, columns: Int): String =
    if columns <= 0 then ""
    else
      val out = new StringBuilder
      var used = 0
      var full = false
      Graphemes.foreach(text) { g =>
        if !full then
          val w = cellsFor(g)
          if used + w <= columns then
            out ++= g
            used += w
          else full = true
      }
      out.toString

  /**
   * Whether the given code point is rendered in two terminal columns: East
   * Asian Width W or F, by binary search over [[WideRanges.bounds]].
   */
  def isWideCodepoint(cp: Int): Boolean =
    val bounds = WideRanges.bounds
    // Nothing below the first wide range (U+1100) is wide: the common case, ASCII and Latin.
    if cp < bounds.head then false
    else
      var low = 0
      var high = bounds.length / 2 - 1
      var found = false
      while !found && low <= high do
        val mid = (low + high) >>> 1
        if cp < bounds(2 * mid) then high = mid - 1
        else if cp > bounds(2 * mid + 1) then low = mid + 1
        else found = true
      found
