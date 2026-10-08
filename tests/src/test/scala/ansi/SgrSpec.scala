package io.github.wickedsik.wsconsole
package ansi

import zio.Scope
import zio.test.*

object SgrSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Sgr")(
    suite("rendering")(
      test("a single parameter renders as one escape") {
        assertTrue(Sgr.Bold.toAnsi == s"${Csi.ESC}[1m")
      },
      test("Empty renders to the empty string, NOT to a bare ESC[m") {
        // A parameterless SGR means "reset all"; emitting one for an empty
        // style would silently clear the terminal's rendition.
        assertTrue(Sgr.Empty.toAnsi.isEmpty, Sgr.Empty.isEmpty, !Sgr.Empty.nonEmpty)
      },
      test("parameters are semicolon-separated inside a single escape") {
        val merged = Sgr.Bold ++ Sgr.Italic ++ Sgr.fgRgb(255, 128, 0)
        assertTrue(
          merged.toAnsi == s"${Csi.ESC}[1;3;38;2;255;128;0m",
          merged.toAnsi.count(_ == Csi.EscChar) == 1
        )
      }
    ),
    suite("composition")(
      test("++ concatenates parameters in order") {
        assertTrue((Sgr.Bold ++ Sgr.Underline).params == Vector(1, 4))
      },
      test("++ is associative") {
        val left = (Sgr.Bold ++ Sgr.Dim) ++ Sgr.Italic
        val right = Sgr.Bold ++ (Sgr.Dim ++ Sgr.Italic)
        assertTrue(left == right)
      },
      test("Empty is the identity of ++") {
        assertTrue(
          (Sgr.Empty ++ Sgr.Bold) == Sgr.Bold,
          (Sgr.Bold ++ Sgr.Empty) == Sgr.Bold
        )
      },
      test("merging saves two bytes per group beyond the first") {
        val separate = Sgr.Bold.toAnsi + Sgr.Italic.toAnsi + Sgr.Underline.toAnsi
        val merged = (Sgr.Bold ++ Sgr.Italic ++ Sgr.Underline).toAnsi
        assertTrue(merged.length == separate.length - 4)
      },
      test("Reset composes as a leading parameter rather than its own escape") {
        assertTrue((Sgr.Reset ++ Sgr.Bold).toAnsi == s"${Csi.ESC}[0;1m")
      }
    ),
    suite("colours")(
      test("256-colour foreground and background use the 5-selector form") {
        assertTrue(
          Sgr.fg256(196).params == Vector(38, 5, 196),
          Sgr.bg256(21).params == Vector(48, 5, 21)
        )
      },
      test("truecolour uses the 2-selector form") {
        assertTrue(
          Sgr.fgRgb(255, 128, 0).params == Vector(38, 2, 255, 128, 0),
          Sgr.bgRgb(10, 20, 30).params == Vector(48, 2, 10, 20, 30)
        )
      },
      test("named colours expose their code as a single parameter") {
        assertTrue(
          FgColor.Red.sgr.params == Vector(31),
          BgColor.BrightWhite.sgr.params == Vector(107)
        )
      },
      test("out-of-range colour components are rejected") {
        assertTrue(
          scala.util.Try(Sgr.fg256(256)).isFailure,
          scala.util.Try(Sgr.bg256(-1)).isFailure,
          scala.util.Try(Sgr.fgRgb(256, 0, 0)).isFailure,
          scala.util.Try(Sgr.bgRgb(0, 0, -1)).isFailure
        )
      }
    ),
    suite("string facades stay in agreement")(
      test("Style constants match their Sgr counterparts") {
        assertTrue(
          Style.Bold == Sgr.Bold.toAnsi,
          Style.Reset == Sgr.Reset.toAnsi,
          Style.Strikethrough == Sgr.Strikethrough.toAnsi
        )
      },
      test("Color.Templates match their Sgr counterparts") {
        assertTrue(
          Color.Templates.Fg256(196) == Sgr.fg256(196).toAnsi,
          Color.Templates.BgRgb(1, 2, 3) == Sgr.bgRgb(1, 2, 3).toAnsi
        )
      },
      test("StyleDisable constants match their Sgr counterparts") {
        assertTrue(
          StyleDisable.BoldDim == Sgr.NoBoldDim.toAnsi,
          StyleDisable.Blink == Sgr.NoBlink.toAnsi,
          StyleDisable.Strikethrough == Sgr.NoStrikethrough.toAnsi
        )
      }
    ),
    suite("AnsiBuilder integration")(
      test("sgr appends one escape for a composed style") {
        val built = AnsiBuilder().sgr(Sgr.Bold ++ FgColor.Red.sgr).text("x").build
        assertTrue(built == s"${Csi.ESC}[1;31mx")
      },
      test("sgr on an empty Sgr appends nothing") {
        assertTrue(AnsiBuilder().sgr(Sgr.Empty).text("x").build == "x")
      },
      test("chaining bold then fg still emits two escapes — sgr is the merge path") {
        val chained = AnsiBuilder().bold.fg(FgColor.Red).build
        val merged = AnsiBuilder().sgr(Sgr.Bold ++ FgColor.Red.sgr).build
        assertTrue(
          chained.count(_ == Csi.EscChar) == 2,
          merged.count(_ == Csi.EscChar) == 1
        )
      }
    )
  )
