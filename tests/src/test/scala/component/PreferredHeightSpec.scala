package io.github.wickedsik.wsconsole
package component

import buffer.Cell
import geometry.{Insets, Rect, Sides}
import layout.Constraint
import testkit.RenderHarness

import zio.{Scope, ZIO}
import zio.test.*

object PreferredHeightSpec extends ZIOSpecDefault:

  /** A leaf whose preference is fixed, for exercising the containers. */
  final private case class Tall(rows: Int) extends Component:
    override def preferredHeight(width: Int): Option[Int] = Some(rows)
    def render(area: Rect, canvas: buffer.Canvas, ctx: RenderContext): Unit = ()

  /** A leaf that grows as it narrows — the height-for-width shape wrapped text will take. */
  final private case class Wraps(chars: Int) extends Component:
    override def preferredHeight(width: Int): Option[Int] =
      if width <= 0 then Some(0) else Some((chars + width - 1) / width)
    def render(area: Rect, canvas: buffer.Canvas, ctx: RenderContext): Unit = ()

  private def rectsOf(c: Component, area: Rect): Seq[Rect] = c.childLayouts(area).map(_._2)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("preferredHeight")(
    suite("leaves")(
      test("Text is one row at any width, including Unicode and empty content") {
        assertTrue(
          Text("hello").preferredHeight(80).contains(1),
          Text("中文").preferredHeight(1).contains(1),
          Text("Привет").preferredHeight(0).contains(1),
          Text("").preferredHeight(10).contains(1)
        )
      },
      test("Spacer and RawCanvas have no preference") {
        assertTrue(
          Spacer.preferredHeight(10).isEmpty,
          RawCanvas(_ => ()).preferredHeight(10).isEmpty
        )
      },
      test("interactive leaves report their natural height") {
        for
          input    <- TextInput.make()
          bare     <- TextInput.make(sides = Sides.none)
          padded   <- TextInput.make(padding = Insets.symmetric(horizontal = 1, vertical = 1))
          button   <- Button.make("OK", ZIO.unit)
          checkbox <- Checkbox.make("Agree")
          radios   <- RadioGroup.make(Seq("a", "b", "c", "d"))
        yield assertTrue(
          input.preferredHeight(20).contains(3),
          bare.preferredHeight(20).contains(1),
          padded.preferredHeight(20).contains(5),
          button.preferredHeight(20).contains(3),
          checkbox.preferredHeight(20).contains(1),
          radios.preferredHeight(20).contains(4),
          ProgressBar(0.5).preferredHeight(20).contains(1),
          Spinner().preferredHeight(20).contains(1)
        )
      }
    ),
    suite("Panel")(
      test("adds border rows and vertical padding to the child's preference") {
        val panel = Panel(child = Tall(4), padding = Insets(1, 0, 2, 0))
        assertTrue(panel.preferredHeight(20).contains(4 + 2 + 3))
      },
      test("suppressed sides cost nothing") {
        assertTrue(Panel(child = Tall(4), sides = Sides.none).preferredHeight(20).contains(4))
      },
      test("measures the child at the inner width") {
        // 20 chars at inner width 10 - 2 (border) - 2 (padding) = 6 → 4 rows, + 2 border rows.
        val panel = Panel(child = Wraps(20), padding = Insets.symmetric(horizontal = 1, vertical = 0))
        assertTrue(panel.preferredHeight(10).contains(6))
      },
      test("has no preference when its child has none") {
        assertTrue(Panel().preferredHeight(20).isEmpty)
      }
    ),
    suite("VBox")(
      test("sums Fixed sizes and children's preferences") {
        val box = VBox(
          Constraint.Fixed(2) -> Spacer,
          Constraint.Fit      -> Tall(3),
          Constraint.Fill     -> Text("x")
        )
        assertTrue(box.preferredHeight(10).contains(6))
      },
      test("Percentage counts nothing; Bounded clamps its inner contribution") {
        val box = VBox(
          Constraint.Percentage(50)                -> Tall(9),
          Constraint.atMost(2, Constraint.Fit)     -> Tall(5),
          Constraint.atLeast(4, Constraint.Fill)   -> Spacer
        )
        assertTrue(box.preferredHeight(10).contains(2 + 4))
      },
      test("a box of bare Fill spacers has no preference; an empty box has none either") {
        assertTrue(
          VBox(Spacer, Spacer).preferredHeight(10).isEmpty,
          VBox.empty.preferredHeight(10).isEmpty
        )
      },
      test("nested boxes compose") {
        val inner = VBox(Constraint.Fit -> Tall(2), Constraint.Fit -> Tall(3))
        val outer = VBox(Constraint.Fit -> inner, Constraint.Fixed(1) -> Spacer)
        assertTrue(outer.preferredHeight(10).contains(6))
      }
    ),
    suite("HBox")(
      test("reports the tallest child") {
        val box = HBox(Tall(2), Tall(5), Spacer)
        assertTrue(box.preferredHeight(30).contains(5))
      },
      test("measures each child at the width it will be assigned") {
        // Widths 4 and 16: Wraps(16) → 4 rows at 4 cols, 1 row at 16 cols.
        val box = HBox(Constraint.Fixed(4) -> Wraps(16), Constraint.Fill -> Wraps(16))
        assertTrue(box.preferredHeight(20).contains(4))
      },
      test("has no preference when no child has one") {
        assertTrue(HBox(Spacer, RawCanvas(_ => ())).preferredHeight(10).isEmpty)
      }
    ),
    suite("Fit in a VBox")(
      test("resolves to the child's preference alongside Fixed and Fill") {
        val box = VBox(
          Constraint.Fixed(2) -> Spacer,
          Constraint.Fit      -> Tall(3),
          Constraint.Fill     -> Spacer
        )
        assertTrue(
          rectsOf(box, Rect(0, 0, 10, 20)) == Seq(
            Rect(0, 0, 10, 2),
            Rect(0, 2, 10, 3),
            Rect(0, 5, 10, 15)
          )
        )
      },
      test("resolves height-for-width at the box's width") {
        val box = VBox(Constraint.Fit -> Wraps(30), Constraint.Fill -> Spacer)
        assertTrue(
          rectsOf(box, Rect(0, 0, 10, 20)).head.height == 3,
          rectsOf(box, Rect(0, 0, 5, 20)).head.height == 6
        )
      },
      test("Bounded(Fit) clamps the preference") {
        val box = VBox(Constraint.atMost(4, Constraint.Fit) -> Tall(9), Constraint.Fill -> Spacer)
        assertTrue(rectsOf(box, Rect(0, 0, 10, 20)).head.height == 4)
      },
      test("a child without a preference falls back to Fill") {
        val box = VBox(Constraint.Fixed(4) -> Spacer, Constraint.Fit -> Spacer)
        assertTrue(rectsOf(box, Rect(0, 0, 10, 10)).last.height == 6)
      },
      test("zero width resolves without error") {
        val box = VBox(Constraint.Fit -> Wraps(10), Constraint.Fit -> Text("x"))
        assertTrue(rectsOf(box, Rect(0, 0, 0, 10)).map(_.height) == Seq(0, 1))
      },
      test("Fit on the horizontal axis behaves as Fill") {
        val box = HBox(Constraint.Fixed(3) -> Tall(2), Constraint.Fit -> Tall(2))
        assertTrue(rectsOf(box, Rect(0, 0, 10, 1)).last.width == 7)
      }
    ),
    suite("Text measures columns, not chars")(
      test("CJK truncates on whole glyphs") {
        val buf = RenderHarness.renderToBuffer(5, 1)(Text("中文字"))
        assertTrue(
          buf.get(0, 0).map(_.text).contains("中"),
          buf.get(2, 0).map(_.text).contains("文"),
          buf.get(4, 0).contains(Cell.Empty)
        )
      },
      test("right alignment counts wide glyphs as two columns") {
        val buf = RenderHarness.renderToBuffer(6, 1)(Text("中文", align = Alignment.Right))
        assertTrue(
          buf.get(1, 0).contains(Cell.Empty),
          buf.get(2, 0).map(_.text).contains("中"),
          buf.get(4, 0).map(_.text).contains("文")
        )
      }
    )
  )
