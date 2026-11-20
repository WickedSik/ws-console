package io.github.wickedsik.wsconsole
package demo.panels

import buffer.{Canvas, ScreenBuffer}
import geometry.Rect
import testkit.GridAssertions.assertChar

import zio.Scope
import zio.test.*

/** [[EventInspectorPanel]] through its pure `renderLog` seam. */
object EventInspectorPanelSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("EventInspectorPanel.renderLog")(
    test("shows '(awaiting input...)' when the log is empty") {
      val buf = ScreenBuffer.of(80, 24)
      EventInspectorPanel.renderLog(Canvas(buf), Rect(0, 0, 80, 24), Vector.empty)
      // "(awaiting input...)" starts at (4, 8)
      assertChar(buf, 4, 8, '(') &&
      assertChar(buf, 5, 8, 'a')
    },
    test("renders log entries starting at row 8") {
      val buf = ScreenBuffer.of(80, 24)
      val log = Vector("first line", "second line")
      EventInspectorPanel.renderLog(Canvas(buf), Rect(0, 0, 80, 24), log)
      assertChar(buf, 4, 8, 'f') &&
      assertChar(buf, 4, 9, 's')
    }
  )
