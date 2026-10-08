package io.github.wickedsik.wsconsole
package component

import zio.Scope
import zio.test.*

object ProgressBarStyleSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ProgressBarStyle")(
    suite("Fill")(
      test("exposes exactly eight sub-cell eighths") {
        assertTrue(ProgressBarStyle.Fill.eighths.length == 8)
      },
      test("eighths run from one-eighth (▏) up to a full block (█)") {
        assertTrue(
          ProgressBarStyle.Fill.eighths.head == '▏',
          ProgressBarStyle.Fill.eighths.last == '█'
        )
      },
      test("eighths glyphs match the styleguide's documented sequence") {
        assertTrue(
          ProgressBarStyle.Fill.eighths == "▏▎▍▌▋▊▉█".toVector
        )
      },
      test("full is the full block glyph and empty is a space") {
        assertTrue(
          ProgressBarStyle.Fill.full == '█',
          ProgressBarStyle.Fill.empty == ' '
        )
      }
    ),
    suite("Shade")(
      test("exposes exactly four shade levels") {
        assertTrue(ProgressBarStyle.Shade.shades.length == 4)
      },
      test("shades run from lightest (░) up to a full block (█)") {
        assertTrue(
          ProgressBarStyle.Shade.shades.head == '░',
          ProgressBarStyle.Shade.shades.last == '█'
        )
      },
      test("shade glyphs match the styleguide's documented sequence") {
        assertTrue(
          ProgressBarStyle.Shade.shades == "░▒▓█".toVector
        )
      }
    ),
    suite("Segmented")(
      test("exposes filled and empty pip glyphs") {
        assertTrue(
          ProgressBarStyle.Segmented.filled == '■',
          ProgressBarStyle.Segmented.empty == '□'
        )
      }
    )
  )
