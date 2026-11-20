package io.github.wickedsik.wsconsole
package component

import zio.Scope
import zio.test.*

object BooleanMarksSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BooleanMarks")(
    test("apply picks the mark for the state") {
      val marks = BooleanMarks("on", "off")
      assertTrue(marks(true) == "on", marks(false) == "off")
    },
    test("width is the wider of the two marks, in columns") {
      assertTrue(
        BooleanMarks("✔", "[ ]").width == 3,
        BooleanMarks("[x]", "-").width == 3,
        BooleanMarks("●", "○").width == 1
      )
    },
    test("width counts a wide grapheme as two columns") {
      assertTrue(BooleanMarks("済", "未").width == 2)
    }
  )
