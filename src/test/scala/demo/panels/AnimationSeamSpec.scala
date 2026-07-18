package io.github.wickedsik.wsconsole
package demo.panels

import buffer.{Canvas, ScreenBuffer}
import unicode.SequencedDrawing
import testkit.GridAssertions.assertChar

import zio.Scope
import zio.test.*

/**
 * Deterministic tests for the animated panels' pure frame seams. Each renders
 * a specific frame directly and asserts the exact glyph — no `ZIO.sleep`, no
 * `TestClock`, no real time. The `show`/`animate` loops that drive these seams
 * on the clock are exercised by running the demo; the glyph mapping itself is
 * proven here.
 */
object AnimationSeamSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("animation frame seams")(

    test("SpinnerPanel.renderFrame draws the braille glyph for a specific frame") {
      // Frame 37 → glyph index 37 % 10 = 7.
      val buf = ScreenBuffer.of(80, 10)
      SpinnerPanel.renderFrame(Canvas(buf), 37)
      assertChar(buf, SpinnerPanel.spinnerCol, SpinnerPanel.spinnerRow, SequencedDrawing.Spinner(7))
    },

    test("SpinnerPanel.renderFrame wraps via modulo at the sequence boundary") {
      // Frame == length → index 0 (proves the modulo lives inside the seam).
      val buf = ScreenBuffer.of(80, 10)
      SpinnerPanel.renderFrame(Canvas(buf), SequencedDrawing.Spinner.length)
      assertChar(buf, SpinnerPanel.spinnerCol, SpinnerPanel.spinnerRow, SequencedDrawing.Spinner(0))
    },

    test("ProgressBarPanel.drawBar fills the first cell at 100% and blanks it at 0%") {
      val full = ScreenBuffer.of(80, 12)
      ProgressBarPanel.drawBar(Canvas(full), 100)
      val empty = ScreenBuffer.of(80, 12)
      ProgressBarPanel.drawBar(Canvas(empty), 0)
      // 100% → the first bar cell is a full block; 0% → it is a space.
      assertChar(full,  ProgressBarPanel.barCol + 1, ProgressBarPanel.barRow, SequencedDrawing.ProgressBar(8)) &&
      assertChar(empty, ProgressBarPanel.barCol + 1, ProgressBarPanel.barRow, ' ')
    }
  )
