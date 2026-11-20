package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Cell, CellStyle, Foreground}
import geometry.Rect
import layout.Constraint
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

object ContainerSpec extends ZIOSpecDefault:

  private val redStyle = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val blueStyle = CellStyle(fg = Foreground.Named(FgColor.Blue))
  private val ctx = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Container")(
    suite("HBox")(
      test("explicit pairs partition the area horizontally") {
        val buf = renderToBuffer(20, 3)(
          HBox(
            Constraint.Fixed(5) -> Text("aaaaa", redStyle),
            Constraint.Fill     -> Text("bbbbb", blueStyle)
          ),
          Rect(0, 0, 20, 1),
          ctx
        )
        // First child at x=0..4, second child at x=5..19; second renders 'bbbbb' starting at x=5
        assertTrue(
          buf.get(0, 0).contains(Cell('a', redStyle)),
          buf.get(4, 0).contains(Cell('a', redStyle)),
          buf.get(5, 0).contains(Cell('b', blueStyle)),
          buf.get(9, 0).contains(Cell('b', blueStyle))
        )
      },
      test("bare children get equal Fill share") {
        val buf = renderToBuffer(20, 3)(
          HBox.evenly(
            Text("AAAAA", redStyle),
            Text("BBBBB", blueStyle)
          ),
          Rect(0, 0, 10, 1),
          ctx
        )
        // Each child gets 5 cells
        assertTrue(
          buf.get(0, 0).contains(Cell('A', redStyle)),
          buf.get(4, 0).contains(Cell('A', redStyle)),
          buf.get(5, 0).contains(Cell('B', blueStyle)),
          buf.get(9, 0).contains(Cell('B', blueStyle))
        )
      },
      test("empty HBox is a no-op") {
        val buf = renderToBuffer(20, 3)(
          HBox.empty,
          Rect(0, 0, 20, 3),
          ctx
        )
        assertTrue(buf.get(0, 0).contains(Cell.Empty))
      },
      test("HBox.items reflects the constructed list (case class equality)") {
        val a = HBox(Constraint.Fill -> Text("X"), Constraint.Fill -> Text("Y"))
        val b = HBox(Constraint.Fill -> Text("X"), Constraint.Fill -> Text("Y"))
        assertTrue(a == b, a.items.size == 2)
      }
    ),
    suite("VBox")(
      test("explicit pairs partition the area vertically") {
        val buf = renderToBuffer(10, 5)(
          VBox(
            Constraint.Fixed(1) -> Text("AA", redStyle),
            Constraint.Fixed(1) -> Text("BB", blueStyle)
          ),
          Rect(0, 0, 10, 5),
          ctx
        )
        assertTrue(
          buf.get(0, 0).contains(Cell('A', redStyle)),
          buf.get(1, 0).contains(Cell('A', redStyle)),
          buf.get(0, 1).contains(Cell('B', blueStyle)),
          buf.get(1, 1).contains(Cell('B', blueStyle))
        )
      },
      test("bare children get equal Fill share vertically") {
        val buf = renderToBuffer(10, 4)(
          VBox.evenly(
            Text("a", redStyle),
            Text("b", blueStyle)
          ),
          Rect(0, 0, 10, 4),
          ctx
        )
        // Each child gets 2 rows; Text renders on its first row
        assertTrue(
          buf.get(0, 0).contains(Cell('a', redStyle)),
          buf.get(0, 2).contains(Cell('b', blueStyle))
        )
      }
    )
  )
