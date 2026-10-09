package io.github.wickedsik.wsconsole
package component

import ansi.BgColor
import buffer.{Attribute, Background, CellStyle, ScreenBuffer}
import geometry.Rect
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

object LabelSpec extends ZIOSpecDefault:

  private val muted = CellStyle(attributes = Set(Attribute.Dim))

  /** Row `y` of `buf` as one char per cell, blank for an empty cell. */
  private def row(buf: ScreenBuffer, y: Int): String =
    (0 until buf.width).map(x => buf.get(x, y).map(_.char).getOrElse(' ')).mkString

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Label and RectPlaceholder")(
    suite("Label")(
      test("renders its text in its style, one row tall") {
        for l <- Label.make("Characters: 0/100", muted)
        yield
          val buf = renderToBuffer(20, 2)(l)
          assertTrue(
            row(buf, 0) == "Characters: 0/100   ",
            buf.get(0, 0).exists(_.style.attributes.contains(Attribute.Dim)),
            l.preferredHeight(20).contains(1)
          )
      },
      test("set replaces the text on the next render") {
        for l <- Label.make("Tags remaining: 6")
        yield
          l.set("Tags remaining: 5")
          assertTrue(l.text == "Tags remaining: 5", row(renderToBuffer(20, 1)(l), 0).startsWith("Tags remaining: 5"))
      },
      test("aligns right and truncates by cells without splitting a wide glyph") {
        for
          right <- Label.make("n/max", align = Alignment.Right)
          cjk   <- Label.make("中文中文")
        yield assertTrue(
          row(renderToBuffer(8, 1)(right), 0) == "   n/max",
          renderToBuffer(5, 1)(cjk).get(2, 0).map(_.text).contains("文"),
          renderToBuffer(5, 1)(cjk).get(4, 0).forall(_.text.isBlank)
        )
      },
      test("is not focusable") {
        for l <- Label.make("x")
        yield assertTrue(!l.focusable)
      }
    ),
    suite("RectPlaceholder")(
      test("fills every cell of its area with its style") {
        val blue = CellStyle(bg = Background.Named(BgColor.Blue))
        val buf = renderToBuffer(6, 4)(RectPlaceholder(blue), Rect(1, 1, 4, 2))
        val inside = for x <- 1 to 4; y <- 1 to 2 yield buf.get(x, y).map(_.style)
        assertTrue(
          inside.forall(_.contains(blue)),
          buf.get(0, 0).map(_.style).contains(CellStyle.Empty),
          buf.get(5, 3).map(_.style).contains(CellStyle.Empty)
        )
      },
      test("has no preferred height and is not focusable") {
        val r = RectPlaceholder(CellStyle.Empty)
        assertTrue(r.preferredHeight(10).isEmpty, !r.focusable)
      }
    )
  )
