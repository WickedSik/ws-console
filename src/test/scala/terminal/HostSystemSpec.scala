package io.github.wickedsik.wsconsole
package terminal

import zio.Scope
import zio.test.*

object HostSystemSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("HostSystem")(
    colorSupportSuite,
    unicodeSuite
  )

  private val colorSupportSuite = suite("resolveColorSupport")(
    // === TrueColor via COLORTERM ===

    test("COLORTERM=truecolor yields TrueColor") {
      val result = HostSystem.resolveColorSupport("truecolor", "xterm")
      assertTrue(result == ColorSupport.TrueColor)
    },
    test("COLORTERM=24bit yields TrueColor") {
      val result = HostSystem.resolveColorSupport("24bit", "xterm")
      assertTrue(result == ColorSupport.TrueColor)
    },
    test("COLORTERM is case-insensitive") {
      assertTrue(
        HostSystem.resolveColorSupport("TrueColor", "dumb") == ColorSupport.TrueColor,
        HostSystem.resolveColorSupport("TRUECOLOR", "dumb") == ColorSupport.TrueColor,
        HostSystem.resolveColorSupport("24BIT", "") == ColorSupport.TrueColor
      )
    },
    test("COLORTERM takes precedence over TERM") {
      val result = HostSystem.resolveColorSupport("truecolor", "dumb")
      assertTrue(result == ColorSupport.TrueColor)
    },

    // === Extended256 via TERM ===

    test("TERM containing 256color yields Extended256") {
      assertTrue(
        HostSystem.resolveColorSupport("", "xterm-256color") == ColorSupport.Extended256,
        HostSystem.resolveColorSupport("", "screen-256color") == ColorSupport.Extended256,
        HostSystem.resolveColorSupport("", "tmux-256color") == ColorSupport.Extended256
      )
    },

    // === NoColor ===

    test("TERM=dumb yields NoColor") {
      val result = HostSystem.resolveColorSupport("", "dumb")
      assertTrue(result == ColorSupport.NoColor)
    },
    test("empty TERM with no COLORTERM yields NoColor") {
      val result = HostSystem.resolveColorSupport("", "")
      assertTrue(result == ColorSupport.NoColor)
    },

    // === Basic16 fallback ===

    test("unknown TERM yields Basic16") {
      assertTrue(
        HostSystem.resolveColorSupport("", "xterm") == ColorSupport.Basic16,
        HostSystem.resolveColorSupport("", "screen") == ColorSupport.Basic16,
        HostSystem.resolveColorSupport("", "linux") == ColorSupport.Basic16,
        HostSystem.resolveColorSupport("", "rxvt-unicode") == ColorSupport.Basic16
      )
    },
    test("unrecognized COLORTERM does not grant TrueColor") {
      val result = HostSystem.resolveColorSupport("yes", "xterm")
      assertTrue(result == ColorSupport.Basic16)
    }
  )

  private val unicodeSuite = suite("resolveUnicode")(
    test("LANG with UTF-8 yields true") {
      assertTrue(HostSystem.resolveUnicode("en_US.UTF-8", "", ""))
    },
    test("LC_ALL with UTF-8 yields true") {
      assertTrue(HostSystem.resolveUnicode("", "en_US.UTF-8", ""))
    },
    test("LC_CTYPE with UTF-8 yields true") {
      assertTrue(HostSystem.resolveUnicode("", "", "en_US.UTF-8"))
    },
    test("UTF8 without hyphen is recognized") {
      assertTrue(HostSystem.resolveUnicode("en_US.UTF8", "", ""))
    },
    test("case-insensitive matching") {
      assertTrue(
        HostSystem.resolveUnicode("en_US.utf-8", "", ""),
        HostSystem.resolveUnicode("", "en_US.Utf8", "")
      )
    },
    test("no locale vars set yields false") {
      assertTrue(!HostSystem.resolveUnicode("", "", ""))
    },
    test("locale without UTF-8 yields false") {
      assertTrue(!HostSystem.resolveUnicode("en_US.ISO-8859-1", "POSIX", "C"))
    },
    test("any single var is sufficient") {
      assertTrue(
        HostSystem.resolveUnicode("C.UTF-8", "POSIX", "C"),
        HostSystem.resolveUnicode("C", "C.UTF-8", "C"),
        HostSystem.resolveUnicode("C", "C", "C.UTF-8")
      )
    }
  )
