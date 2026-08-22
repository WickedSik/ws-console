package io.github.wickedsik.wsconsole
package buffer

import zio.test.*

/**
 * Pins the width classifier behind the continuation-cell mechanism. The
 * ranges are intentionally coarse (see [[Widths.isWideCodepoint]]); this
 * spec covers a representative from each block plus the common narrow /
 * ambiguous cases users hit in practice.
 */
object WidthsSpec extends ZIOSpecDefault:

  def spec = suite("Widths.cellsFor")(
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
    test("empty string is zero cells") {
      assertTrue(Widths.cellsFor("") == 0)
    }
  )
