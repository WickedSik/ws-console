package io.github.wickedsik.wsconsole
package app

import ansi.AnsiBuilder
import buffer.{Canvas, Frame}
import component.{Component, RenderContext}
import event.{Event, EventResult, KeyEvent, KeyModifier}
import event.KeyEvent.CharKey
import geometry.Rect
import terminal.{ColorSupport, RawInput, Terminal, TerminalCapabilities, TerminalSize}

import zio.*
import zio.stream.ZStream
import zio.test.*

import java.io.IOException

object ApplicationSpec extends ZIOSpecDefault:

  // ===== Test infrastructure =====

  /**
   * Terminal that records every lifecycle call and supplies a controllable
   * event stream backed by a `Queue[Event]`. `acquired` completes after
   * `enterRawMode` records — the last of the three acquires — so tests
   * can wait for full lifecycle entry before triggering interrupt / quit.
   */
  private final class TestTerminal(
    log:      Ref[Vector[String]],
    eventsQ:  Queue[Event],
    acquired: Promise[Nothing, Unit]
  ) extends Terminal:
    private def record(name: String): IO[IOException, Unit] = log.update(_ :+ name)

    def enterRawMode:          IO[IOException, Unit] = record("enterRawMode") *> acquired.succeed(()).unit
    def exitRawMode:           IO[IOException, Unit] = record("exitRawMode")
    def enterAlternateBuffer:  IO[IOException, Unit] = record("enterAlternateBuffer")
    def exitAlternateBuffer:   IO[IOException, Unit] = record("exitAlternateBuffer")
    def disableLineWrap:       IO[IOException, Unit] = record("disableLineWrap")
    def enableLineWrap:        IO[IOException, Unit] = record("enableLineWrap")
    def hideCursor:            IO[IOException, Unit] = record("hideCursor")
    def showCursor:            IO[IOException, Unit] = record("showCursor")

    def moveCursor(row: Int, col: Int):         IO[IOException, Unit] = ZIO.unit
    def saveCursor:                             IO[IOException, Unit] = ZIO.unit
    def restoreCursor:                          IO[IOException, Unit] = ZIO.unit
    def clearScreen:                            IO[IOException, Unit] = ZIO.unit
    def clearLine:                              IO[IOException, Unit] = ZIO.unit
    def setScrollRegion(top: Int, bottom: Int): IO[IOException, Unit] = ZIO.unit
    def resetScrollRegion:                      IO[IOException, Unit] = ZIO.unit
    def write(text: String):                    IO[IOException, Unit] = ZIO.unit
    def writeBuilder(builder: AnsiBuilder):     IO[IOException, Unit] = ZIO.unit
    def flush:                                  IO[IOException, Unit] = ZIO.unit
    def readRaw(timeout: Duration):             IO[IOException, RawInput] = ZIO.succeed(RawInput.Timeout)

    def size: IO[IOException, TerminalSize] = ZIO.succeed(TerminalSize(24, 80))
    def capabilities: IO[IOException, TerminalCapabilities] =
      ZIO.succeed(TerminalCapabilities(ColorSupport.TrueColor, true, true, true, true, TerminalSize(24, 80)))

    override def events: ZStream[Any, IOException, Event] =
      ZStream.fromQueue(eventsQ)

  private object EmptyRoot extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Set up a fresh log + event queue + acquired signal + layer for each test. */
  private val makeLayer: UIO[(Ref[Vector[String]], Queue[Event], Promise[Nothing, Unit], ZLayer[Any, Nothing, Terminal & Frame])] =
    for
      log      <- Ref.make(Vector.empty[String])
      events   <- Queue.unbounded[Event]
      acquired <- Promise.make[Nothing, Unit]
      term      = new TestTerminal(log, events, acquired)
      terminal  = ZLayer.succeed[Terminal](term)
      frame     = terminal >>> Frame.live.orDie
    yield (log, events, acquired, terminal ++ frame)

  /** Cap a long-running fiber so a test never hangs the suite. */
  private val testTimeout: Duration = Duration.fromSeconds(5)

  // ===== Specs =====

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Application")(

    test("run acquires alt buffer → wrap off → hidden cursor → raw mode in order") {
      for
        s <- makeLayer
        (log, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- app.quit
        _      <- fiber.join.timeout(testTimeout)
        calls  <- log.get
      yield
        val acquires = calls.filter(c =>
          c == "enterAlternateBuffer" || c == "disableLineWrap" || c == "hideCursor" || c == "enterRawMode"
        )
        assertTrue(acquires == Vector("enterAlternateBuffer", "disableLineWrap", "hideCursor", "enterRawMode"))
    } @@ TestAspect.withLiveClock,

    test("release runs in reverse order on clean exit (quit)") {
      for
        s <- makeLayer
        (log, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- app.quit
        _      <- fiber.join.timeout(testTimeout)
        calls  <- log.get
      yield
        val releases = calls.filter(c =>
          c == "exitAlternateBuffer" || c == "enableLineWrap" || c == "showCursor" || c == "exitRawMode"
        )
        assertTrue(releases == Vector("exitRawMode", "showCursor", "enableLineWrap", "exitAlternateBuffer"))
    } @@ TestAspect.withLiveClock,

    test("release runs on fiber interruption") {
      for
        s <- makeLayer
        (log, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- fiber.interrupt
        calls  <- log.get
      yield assertTrue(
        calls.contains("exitAlternateBuffer"),
        calls.contains("showCursor"),
        calls.contains("exitRawMode")
      )
    } @@ TestAspect.withLiveClock,

    test("quit returns cleanly without raising an error") {
      for
        s <- makeLayer
        (_, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- app.quit
        result <- fiber.await.timeout(testTimeout)
      yield assertTrue(result match
        case Some(Exit.Success(_)) => true
        case _                     => false
      )
    } @@ TestAspect.withLiveClock,

    test("Ctrl+C parsed event triggers quit automatically") {
      for
        s <- makeLayer
        (log, events, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('c', Set(KeyModifier.Ctrl)))
        result <- fiber.await.timeout(testTimeout)
        calls  <- log.get
      yield assertTrue(
        result match
          case Some(Exit.Success(_)) => true
          case _                     => false,
        calls.contains("exitRawMode")
      )
    } @@ TestAspect.withLiveClock,

    test("'q' keypress triggers quit automatically") {
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('q', Set.empty))
        result <- fiber.await.timeout(testTimeout)
      yield assertTrue(result match
        case Some(Exit.Success(_)) => true
        case _                     => false
      )
    } @@ TestAspect.withLiveClock,

    test("empty quitOn set disables automatic quit") {
      // With an empty quitOn set, 'q' does not terminate; only an
      // explicit app.quit (or consumer onEvent returning false) stops the loop.
      val sentinel = new java.util.concurrent.atomic.AtomicInteger(0)
      val onEvent: (Event, EventResult) => UIO[Boolean] = (_, _) =>
        sentinel.incrementAndGet()
        ZIO.succeed(true)
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app    <- Application.make(Set.empty)
        fiber  <- app.run(EmptyRoot, onEvent).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('q', Set.empty))
        _      <- ZIO.sleep(100.millis)  // give the event time to reach onEvent
        _      <- app.quit
        _      <- fiber.join.timeout(testTimeout)
      yield assertTrue(sentinel.get() >= 1)
    } @@ TestAspect.withLiveClock
  ) @@ TestAspect.timeout(15.seconds)
