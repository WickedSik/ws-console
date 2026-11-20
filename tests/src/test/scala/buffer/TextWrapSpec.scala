package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor

import zio.Scope
import zio.test.*

object TextWrapSpec extends ZIOSpecDefault:

  private val red = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val blue = CellStyle(fg = Foreground.Named(FgColor.Blue))

  private val zwj = "\u200D"
  private val family = "👨" + zwj + "👩" + zwj + "👧"

  private def wrap(text: String, width: Int): Vector[WrappedLine] = TextWrap.wrap(Spans(text), width)
  private def texts(text: String, width: Int): Vector[String] = wrap(text, width).map(_.line.text)

  /**
   * Rebuild the source from the lines' offsets: each line's text must equal
   * its source slice, and every gap between lines must be whitespace or line
   * breaks only (what a soft or hard break drops). Returns the rebuilt text.
   */
  private def roundTrip(source: StyledLine, width: Int): (Boolean, String) =
    val text = source.text
    val lines = TextWrap.wrap(source, width)
    var cursor = 0
    var ok = true
    val rebuilt = StringBuilder()
    lines.foreach { l =>
      val gap = text.substring(cursor, l.start)
      ok = ok && gap.forall(Character.isWhitespace) && l.line.text == text.substring(l.start, l.end)
      val _ = rebuilt ++= gap ++= l.line.text
      cursor = l.end
    }
    val tail = text.substring(cursor)
    ok = ok && tail.forall(Character.isWhitespace)
    (ok, rebuilt.append(tail).toString)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextWrap")(
    suite("words")(
      test("plain words wrap at the last space that fits") {
        assertTrue(texts("the quick brown fox", 10) == Vector("the quick", "brown fox"))
      },
      test("a line that fits exactly is not wrapped") {
        assertTrue(
          texts("ab cd", 5) == Vector("ab cd"),
          texts("abcd efgh", 4) == Vector("abcd", "efgh")
        )
      },
      test("a word longer than the width breaks at the last grapheme that fits") {
        assertTrue(texts("abcdefghij", 4) == Vector("abcd", "efgh", "ij"))
      },
      test("a long word moves to a fresh line before breaking") {
        assertTrue(texts("ab cdefghij", 4) == Vector("ab", "cdef", "ghij"))
      },
      test("width 1 puts one narrow grapheme per line") {
        assertTrue(texts("ab c", 1) == Vector("a", "b", "c"))
      }
    ),
    suite("degenerate input")(
      test("width 0 or less yields no lines") {
        assertTrue(
          wrap("hello", 0).isEmpty,
          wrap("hello", -3).isEmpty
        )
      },
      test("empty source yields no lines") {
        assertTrue(
          wrap("", 10).isEmpty,
          TextWrap.wrap(StyledLine.Empty, 10).isEmpty
        )
      },
      test("whitespace-only source is one line, kept as-is when it fits") {
        assertTrue(
          texts("   ", 10) == Vector("   "),
          texts("     ", 2) == Vector("")
        )
      }
    ),
    suite("whitespace")(
      test("leading whitespace is kept on a paragraph's first line") {
        assertTrue(texts("  indented text", 20) == Vector("  indented text"))
      },
      test("leading whitespace is dropped on a soft-wrapped continuation") {
        assertTrue(texts("aaa    bbb", 4) == Vector("aaa", "bbb"))
      },
      test("whitespace at a soft break is not drawn, but stays in the source gap") {
        val lines = wrap("aaa bbb", 3)
        assertTrue(
          lines.map(_.line.text) == Vector("aaa", "bbb"),
          lines.map(l => (l.start, l.end)) == Vector((0, 3), (4, 7))
        )
      },
      test("a tab is breakable whitespace drawn as one space") {
        val lines = wrap("a\tb", 10)
        assertTrue(
          lines.map(_.line.text) == Vector("a b"),
          lines.map(l => (l.start, l.end)) == Vector((0, 3)),
          texts("aa\tbb", 3) == Vector("aa", "bb")
        )
      }
    ),
    suite("hard breaks")(
      test("\\n starts a new line and marks the line it ends") {
        val lines = wrap("a\nb", 10)
        assertTrue(
          lines.map(_.line.text) == Vector("a", "b"),
          lines.map(_.hardBreak) == Vector(true, false)
        )
      },
      test("a lone \\n is two empty lines") {
        assertTrue(texts("\n", 10) == Vector("", ""))
      },
      test("a trailing \\n yields a trailing empty line") {
        assertTrue(texts("a\n", 10) == Vector("a", ""))
      },
      test("doubled \\n yields an empty line between") {
        val lines = wrap("a\n\nb", 10)
        assertTrue(
          lines.map(_.line.text) == Vector("a", "", "b"),
          lines.map(_.hardBreak) == Vector(true, true, false),
          lines.map(l => (l.start, l.end)) == Vector((0, 1), (2, 2), (3, 4))
        )
      },
      test("\\r\\n is one break, \\r alone is a break") {
        assertTrue(
          texts("a\r\nb", 10) == Vector("a", "b"),
          texts("a\r\n\r\nb", 10) == Vector("a", "", "b"),
          texts("a\rb", 10) == Vector("a", "b")
        )
      },
      test("a hard break after a soft wrap marks the soft-wrapped line") {
        val lines = wrap("aaaa      \nb", 5)
        assertTrue(
          lines.map(_.line.text) == Vector("aaaa", "b"),
          lines.map(_.hardBreak) == Vector(true, false)
        )
      }
    ),
    suite("spans")(
      test("a span boundary mid-word: the word stays whole and keeps both styles") {
        val lines = TextWrap.wrap(Spans(Span("hel", red), Span("lo world", blue)), 5)
        assertTrue(
          lines.map(_.line) == Vector(
            StyledLine(Vector(Span("hel", red), Span("lo", blue))),
            StyledLine(Vector(Span("world", blue)))
          )
        )
      },
      test("a span boundary at the break: each line keeps only its own span") {
        val lines = TextWrap.wrap(Spans(Span("hello ", red), Span("world", blue)), 7)
        assertTrue(
          lines.map(_.line) == Vector(
            StyledLine(Vector(Span("hello", red))),
            StyledLine(Vector(Span("world", blue)))
          )
        )
      },
      test("a span continues on the next line in its own style") {
        val lines = TextWrap.wrap(Spans(Span("one two three", red)), 7)
        assertTrue(lines.map(_.line) == Vector("one two", "three").map(t => StyledLine(Vector(Span(t, red)))))
      }
    ),
    suite("Unicode")(
      test("CJK wraps by columns, a wide grapheme at the right edge moves down") {
        assertTrue(
          texts("中文字符测试", 5) == Vector("中文", "字符", "测试"),
          texts("a中", 2) == Vector("a", "中")
        )
      },
      test("Cyrillic wraps on words") {
        assertTrue(texts("Привет мир", 6) == Vector("Привет", "мир"))
      },
      test("emoji and a ZWJ sequence are never split") {
        assertTrue(
          texts(s"$family$family", 3) == Vector(family, family),
          texts("👍🏽🇳🇱", 2) == Vector("👍🏽", "🇳🇱")
        )
      },
      test("a flag is two columns: it moves down rather than straddle the edge") {
        assertTrue(
          texts("ab🇳🇱", 3) == Vector("ab", "🇳🇱"),
          texts("flags 🇳🇱 stay", 8) == Vector("flags 🇳🇱", "stay"),
          wrap("🇳🇱🇯🇵", 3).map(_.line.width) == Vector(2, 2)
        )
      },
      test("a grapheme wider than the width sits alone on its own line") {
        assertTrue(
          texts("中", 1) == Vector("中"),
          texts("中文", 1) == Vector("中", "文"),
          texts("a中b", 1) == Vector("a", "中", "b")
        )
      }
    ),
    suite("round trip")(
      test("line slices plus dropped whitespace rebuild the source") {
        val source = Spans(
          "Field help text ",
          Span("runs up to seven lines", red),
          " in a narrow column.\n\nСекунда · 中文字符 ",
          Span(family, blue),
          " end\n"
        )
        val results = Seq(1, 3, 7, 12, 40).map(roundTrip(source, _))
        assertTrue(
          results.forall(_._1),
          results.forall(_._2 == source.text)
        )
      }
    ),
    suite("ellipsize")(
      test("cuts the line and ends it with … in the last visible span's style") {
        val line = Spans(Span("abc", red), Span("defgh", blue))
        assertTrue(
          TextWrap.ellipsize(line, 5) == StyledLine(Vector(Span("abc", red), Span("d", blue), Span("…", blue))),
          TextWrap.ellipsize(line, 3) == StyledLine(Vector(Span("ab", red), Span("…", red))),
          TextWrap.ellipsize(line, 1) == StyledLine(Vector(Span("…", red))),
          TextWrap.ellipsize(line, 0) == StyledLine.Empty
        )
      },
      test("never exceeds the width, even with a wide grapheme at the cut") {
        val cut = TextWrap.ellipsize(Spans("中文字"), 4)
        assertTrue(
          cut.text == "中…",
          cut.width == 3
        )
      }
    )
  )
