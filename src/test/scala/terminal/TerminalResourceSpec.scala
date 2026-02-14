package io.github.wickedsik.wsconsole
package terminal

import ansi.AnsiBuilder
import zio.*
import zio.test.*

import java.io.IOException

object TerminalResourceSpec extends ZIOSpecDefault:

  /**
   * Mock Terminal that records method calls for verifying acquire/release behavior.
   * All methods append their name to a Ref for later assertion.
   * An optional Promise can be completed on specific actions to coordinate test timing.
   */
  private class RecordingTerminal(
    log: Ref[List[String]],
    acquiredSignal: Option[Promise[Nothing, Unit]] = None
  ) extends Terminal:
    private def record(name: String): IO[IOException, Unit] =
      log.update(_ :+ name)

    private def recordAndSignal(name: String): IO[IOException, Unit] =
      record(name) *> ZIO.fromOption(acquiredSignal).flatMap(_.succeed(())).ignore

    override def enterRawMode: IO[IOException, Unit] = recordAndSignal("enterRawMode")
    override def exitRawMode: IO[IOException, Unit] = record("exitRawMode")
    override def enterAlternateBuffer: IO[IOException, Unit] = recordAndSignal("enterAlternateBuffer")
    override def exitAlternateBuffer: IO[IOException, Unit] = record("exitAlternateBuffer")
    override def moveCursor(row: Int, col: Int): IO[IOException, Unit] = record("moveCursor")
    override def hideCursor: IO[IOException, Unit] = recordAndSignal("hideCursor")
    override def showCursor: IO[IOException, Unit] = record("showCursor")
    override def saveCursor: IO[IOException, Unit] = record("saveCursor")
    override def restoreCursor: IO[IOException, Unit] = record("restoreCursor")
    override def clearScreen: IO[IOException, Unit] = record("clearScreen")
    override def clearLine: IO[IOException, Unit] = record("clearLine")
    override def setScrollRegion(top: Int, bottom: Int): IO[IOException, Unit] = record("setScrollRegion")
    override def resetScrollRegion: IO[IOException, Unit] = record("resetScrollRegion")
    override def write(text: String): IO[IOException, Unit] = record("write")
    override def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit] = record("writeBuilder")
    override def flush: IO[IOException, Unit] = record("flush")
    override def readRaw(timeout: Duration): IO[IOException, RawInput] = ZIO.succeed(RawInput.Timeout)
    override def size: IO[IOException, TerminalSize] = ZIO.succeed(TerminalSize(24, 80))
    override def capabilities: IO[IOException, TerminalCapabilities] = ZIO.succeed(
      TerminalCapabilities(ColorSupport.TrueColor, true, true, true, true, TerminalSize(24, 80))
    )

  private def withRecordingTerminal[A](
    body: (Ref[List[String]], ZLayer[Any, Nothing, Terminal]) => ZIO[Any, Any, A]
  ): ZIO[Any, Any, A] =
    for
      log <- Ref.make[List[String]](Nil)
      terminal = new RecordingTerminal(log)
      layer = ZLayer.succeed[Terminal](terminal)
      result <- body(log, layer)
    yield result

  /** Variant that provides a Promise for coordinating interruption tests */
  private def withSignalingTerminal[A](
    body: (Ref[List[String]], ZLayer[Any, Nothing, Terminal], Promise[Nothing, Unit]) => ZIO[Any, Any, A]
  ): ZIO[Any, Any, A] =
    for
      log      <- Ref.make[List[String]](Nil)
      acquired <- Promise.make[Nothing, Unit]
      terminal = new RecordingTerminal(log, Some(acquired))
      layer = ZLayer.succeed[Terminal](terminal)
      result <- body(log, layer, acquired)
    yield result

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Terminal resource helpers")(
    alternateBufferSuite,
    hiddenCursorSuite,
    rawModeSuite
  )

  private val alternateBufferSuite = suite("withAlternateBuffer")(

    test("enters and exits buffer on success") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withAlternateBuffer(ZIO.unit).provide(layer)
          calls <- log.get
        yield assertTrue(
          calls.head == "enterAlternateBuffer",
          calls.last == "exitAlternateBuffer"
        )
      }
    },

    test("exits buffer on failure") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withAlternateBuffer(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- log.get
        yield assertTrue(
          calls.contains("enterAlternateBuffer"),
          calls.contains("exitAlternateBuffer")
        )
      }
    },

    test("exits buffer on interruption") {
      withSignalingTerminal { (log, layer, acquired) =>
        for
          fiber <- Terminal.withAlternateBuffer(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- log.get
        yield assertTrue(
          calls.contains("enterAlternateBuffer"),
          calls.contains("exitAlternateBuffer")
        )
      }
    }
  )

  private val hiddenCursorSuite = suite("withHiddenCursor")(

    test("hides and shows cursor on success") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withHiddenCursor(ZIO.unit).provide(layer)
          calls <- log.get
        yield assertTrue(
          calls.head == "hideCursor",
          calls.last == "showCursor"
        )
      }
    },

    test("shows cursor on failure") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withHiddenCursor(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- log.get
        yield assertTrue(
          calls.contains("hideCursor"),
          calls.contains("showCursor")
        )
      }
    },

    test("shows cursor on interruption") {
      withSignalingTerminal { (log, layer, acquired) =>
        for
          fiber <- Terminal.withHiddenCursor(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- log.get
        yield assertTrue(
          calls.contains("hideCursor"),
          calls.contains("showCursor")
        )
      }
    }
  )

  private val rawModeSuite = suite("withRawMode")(

    test("enters and exits raw mode on success") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withRawMode(ZIO.unit).provide(layer)
          calls <- log.get
        yield assertTrue(
          calls.head == "enterRawMode",
          calls.last == "exitRawMode"
        )
      }
    },

    test("exits raw mode on failure") {
      withRecordingTerminal { (log, layer) =>
        for
          _ <- Terminal.withRawMode(
            ZIO.fail(new IOException("boom"))
          ).provide(layer).exit
          calls <- log.get
        yield assertTrue(
          calls.contains("enterRawMode"),
          calls.contains("exitRawMode")
        )
      }
    },

    test("exits raw mode on interruption") {
      withSignalingTerminal { (log, layer, acquired) =>
        for
          fiber <- Terminal.withRawMode(
            acquired.await *> ZIO.never
          ).provide(layer).fork
          _ <- acquired.await
          _ <- fiber.interrupt
          calls <- log.get
        yield assertTrue(
          calls.contains("enterRawMode"),
          calls.contains("exitRawMode")
        )
      }
    }
  )
