package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{CellStyle, Foreground}
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.durationInt
import zio.test.*

import java.time.Instant

object SpinnerSpec extends ZIOSpecDefault:

  private val spinStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private def ctxAt(millis: Long): RenderContext =
    RenderContext.empty.copy(timestamp = Instant.ofEpochMilli(millis))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Spinner")(
    // ===== Deterministic frame selection =====

    suite("Braille cycle")(
      test("timestamp at epoch selects the first frame") {
        val buf = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(0L)
        )
        // Braille frame 0 = ⠋
        assertTrue(buf.get(0, 0).map(_.char).contains('⠋'))
      },
      test("timestamp at two full intervals selects frame 2") {
        // interval = 80ms → 160ms elapsed = 2 ticks → idx 2 = ⠹
        val buf = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(160L)
        )
        assertTrue(buf.get(0, 0).map(_.char).contains('⠹'))
      },
      test("timestamp within one interval stays on the same frame") {
        // Both 40ms and 79ms are in the [0, 80ms) bucket → frame 0
        val bufEarly = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(40L)
        )
        val bufLate = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(79L)
        )
        assertTrue(
          bufEarly.get(0, 0).map(_.char).contains('⠋'),
          bufLate.get(0, 0).map(_.char).contains('⠋')
        )
      },
      test("timestamp past a full cycle wraps by modulo") {
        // 10 frames × 80ms = 800ms per cycle. 800ms → back to frame 0.
        val buf = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(800L)
        )
        assertTrue(buf.get(0, 0).map(_.char).contains('⠋'))
      },
      test("timestamp advances by one interval → frame advances by one") {
        val f0 = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(0L)
        )
        val f1 = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Braille, spinStyle),
          ctx = ctxAt(80L)
        )
        assertTrue(
          f0.get(0, 0).map(_.char).contains('⠋'),
          f1.get(0, 0).map(_.char).contains('⠙')
        )
      }
    ),

    // ===== Line and Circle cycles =====

    suite("named cycles")(
      test("Line cycle selects the correct ASCII frame at one interval") {
        // Line interval = 200ms → 200ms elapsed = 1 tick → frame 1 = '\\'
        val buf = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Line, spinStyle),
          ctx = ctxAt(200L)
        )
        assertTrue(buf.get(0, 0).map(_.char).contains('\\'))
      },
      test("Circle cycle wraps after 6 frames at its own interval") {
        // Circle interval = 160ms; 6 × 160 = 960ms → back to frame 0.
        val buf = renderToBuffer(4, 1)(
          Spinner(SpinnerStyle.Circle, spinStyle),
          ctx = ctxAt(960L)
        )
        assertTrue(buf.get(0, 0).map(_.char).contains('◜'))
      }
    ),

    // ===== Custom cycles =====

    suite("custom cycles")(
      test("custom frames select by the same timestamp formula") {
        val custom = SpinnerStyle(Vector("A", "B", "C"), 100.millis)
        val bufA = renderToBuffer(4, 1)(Spinner(custom, spinStyle), ctx = ctxAt(0L))
        val bufB = renderToBuffer(4, 1)(Spinner(custom, spinStyle), ctx = ctxAt(100L))
        val bufC = renderToBuffer(4, 1)(Spinner(custom, spinStyle), ctx = ctxAt(200L))
        val wrap = renderToBuffer(4, 1)(Spinner(custom, spinStyle), ctx = ctxAt(300L))
        assertTrue(
          bufA.get(0, 0).map(_.char).contains('A'),
          bufB.get(0, 0).map(_.char).contains('B'),
          bufC.get(0, 0).map(_.char).contains('C'),
          wrap.get(0, 0).map(_.char).contains('A')
        )
      }
    ),

    // ===== Style =====

    test("the rendered glyph carries the consumer's style fg") {
      val buf = renderToBuffer(4, 1)(
        Spinner(SpinnerStyle.Braille, spinStyle),
        ctx = ctxAt(0L)
      )
      assertTrue(buf.get(0, 0).map(_.style.fg).contains(spinStyle.fg))
    },

    // ===== Empty area =====

    test("empty area is a no-op") {
      val buf = renderToBuffer(4, 4)(
        Spinner(SpinnerStyle.Braille, spinStyle),
        area = geometry.Rect(0, 0, 0, 0),
        ctx = ctxAt(0L)
      )
      assertTrue(buf.get(0, 0).contains(buffer.Cell.Empty))
    }
  )
