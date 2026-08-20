package io.github.wickedsik.wsconsole
package component

import zio.{Duration, Scope}
import zio.durationInt
import zio.test.*

object SpinnerStyleSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("SpinnerStyle")(

    suite("Braille")(

      test("has ten frames") {
        assertTrue(SpinnerStyle.Braille.frames.length == 10)
      },

      test("frames match the styleguide's documented braille cycle") {
        assertTrue(
          SpinnerStyle.Braille.frames ==
            Vector("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
        )
      },

      test("uses the default frame interval") {
        assertTrue(SpinnerStyle.Braille.frameInterval == SpinnerStyle.DefaultInterval)
      }
    ),

    suite("Line")(

      test("has four frames") {
        assertTrue(SpinnerStyle.Line.frames.length == 4)
      },

      test("frames match the classic terminal rotating-line cycle") {
        assertTrue(SpinnerStyle.Line.frames == Vector("-", "\\", "|", "/"))
      },

      test("runs at 200ms per frame — slower than Braille so a 4-frame cycle is legible") {
        assertTrue(SpinnerStyle.Line.frameInterval == 200.millis)
      }
    ),

    suite("Circle")(

      test("has six frames") {
        assertTrue(SpinnerStyle.Circle.frames.length == 6)
      },

      test("frames match the styleguide's documented ring cycle") {
        assertTrue(SpinnerStyle.Circle.frames == Vector("◜", "◠", "◝", "◞", "◡", "◟"))
      },

      test("runs at 160ms per frame — softer than Braille without shimmering") {
        assertTrue(SpinnerStyle.Circle.frameInterval == 160.millis)
      }
    ),

    suite("custom cycles")(

      test("SpinnerStyle(frames) defaults to the standard interval") {
        val custom = SpinnerStyle(Vector(".", "o", "O"))
        assertTrue(
          custom.frames == Vector(".", "o", "O"),
          custom.frameInterval == SpinnerStyle.DefaultInterval
        )
      },

      test("SpinnerStyle(frames, interval) accepts a custom cadence") {
        val custom = SpinnerStyle(Vector("a", "b"), 250.millis)
        assertTrue(
          custom.frames == Vector("a", "b"),
          custom.frameInterval == 250.millis
        )
      }
    ),

    suite("default interval")(

      test("is a positive duration") {
        assertTrue(SpinnerStyle.DefaultInterval.toMillis > 0L)
      }
    )
  )
