package io.github.wickedsik.wsconsole
package buffer

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
 * '''Scope and precision.''' Not a full implementation of Unicode's East
 * Asian Width property. `cellsFor` returns 2 for the well-known wide ranges
 * (CJK, Hangul, Kana, common emoji planes, Dingbats) and 1 for everything
 * else. Ambiguous-width characters are treated as narrow — this matches most
 * modern terminals in Western locales. When a specific character's width is
 * wrong for your terminal, add its range to [[isWideCodepoint]] rather than
 * introducing per-character exceptions.
 */
object Widths:

  /** Column width of a grapheme cluster, based on its first codepoint. */
  def cellsFor(grapheme: String): Int =
    if grapheme.isEmpty then 0
    else if isWideCodepoint(grapheme.codePointAt(0)) then 2
    else 1

  /**
   * Whether the given codepoint is rendered in two terminal columns.
   *
   * The ranges are cherry-picked from Unicode's East Asian Width data
   * (Wide + Fullwidth values, plus the well-known emoji blocks that render
   * two-wide in practice regardless of their formal EAW). Terminal-agnostic
   * ambiguous cases are deliberately narrow.
   */
  def isWideCodepoint(cp: Int): Boolean =
    // Hangul Jamo (leading consonants — the visible width contributor).
    (cp >= 0x1100 && cp <= 0x115F) ||
    // CJK Radicals, Kangxi Radicals, IDS, CJK Symbols and Punctuation.
    (cp >= 0x2E80 && cp <= 0x303E) ||
    // Hiragana, Katakana, Bopomofo, Hangul Compat Jamo, Kanbun, Bopomofo Ext,
    // CJK Strokes, Katakana Phonetic Extensions, Enclosed CJK, CJK Compatibility.
    (cp >= 0x3041 && cp <= 0x33FF) ||
    // CJK Unified Ideographs Extension A.
    (cp >= 0x3400 && cp <= 0x4DBF) ||
    // CJK Unified Ideographs.
    (cp >= 0x4E00 && cp <= 0x9FFF) ||
    // Yi Syllables, Yi Radicals.
    (cp >= 0xA000 && cp <= 0xA4CF) ||
    // Hangul Syllables.
    (cp >= 0xAC00 && cp <= 0xD7A3) ||
    // CJK Compatibility Ideographs.
    (cp >= 0xF900 && cp <= 0xFAFF) ||
    // Vertical Forms, CJK Compatibility Forms, Small Form Variants.
    (cp >= 0xFE30 && cp <= 0xFE6F) ||
    // Halfwidth and Fullwidth Forms (fullwidth ASCII + fullwidth punctuation).
    (cp >= 0xFF00 && cp <= 0xFF60) ||
    // Fullwidth signs (won, yen, sterling).
    (cp >= 0xFFE0 && cp <= 0xFFE6) ||
    // Dingbats (contains ✅ ✳ ✴ ✨ and many other wide symbols).
    (cp >= 0x2700 && cp <= 0x27BF) ||
    // Miscellaneous Symbols and Pictographs (📊 ✋ ...) + Emoticons + Transport +
    // Alchemical + Geometric Shapes Ext + Sup Arrows-C + Sup Symbols & Pictographs
    // (🧹 ...) + Chess + Symbols & Pictographs Ext-A + Legacy Computing Symbols.
    (cp >= 0x1F300 && cp <= 0x1FBFF) ||
    // CJK Unified Ideographs Extension B–F + CJK Compatibility Ideographs Sup.
    (cp >= 0x20000 && cp <= 0x2FFFF)
