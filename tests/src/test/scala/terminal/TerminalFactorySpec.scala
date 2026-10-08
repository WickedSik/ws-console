package io.github.wickedsik.wsconsole
package terminal

import zio.*
import zio.test.*

object TerminalFactorySpec extends ZIOSpecDefault:

  private val validCaps = TerminalCapabilities(
    colorSupport = ColorSupport.TrueColor,
    supportsUnicode = true,
    supportsMouseTracking = true,
    supportsAlternateBuffer = true,
    isTTY = true,
    size = TerminalSize(24, 80)
  )

  /** Run validate and capture the error message if it fails */
  private def validateAndCapture(caps: TerminalCapabilities): ZIO[Any, Nothing, Either[String, Unit]] =
    TerminalFactory.validate(caps)
      .map(Right(_))
      .catchAll(e => ZIO.succeed(Left(e.getMessage)))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("TerminalFactory.validate")(
    test("all requirements met succeeds") {
      for
        result <- validateAndCapture(validCaps)
      yield assertTrue(result.isRight)
    },
    test("missing TTY fails with TTY message") {
      val caps = validCaps.copy(isTTY = false)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(
        result.isLeft,
        result.swap.toOption.exists(_.contains("TTY required"))
      )
    },
    test("NoColor fails with color message") {
      val caps = validCaps.copy(colorSupport = ColorSupport.NoColor)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(
        result.isLeft,
        result.swap.toOption.exists(_.contains("color support"))
      )
    },
    test("Basic16 passes validation") {
      val caps = validCaps.copy(colorSupport = ColorSupport.Basic16)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(result.isRight)
    },
    test("Extended256 passes validation") {
      val caps = validCaps.copy(colorSupport = ColorSupport.Extended256)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(result.isRight)
    },
    test("missing unicode fails with Unicode message") {
      val caps = validCaps.copy(supportsUnicode = false)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(
        result.isLeft,
        result.swap.toOption.exists(_.contains("Unicode"))
      )
    },
    test("multiple failures reports all issues") {
      val caps = validCaps.copy(
        isTTY = false,
        colorSupport = ColorSupport.NoColor,
        supportsUnicode = false
      )
      for
        result <- validateAndCapture(caps)
      yield
        val msg = result.swap.toOption.getOrElse("")
        assertTrue(
          result.isLeft,
          msg.contains("TTY required"),
          msg.contains("color support"),
          msg.contains("Unicode")
        )
    },
    test("error message includes supported terminals list") {
      val caps = validCaps.copy(isTTY = false)
      for
        result <- validateAndCapture(caps)
      yield assertTrue(
        result.swap.toOption.exists(_.contains("Supported terminals"))
      )
    },
    test("error is UnsupportedTerminalException") {
      val caps = validCaps.copy(isTTY = false)
      for
        exit <- TerminalFactory.validate(caps).exit
      yield assertTrue(
        exit match
          case Exit.Failure(cause) => cause.failureOption.exists(_.isInstanceOf[UnsupportedTerminalException])
          case _                   => false
      )
    }
  )
