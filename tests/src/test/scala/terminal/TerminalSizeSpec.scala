package io.github.wickedsik.wsconsole
package terminal

import zio.Scope
import zio.test.*

object TerminalSizeSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TerminalSize")(
    test("valid dimensions are accepted") {
      val size = TerminalSize(24, 80)
      assertTrue(
        size.rows == 24,
        size.cols == 80
      )
    },
    test("area computes correctly") {
      assertTrue(
        TerminalSize(24, 80).area == 1920,
        TerminalSize(1, 1).area == 1,
        TerminalSize(50, 200).area == 10000
      )
    },
    test("zero rows rejected") {
      val result = scala.util.Try(TerminalSize(0, 80))
      assertTrue(result.isFailure)
    },
    test("zero cols rejected") {
      val result = scala.util.Try(TerminalSize(24, 0))
      assertTrue(result.isFailure)
    },
    test("negative rows rejected") {
      val result = scala.util.Try(TerminalSize(-1, 80))
      assertTrue(result.isFailure)
    },
    test("negative cols rejected") {
      val result = scala.util.Try(TerminalSize(24, -5))
      assertTrue(result.isFailure)
    },
    test("both dimensions invalid rejected") {
      val result = scala.util.Try(TerminalSize(0, 0))
      assertTrue(result.isFailure)
    },
    suite("from")(
      test("accepts positive dimensions") {
        assertTrue(
          TerminalSize.from(24, 80).contains(TerminalSize(24, 80)),
          TerminalSize.from(1, 1).contains(TerminalSize(1, 1))
        )
      },
      test("answers None for the 0 0 a detached pty reports") {
        assertTrue(TerminalSize.from(0, 0).isEmpty)
      },
      test("answers None when either dimension is not positive") {
        assertTrue(
          TerminalSize.from(0, 80).isEmpty,
          TerminalSize.from(24, 0).isEmpty,
          TerminalSize.from(-1, 80).isEmpty,
          TerminalSize.from(24, -5).isEmpty
        )
      }
    )
  )
