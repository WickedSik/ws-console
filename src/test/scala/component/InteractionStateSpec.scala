package io.github.wickedsik.wsconsole
package component

import ansi.{BgColor, FgColor}
import buffer.{Attribute, Background, CellStyle, Foreground}
import zio.Scope
import zio.test.*

object InteractionStateSpec extends ZIOSpecDefault:

  /**
   * One CellStyle per semantic role. State
   * modulations are role-agnostic — each test iterates across every
   * fixture and asserts the same property, so the "table-driven across
   * nine roles" shape holds without emitting nine near-duplicate tests.
   */
  private val roleFixtures: Seq[(String, CellStyle)] = Seq(
    "default"  -> CellStyle.Empty,
    "muted"    -> CellStyle(attributes = Set(Attribute.Dim)),
    "emphasis" -> CellStyle(attributes = Set(Attribute.Bold)),
    "accent"   -> CellStyle(fg = Foreground.Named(FgColor.BrightCyan)),
    "error"    -> CellStyle(fg = Foreground.Named(FgColor.Red)),
    "success"  -> CellStyle(fg = Foreground.Named(FgColor.Green)),
    "warning"  -> CellStyle(fg = Foreground.Named(FgColor.Yellow)),
    "info"     -> CellStyle(fg = Foreground.Named(FgColor.Blue)),
    "code"     -> CellStyle(fg = Foreground.Named(FgColor.Cyan))
  )

  private def preservesHue(before: CellStyle, after: CellStyle): Boolean =
    after.fg == before.fg && after.bg == before.bg

  def spec: Spec[TestEnvironment & Scope, Any] = suite("InteractionState")(
    suite("focused")(
      test("adds Bold to every role's attribute set") {
        val ok = roleFixtures.forall { case (_, role) =>
          InteractionState.focused(role).attributes.contains(Attribute.Bold)
        }
        assertTrue(ok)
      },
      test("preserves the role's fg and bg (hue is role-owned)") {
        val ok = roleFixtures.forall { case (_, role) =>
          preservesHue(role, InteractionState.focused(role))
        }
        assertTrue(ok)
      },
      test("preserves attributes the base already carries") {
        val base = CellStyle(attributes = Set(Attribute.Italic, Attribute.Underline))
        val focused = InteractionState.focused(base)
        assertTrue(
          focused.attributes == Set(Attribute.Italic, Attribute.Underline, Attribute.Bold)
        )
      },
      test("is idempotent (Set semantics)") {
        val base = CellStyle.Empty
        val once = InteractionState.focused(base)
        val twice = InteractionState.focused(once)
        assertTrue(once == twice)
      }
    ),
    suite("disabled")(
      test("adds Dim to every role's attribute set") {
        val ok = roleFixtures.forall { case (_, role) =>
          InteractionState.disabled(role).attributes.contains(Attribute.Dim)
        }
        assertTrue(ok)
      },
      test("preserves the role's fg and bg") {
        val ok = roleFixtures.forall { case (_, role) =>
          preservesHue(role, InteractionState.disabled(role))
        }
        assertTrue(ok)
      },
      test("is idempotent") {
        val base = CellStyle(fg = Foreground.Named(FgColor.Red))
        assertTrue(InteractionState.disabled(base) == InteractionState.disabled(InteractionState.disabled(base)))
      }
    ),
    suite("active")(
      test("adds Reverse to every role's attribute set") {
        val ok = roleFixtures.forall { case (_, role) =>
          InteractionState.active(role).attributes.contains(Attribute.Reverse)
        }
        assertTrue(ok)
      },
      test("preserves the role's fg and bg") {
        val ok = roleFixtures.forall { case (_, role) =>
          preservesHue(role, InteractionState.active(role))
        }
        assertTrue(ok)
      },
      test("is idempotent") {
        val base = CellStyle(bg = Background.Named(BgColor.Blue))
        assertTrue(InteractionState.active(base) == InteractionState.active(InteractionState.active(base)))
      }
    ),
    suite("selected")(
      test("adds Reverse to every role's attribute set") {
        val ok = roleFixtures.forall { case (_, role) =>
          InteractionState.selected(role).attributes.contains(Attribute.Reverse)
        }
        assertTrue(ok)
      },
      test("preserves the role's fg and bg") {
        val ok = roleFixtures.forall { case (_, role) =>
          preservesHue(role, InteractionState.selected(role))
        }
        assertTrue(ok)
      },
      test("is idempotent") {
        val base = CellStyle.Empty
        assertTrue(InteractionState.selected(base) == InteractionState.selected(InteractionState.selected(base)))
      }
    ),
    suite("cross-cutting")(
      test("focused and disabled produce different attributes (Bold vs Dim)") {
        val base = CellStyle.Empty
        assertTrue(
          InteractionState.focused(base).attributes == Set(Attribute.Bold),
          InteractionState.disabled(base).attributes == Set(Attribute.Dim)
        )
      },
      test("active and selected currently share Reverse — visual parity is intentional") {
        // Documenting the design decision: temporal semantics distinguish
        // them (`active` is momentary, `selected` is persistent), not
        // visuals. A consumer that needs a bespoke difference reaches for
        // a future Theme override.
        val base = CellStyle.Empty
        assertTrue(
          InteractionState.active(base) == InteractionState.selected(base),
          InteractionState.active(base).attributes.contains(Attribute.Reverse)
        )
      }
    )
  )
