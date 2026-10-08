package io.github.wickedsik.wsconsole
package buffer

import zio.test.*

/**
 * Behavioural pins for the grapheme-cluster iteration that backs
 * `Canvas.putText` and `Line.text`. Regression driver: `Char`-by-`Char`
 * iteration split supplementary codepoints into surrogate halves, and
 * split base + variation-selector sequences into two separate cells —
 * terminals then displayed the fragments as `??` or as unstyled bases.
 */
object GraphemesSpec extends ZIOSpecDefault:

  def spec = suite("Graphemes.foreach")(
    test("ASCII: one grapheme per character") {
      assertTrue(Graphemes.toSeq("hi!") == Seq("h", "i", "!"))
    },
    test("BMP characters: one grapheme per Char") {
      // ⚠ is U+26A0 (BMP), a single Char.
      assertTrue(Graphemes.toSeq("⚠ ok") == Seq("⚠", " ", "o", "k"))
    },
    test("supplementary codepoint: surrogate pair collapses to ONE grapheme") {
      // 🧹 is U+1F9F9, stored as two Chars in Java's UTF-16.
      val g = Graphemes.toSeq("🧹")
      assertTrue(g.length == 1, g.head == "🧹", g.head.length == 2)
    },
    test("emoji with variation selector: base + VS collapses to ONE grapheme") {
      // ⚠ + U+FE0F is the emoji-presentation variant of the warning sign.
      val warning = "⚠️"
      val g = Graphemes.toSeq(warning)
      assertTrue(g.length == 1, g.head == warning)
    },
    test("mixed content: emojis and ASCII interleave cleanly") {
      val g = Graphemes.toSeq("🧹 done 👋")
      assertTrue(g == Seq("🧹", " ", "d", "o", "n", "e", " ", "👋"))
    },
    test("empty input yields empty sequence") {
      assertTrue(Graphemes.toSeq("") == Seq.empty)
    }
  )
