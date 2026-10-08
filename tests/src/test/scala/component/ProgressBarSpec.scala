package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground}
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

object ProgressBarSpec extends ZIOSpecDefault:

  private val fillStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightGreen))

  private val ctx = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ProgressBar")(
    // ===== Fill (default) — sub-cell precision =====

    suite("Fill")(
      test("progress = 0 renders all track cells (spaces styled with track style)") {
        val buf = renderToBuffer(10, 1)(ProgressBar(0.0, style = fillStyle), ctx = ctx)
        val allEmpty = (0 until 10).forall(x => buf.get(x, 0).map(_.char).contains(' '))
        val trackDim = buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
        assertTrue(allEmpty, trackDim)
      },
      test("progress = 1 renders every cell as a full block") {
        val buf = renderToBuffer(10, 1)(ProgressBar(1.0, style = fillStyle), ctx = ctx)
        val allFull = (0 until 10).forall(x => buf.get(x, 0).map(_.char).contains('█'))
        assertTrue(allFull)
      },
      test("progress = 0.5 renders half full, half empty") {
        val buf = renderToBuffer(10, 1)(ProgressBar(0.5, style = fillStyle), ctx = ctx)
        assertTrue(
          buf.get(0, 0).map(_.char).contains('█'),
          buf.get(4, 0).map(_.char).contains('█'),
          buf.get(5, 0).map(_.char).contains(' '),
          buf.get(9, 0).map(_.char).contains(' ')
        )
      },
      test("progress = 0.55 places a 4/8 partial-block glyph at the boundary") {
        // totalEighths = round(0.55 * 10 * 8) = 44 → 5 full + 4 eighths (▌) + 4 empty
        val buf = renderToBuffer(10, 1)(ProgressBar(0.55, style = fillStyle), ctx = ctx)
        assertTrue(
          buf.get(4, 0).map(_.char).contains('█'),
          buf.get(5, 0).map(_.char).contains('▌'),
          buf.get(6, 0).map(_.char).contains(' ')
        )
      },
      test("fill cells carry the consumer's role fg") {
        val buf = renderToBuffer(10, 1)(ProgressBar(0.5, style = fillStyle), ctx = ctx)
        assertTrue(buf.get(0, 0).map(_.style.fg).contains(fillStyle.fg))
      },
      test("track cells carry Dim on top of the role's fg (muted-of-hue)") {
        val buf = renderToBuffer(10, 1)(ProgressBar(0.5, style = fillStyle), ctx = ctx)
        assertTrue(
          buf.get(5, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
          buf.get(5, 0).map(_.style.fg).contains(fillStyle.fg)
        )
      }
    ),

    // ===== Shade =====

    suite("Shade")(
      test("progress = 1 renders every cell full") {
        val buf = renderToBuffer(8, 1)(
          ProgressBar(1.0, bar = ProgressBarStyle.Shade, style = fillStyle),
          ctx = ctx
        )
        val allFull = (0 until 8).forall(x => buf.get(x, 0).map(_.char).contains('█'))
        assertTrue(allFull)
      },
      test("progress = 0.5 renders half full, half empty") {
        val buf = renderToBuffer(8, 1)(
          ProgressBar(0.5, bar = ProgressBarStyle.Shade, style = fillStyle),
          ctx = ctx
        )
        assertTrue(
          buf.get(3, 0).map(_.char).contains('█'),
          buf.get(4, 0).map(_.char).contains(' ')
        )
      },
      test("fractional progress places a shade glyph at the boundary") {
        // 4 cells wide, progress 0.375 → totalSteps = round(0.375 * 4 * 4) = 6
        // fullCells = 6 / 4 = 1, partialSteps = 6 % 4 = 2 → shades(1) = '▒'
        val buf = renderToBuffer(4, 1)(
          ProgressBar(0.375, bar = ProgressBarStyle.Shade, style = fillStyle),
          ctx = ctx
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains('█'),
          buf.get(1, 0).map(_.char).contains('▒'),
          buf.get(2, 0).map(_.char).contains(' ')
        )
      }
    ),

    // ===== Segmented =====

    suite("Segmented")(
      test("progress = 1 renders every cell as a filled pip") {
        val buf = renderToBuffer(6, 1)(
          ProgressBar(1.0, bar = ProgressBarStyle.Segmented, style = fillStyle),
          ctx = ctx
        )
        val allFilled = (0 until 6).forall(x => buf.get(x, 0).map(_.char).contains('■'))
        assertTrue(allFilled)
      },
      test("progress = 0.5 fills half the pips") {
        val buf = renderToBuffer(6, 1)(
          ProgressBar(0.5, bar = ProgressBarStyle.Segmented, style = fillStyle),
          ctx = ctx
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains('■'),
          buf.get(2, 0).map(_.char).contains('■'),
          buf.get(3, 0).map(_.char).contains('□'),
          buf.get(5, 0).map(_.char).contains('□')
        )
      }
    ),

    // ===== Clamping =====

    suite("clamping")(
      test("progress > 1 is clamped to a full bar") {
        val buf = renderToBuffer(6, 1)(ProgressBar(2.5, style = fillStyle), ctx = ctx)
        val allFull = (0 until 6).forall(x => buf.get(x, 0).map(_.char).contains('█'))
        assertTrue(allFull)
      },
      test("progress < 0 is clamped to an empty bar") {
        val buf = renderToBuffer(6, 1)(ProgressBar(-0.5, style = fillStyle), ctx = ctx)
        val allEmpty = (0 until 6).forall(x => buf.get(x, 0).map(_.char).contains(' '))
        assertTrue(allEmpty)
      }
    ),

    // ===== Empty area =====

    test("empty area is a no-op") {
      val buf = renderToBuffer(4, 4)(
        ProgressBar(0.5, style = fillStyle),
        area = geometry.Rect(0, 0, 0, 0),
        ctx = ctx
      )
      assertTrue(buf.get(0, 0).contains(buffer.Cell.Empty))
    }
  )
