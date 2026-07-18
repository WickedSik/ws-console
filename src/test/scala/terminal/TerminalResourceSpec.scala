package io.github.wickedsik.wsconsole
package terminal

import testkit.CaptureTerminal

import zio.*
import zio.test.*

import java.io.IOException

object TerminalResourceSpec extends ZIOSpecDefault:

  private def withRecordingTerminal[A](
    body: (CaptureTerminal, ZLayer[Any, Nothing, Terminal]) => ZIO[Any, Any, A]
  ): ZIO[Any, Any, A] =
    for
      terminal <- CaptureTerminal.make()
      layer     = ZLayer.succeed[Terminal](terminal)
      result   <- body(terminal, layer)
    yield result

  /** Variant that provides a Promise for coordinating interruption tests */
  private def withSignalingTerminal[A](
    body: (CaptureTerminal, ZLayer[Any, Nothing, Terminal], Promise[Nothing, Unit]) => ZIO[Any, Any, A]
  ): ZIO[Any, Any, A] =
    for
      acquired <- Promise.make[Nothing, Unit]
      terminal <- CaptureTerminal.make(signals = Map(
                    "enterRawMode"         -> acquired,
                    "enterAlternateBuffer" -> acquired,
                    "hideCursor"           -> acquired
                  ))
      layer     = ZLayer.succeed[Terminal](terminal)
      result   <- body(terminal, layer, acquired)
    yield result

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Terminal resource helpers")(
    alternateBufferSuite,
    hiddenCursorSuite,
    rawModeSuite
  )

  private val alternateBufferSuite = suite("withAlternateBuffer")(

    test("enters and exits buffer on success") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withAlternateBuffer(ZIO.unit).provide(layer)
          calls <- term.capturedOps
        yield assertTrue(
          calls.head == "enterAlternateBuffer",
          calls.last == "exitAlternateBuffer"
        )
      }
    },

    test("exits buffer on failure") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withAlternateBuffer(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("enterAlternateBuffer"),
          calls.contains("exitAlternateBuffer")
        )
      }
    },

    test("exits buffer on interruption") {
      withSignalingTerminal { (term, layer, acquired) =>
        for
          fiber <- Terminal.withAlternateBuffer(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("enterAlternateBuffer"),
          calls.contains("exitAlternateBuffer")
        )
      }
    }
  )

  private val hiddenCursorSuite = suite("withHiddenCursor")(

    test("hides and shows cursor on success") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withHiddenCursor(ZIO.unit).provide(layer)
          calls <- term.capturedOps
        yield assertTrue(
          calls.head == "hideCursor",
          calls.last == "showCursor"
        )
      }
    },

    test("shows cursor on failure") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withHiddenCursor(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("hideCursor"),
          calls.contains("showCursor")
        )
      }
    },

    test("shows cursor on interruption") {
      withSignalingTerminal { (term, layer, acquired) =>
        for
          fiber <- Terminal.withHiddenCursor(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("hideCursor"),
          calls.contains("showCursor")
        )
      }
    }
  )

  private val rawModeSuite = suite("withRawMode")(

    test("enters and exits raw mode on success") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withRawMode(ZIO.unit).provide(layer)
          calls <- term.capturedOps
        yield assertTrue(
          calls.head == "enterRawMode",
          calls.last == "exitRawMode"
        )
      }
    },

    test("exits raw mode on failure") {
      withRecordingTerminal { (term, layer) =>
        for
          _ <- Terminal.withRawMode(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("enterRawMode"),
          calls.contains("exitRawMode")
        )
      }
    },

    test("exits raw mode on interruption") {
      withSignalingTerminal { (term, layer, acquired) =>
        for
          fiber <- Terminal.withRawMode(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- term.capturedOps
        yield assertTrue(
          calls.contains("enterRawMode"),
          calls.contains("exitRawMode")
        )
      }
    }
  )
