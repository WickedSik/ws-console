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

  def spec = suite("Graphemes")(
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
    test("ZWJ sequence, skin-tone modifier and flag are ONE grapheme each (JDK 20+ UAX #29)") {
      val zwj = "‍"
      val family = "👨" + zwj + "👩" + zwj + "👧"
      val thumbs = "👍🏽"
      val flag = "🇳🇱"
      assertTrue(
        Graphemes.toSeq(family) == Seq(family),
        Graphemes.toSeq(thumbs) == Seq(thumbs),
        Graphemes.toSeq(flag) == Seq(flag),
        Graphemes.toSeq(s"a$family$flag") == Seq("a", family, flag)
      )
    },
    test("CRLF is ONE grapheme, a combining accent joins its base") {
      assertTrue(
        Graphemes.toSeq("a\r\nb") == Seq("a", "\r\n", "b"),
        Graphemes.toSeq("éx") == Seq("é", "x")
      )
    },
    test("empty input yields empty sequence") {
      assertTrue(Graphemes.toSeq("") == Seq.empty)
    },
    test("count: one per cluster, whatever its Char length") {
      val zwj = "\u200D"
      val family = "👨" + zwj + "👩" + zwj + "👧"
      assertTrue(
        Graphemes.count("") == 0,
        Graphemes.count("hello") == 5,
        Graphemes.count("🧹") == 1,
        Graphemes.count(family) == 1,
        Graphemes.count("👍🏽🇳🇱") == 2,
        Graphemes.count("e\u0301") == 1,
        Graphemes.count("a\nb") == 3,
        Graphemes.count("中文") == 2
      )
    },
    test("count agrees with toSeq") {
      val text = "a👍🏽 中\r\né🇳🇱"
      assertTrue(Graphemes.count(text) == Graphemes.toSeq(text).size)
    }
  )
