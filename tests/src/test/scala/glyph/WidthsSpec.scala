package io.github.wickedsik.wsconsole
package glyph

import zio.test.*

/**
 * Pins the width classifier behind the continuation-cell mechanism. The
 * wide table is generated from Unicode's East Asian Width data (see
 * [[Widths.isWideCodepoint]]); this spec covers the common cases users hit,
 * one representative of every class the hand-kept table used to get wrong,
 * the table's range edges, and the table's shape.
 */
object WidthsSpec extends ZIOSpecDefault:

  def spec = suite("Widths")(cellsForSuite, stringWidthSuite, wideCodepointSuite, tableSuite)

  private val stringWidthSuite = suite("Widths.stringWidth")(
    test("ASCII width equals length") {
      assertTrue(Widths.stringWidth("Master.") == 7)
    },
    test("CJK corner brackets 『』 count two columns each") {
      // U+300E / U+300F — the character-card case that exposed span drift.
      assertTrue(
        Widths.stringWidth("『") == 2,
        Widths.stringWidth("』") == 2,
        Widths.stringWidth("『You are awake, Master.』 ") == 27
      )
    },
    test("surrogate-pair emoji counts two columns, not its Char length") {
      assertTrue(Widths.stringWidth("🧹 done") == 7)
    },
    test("base + variation selector is one narrow cluster") {
      // ⚠ + U+FE0F: two Chars, one grapheme, first codepoint narrow.
      assertTrue(Widths.stringWidth("⚠️") == 1)
    },
    test("empty string is zero columns") {
      assertTrue(Widths.stringWidth("") == 0)
    }
  )

  private val cellsForSuite = suite("Widths.cellsFor")(
    test("ASCII is narrow") {
      assertTrue(Widths.cellsFor("A") == 1, Widths.cellsFor(" ") == 1)
    },
    test("BMP warning sign ⚠ is narrow (matches terminal rendering)") {
      // ⚠ = U+26A0 is Neutral EAW — terminals render one column.
      assertTrue(Widths.cellsFor("⚠") == 1)
    },
    test("emoji from the Supplemental Symbols & Pictographs plane is wide") {
      // 🧹 = U+1F9F9 — the very case that started this whole thread.
      assertTrue(Widths.cellsFor("🧹") == 2)
    },
    test("emoji from the Miscellaneous Symbols & Pictographs plane is wide") {
      // 📊 = U+1F4CA, 👋 = U+1F44B — the two ExitCommand and ContextCommand
      // sources of the column-overlap visible in the last screenshot.
      assertTrue(Widths.cellsFor("📊") == 2, Widths.cellsFor("👋") == 2)
    },
    test("Dingbats block ✅ is wide") {
      // ✅ = U+2705 is EAW Wide despite living in the BMP.
      assertTrue(Widths.cellsFor("✅") == 2)
    },
    test("CJK Unified Ideographs are wide") {
      assertTrue(Widths.cellsFor("字") == 2, Widths.cellsFor("中") == 2)
    },
    test("Hangul syllables are wide") {
      assertTrue(Widths.cellsFor("한") == 2)
    },
    test("a flag (regional-indicator pair) is wide; a lone indicator is narrow") {
      val loneN = String(Character.toChars(0x1F1F3))
      assertTrue(
        Widths.cellsFor("🇳🇱") == 2,
        Widths.cellsFor(loneN) == 1,
        Widths.stringWidth("🇳🇱🇯🇵") == 4,
        Widths.stringWidth("a🇳🇱b") == 4,
        Widths.truncate("🇳🇱🇯🇵", 3) == "🇳🇱"
      )
    },
    test("empty string is zero cells") {
      assertTrue(Widths.cellsFor("") == 0)
    }
  )

  private def wide(cp: Int): Boolean = Widths.isWideCodepoint(cp)

  private val wideCodepointSuite = suite("Widths.isWideCodepoint")(
    test("narrow Dingbats are narrow: check marks, ballot crosses, asterisks, arrows") {
      // ✓ ✔ ✗ ✳ ➜ — Neutral EAW; the old table took all of U+2700–27BF as wide.
      assertTrue(Seq(0x2713, 0x2714, 0x2717, 0x2733, 0x279C).forall(!wide(_)))
    },
    test("wide Dingbats stay wide") {
      // ✅ ✨ ❌ ➕ ➿
      assertTrue(Seq(0x2705, 0x2728, 0x274C, 0x2795, 0x27BF).forall(wide))
    },
    test("text-presentation symbols in the emoji planes are narrow") {
      // 🌡 🖥 🛠 and the first sextant — the old table took U+1F300–1FBFF as wide.
      assertTrue(Seq(0x1F321, 0x1F5A5, 0x1F6E0, 0x1FB00).forall(!wide(_)))
    },
    test("wide emoji outside the old emoji ranges are wide") {
      // ⌚ ⏰ ☕ ⚡ ⬛ ⭐ 🀄 🆗 🈁 🟰
      assertTrue(Seq(0x231A, 0x23F0, 0x2615, 0x26A1, 0x2B1B, 0x2B50, 0x1F004, 0x1F197, 0x1F201, 0x1F7F0).forall(wide))
    },
    test("wide scripts the old table missed are wide") {
      // Yijing hexagram, Tangut, Nushu, CJK Extension G.
      assertTrue(Seq(0x4DC0, 0x17000, 0x1B170, 0x30000).forall(wide))
    },
    test("unassigned code points in wide CJK blocks are wide") {
      // U+2A6E0 is reserved in CJK Extension B's block; EAW lists it W.
      assertTrue(wide(0x2A6E0))
    },
    test("ambiguous-width characters are narrow") {
      // ▀ █ ① — block elements and circled digits, EAW A.
      assertTrue(Seq(0x2580, 0x2588, 0x2460).forall(!wide(_)))
    },
    test("range edges: Hangul Jamo starts at U+1100 and ends at U+115F") {
      assertTrue(!wide(0x10FF), wide(0x1100), wide(0x115F), !wide(0x1160))
    },
    test("the lowest and highest code points are narrow") {
      assertTrue(!wide(0), !wide(0x10FFFF))
    }
  )

  private val tableSuite = suite("WideRanges")(
    test("holds sorted, merged, inclusive pairs") {
      val pairs = WideRanges.bounds.grouped(2).map(p => (p(0), p(1))).toVector
      assertTrue(
        WideRanges.bounds.length % 2 == 0,
        pairs.forall((start, end) => start <= end),
        // Merged: a gap of at least one narrow code point between ranges.
        pairs.zip(pairs.drop(1)).forall((a, b) => b._1 > a._2 + 1)
      )
    },
    test("was generated from Unicode 16.0.0") {
      assertTrue(WideRanges.UnicodeVersion == "16.0.0")
    }
  )
