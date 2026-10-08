package io.github.wickedsik.wsconsole
package buffer

import zio.Scope
import zio.test.*

object TextEditSpec extends ZIOSpecDefault:

  private val zwj = "‍"
  private val family = "👨" + zwj + "👩" + zwj + "👧"
  private val thumbsUp = "👍🏽"
  private val eAcute = "é"

  private def at(value: String, caret: Int): TextEdit = TextEdit(value, caret)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TextEdit")(
    suite("insert")(
      test("at the start, middle and end") {
        assertTrue(
          at("bc", 0).insert("a") == at("abc", 1),
          at("ac", 1).insert("b") == at("abc", 2),
          at("ab", 2).insert("c") == at("abc", 3)
        )
      },
      test("a newline is ordinary text") {
        assertTrue(at("ab", 1).insert("\n") == at("a\nb", 2))
      },
      test("an empty insert changes nothing") {
        assertTrue(at("ab", 1).insert("") == at("ab", 1))
      },
      test("a combining mark joins the cluster before it, caret after the cluster") {
        assertTrue(at("cafe", 4).insert("́") == at("café", 5))
      },
      test("Unicode text inserts whole") {
        assertTrue(
          at("", 0).insert("你好") == at("你好", 2),
          at("при", 3).insert("вет") == at("привет", 6)
        )
      }
    ),
    suite("backspace")(
      test("at the start, middle and end") {
        assertTrue(
          at("abc", 0).backspace.isEmpty,
          at("abc", 2).backspace.contains(at("ac", 1)),
          at("abc", 3).backspace.contains(at("ab", 2))
        )
      },
      test("at a line start joins the two lines") {
        assertTrue(at("ab\ncd", 3).backspace.contains(at("abcd", 2)))
      },
      test("removes a whole ZWJ family, skin-tone emoji, and accented letter") {
        assertTrue(
          at("a" + family, 1 + family.length).backspace.contains(at("a", 1)),
          at(thumbsUp + "x", thumbsUp.length).backspace.contains(at("x", 0)),
          at("caf" + eAcute, 5).backspace.contains(at("caf", 3))
        )
      },
      test("removes one CJK ideograph") {
        assertTrue(at("你好", 2).backspace.contains(at("你", 1)))
      },
      test("treats \\r\\n as one cluster") {
        assertTrue(at("a\r\nb", 3).backspace.contains(at("ab", 1)))
      }
    ),
    suite("delete")(
      test("at the start, middle and end") {
        assertTrue(
          at("abc", 0).delete.contains(at("bc", 0)),
          at("abc", 1).delete.contains(at("ac", 1)),
          at("abc", 3).delete.isEmpty
        )
      },
      test("at a line end joins the two lines") {
        assertTrue(at("ab\ncd", 2).delete.contains(at("abcd", 2)))
      },
      test("removes a whole ZWJ family") {
        assertTrue(at(family + "b", 0).delete.contains(at("b", 0)))
      }
    ),
    suite("left / right")(
      test("step one cluster and stop at the ends") {
        assertTrue(
          at("abc", 1).left.contains(at("abc", 0)),
          at("abc", 1).right.contains(at("abc", 2)),
          at("abc", 0).left.isEmpty,
          at("abc", 3).right.isEmpty
        )
      },
      test("cross a newline") {
        assertTrue(
          at("a\nb", 1).right.contains(at("a\nb", 2)),
          at("a\nb", 2).left.contains(at("a\nb", 1))
        )
      },
      test("step over a whole emoji sequence") {
        val v = "a" + family + "b"
        assertTrue(
          at(v, 1).right.contains(at(v, 1 + family.length)),
          at(v, 1 + family.length).left.contains(at(v, 1))
        )
      },
      test("step CJK one ideograph at a time") {
        assertTrue(
          at("你好吗", 0).right.contains(at("你好吗", 1)),
          at("你好吗", 3).left.contains(at("你好吗", 2))
        )
      }
    ),
    suite("word motion")(
      test("matches the TextInput rule on ASCII") {
        val v = "foo bar_baz  qux"
        assertTrue(
          at(v, 0).wordRight.contains(at(v, 3)),
          at(v, 3).wordRight.contains(at(v, 11)),
          at(v, 16).wordLeft.contains(at(v, 13)),
          at(v, 13).wordLeft.contains(at(v, 4))
        )
      },
      test("crosses a newline") {
        val v = "one\ntwo"
        assertTrue(
          at(v, 3).wordRight.contains(at(v, 7)),
          at(v, 4).wordLeft.contains(at(v, 0))
        )
      },
      test("an accented letter is part of its word") {
        val v = "caf" + eAcute + " ok"
        assertTrue(
          at(v, 0).wordRight.contains(at(v, 5)),
          at(v, 5).wordLeft.contains(at(v, 0))
        )
      },
      test("Cyrillic and CJK are word characters") {
        assertTrue(
          at("привет мир", 0).wordRight.contains(at("привет мир", 6)),
          at("你好 世界", 5).wordLeft.contains(at("你好 世界", 3))
        )
      },
      test("returns None at the ends") {
        assertTrue(at("ab", 0).wordLeft.isEmpty, at("ab", 2).wordRight.isEmpty)
      }
    ),
    suite("moveTo")(
      test("clamps to the value") {
        assertTrue(
          at("abc", 1).moveTo(-5).contains(at("abc", 0)),
          at("abc", 1).moveTo(99).contains(at("abc", 3))
        )
      },
      test("snaps back to the start of a cluster it lands inside") {
        val v = "a" + family
        assertTrue(at(v, 0).moveTo(3).contains(at(v, 1)))
      },
      test("returns None when the caret would not move") {
        assertTrue(at("abc", 2).moveTo(2).isEmpty)
      }
    ),
    suite("degenerate input")(
      test("empty value") {
        val e = TextEdit.atEnd("")
        assertTrue(
          e.caret == 0,
          e.backspace.isEmpty,
          e.delete.isEmpty,
          e.left.isEmpty,
          e.right.isEmpty,
          e.wordLeft.isEmpty,
          e.wordRight.isEmpty
        )
      },
      test("whitespace-only value moves but finds no word") {
        assertTrue(
          at("   ", 0).wordRight.contains(at("   ", 3)),
          at("   ", 3).wordLeft.contains(at("   ", 0))
        )
      },
      test("atEnd seeds the caret after the value") {
        assertTrue(TextEdit.atEnd("héllo").caret == 5)
      }
    )
  )
