package io.github.wickedsik.wsconsole
package buffer

import ansi.{BgColor, Color, Csi, FgColor, Style as AnsiStyle}
import zio.Scope
import zio.test.*

object CellStyleSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("CellStyle")(

    suite("Empty")(
      test("Empty produces no ANSI output") {
        assertTrue(CellStyle.Empty.toAnsi.isEmpty)
      },

      test("Empty is the default-constructed style") {
        assertTrue(CellStyle() == CellStyle.Empty)
      }
    ),

    suite("Foreground")(
      test("Inherit produces no output") {
        assertTrue(Foreground.Inherit.toAnsi.isEmpty)
      },

      test("Named foreground delegates to FgColor.toAnsi") {
        assertTrue(
          Foreground.Named(FgColor.Red).toAnsi == FgColor.Red.toAnsi,
          Foreground.Named(FgColor.BrightCyan).toAnsi == FgColor.BrightCyan.toAnsi
        )
      },

      test("Indexed foreground matches Color.Templates.Fg256") {
        assertTrue(Foreground.Indexed(196).toAnsi == Color.Templates.Fg256(196))
      },

      test("Rgb foreground matches Color.Templates.FgRgb") {
        assertTrue(Foreground.Rgb(255, 128, 0).toAnsi == Color.Templates.FgRgb(255, 128, 0))
      },

      test("Indexed rejects out-of-range values") {
        val low  = scala.util.Try(Foreground.Indexed(-1))
        val high = scala.util.Try(Foreground.Indexed(256))
        assertTrue(low.isFailure, high.isFailure)
      }
    ),

    suite("Background")(
      test("Named background delegates to BgColor.toAnsi") {
        assertTrue(Background.Named(BgColor.Blue).toAnsi == BgColor.Blue.toAnsi)
      },

      test("Indexed background matches Color.Templates.Bg256") {
        assertTrue(Background.Indexed(21).toAnsi == Color.Templates.Bg256(21))
      },

      test("Rgb background matches Color.Templates.BgRgb") {
        assertTrue(Background.Rgb(10, 20, 30).toAnsi == Color.Templates.BgRgb(10, 20, 30))
      }
    ),

    suite("Attribute")(
      test("each attribute carries a non-empty ANSI code") {
        assertTrue(Attribute.values.forall(_.ansiCode.nonEmpty))
      },

      test("Bold and Underline reuse ansi.Style constants") {
        assertTrue(
          Attribute.Bold.ansiCode == AnsiStyle.Bold,
          Attribute.Underline.ansiCode == AnsiStyle.Underline
        )
      }
    ),

    suite("sgr composition")(
      test("attributes precede colors in parameter order") {
        val style = CellStyle(
          fg         = Foreground.Named(FgColor.Yellow),
          attributes = Set(Attribute.Bold)
        )
        assertTrue(style.sgr.params == Vector(1, FgColor.Yellow.code))
      },

      test("foreground and background combine in fg-then-bg order") {
        val style = CellStyle(
          fg = Foreground.Named(FgColor.White),
          bg = Background.Named(BgColor.Blue)
        )
        assertTrue(style.sgr.params == Vector(FgColor.White.code, BgColor.Blue.code))
      },

      test("the whole style renders as ONE escape, not one per component") {
        val style = CellStyle(
          fg         = Foreground.Rgb(255, 128, 0),
          bg         = Background.Named(BgColor.Blue),
          attributes = Set(Attribute.Bold, Attribute.Italic)
        )
        val escapes = style.toAnsi.count(_ == Csi.EscChar)
        assertTrue(
          escapes == 1,
          style.toAnsi == s"${Csi.ESC}[1;3;38;2;255;128;0;${BgColor.Blue.code}m"
        )
      },

      test("attributes emit in enum declaration order regardless of Set ordering") {
        val forward = CellStyle(attributes =
          Set(Attribute.Bold, Attribute.Italic, Attribute.Underline, Attribute.Strikethrough)
        )
        val reverse = CellStyle(attributes =
          Set(Attribute.Strikethrough, Attribute.Underline, Attribute.Italic, Attribute.Bold)
        )
        // KI-001: equal styles must produce identical bytes, not merely equal values.
        assertTrue(
          forward.sgr.params == Vector(1, 3, 4, 9),
          forward.toAnsi == reverse.toAnsi
        )
      },

      test("an empty style renders to nothing, never to a bare ESC[m") {
        assertTrue(CellStyle.Empty.sgr.isEmpty, CellStyle.Empty.toAnsi.isEmpty)
      },

      test("equal styles compare equal regardless of construction order") {
        val a = CellStyle(
          fg         = Foreground.Named(FgColor.Red),
          attributes = Set(Attribute.Bold, Attribute.Underline)
        )
        val b = CellStyle(
          attributes = Set(Attribute.Underline, Attribute.Bold),
          fg         = Foreground.Named(FgColor.Red)
        )
        assertTrue(a == b, a.hashCode == b.hashCode)
      }
    )
  )
