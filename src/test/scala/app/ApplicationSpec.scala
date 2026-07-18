package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Frame}
import component.{Component, RenderContext}
import event.{Event, EventResult, KeyEvent, KeyModifier}
import event.KeyEvent.CharKey
import geometry.Rect
import terminal.Terminal
import testkit.CaptureTerminal

import zio.*
import zio.stream.ZStream
import zio.test.*

import java.io.IOException

object ApplicationSpec extends ZIOSpecDefault:

  // ===== Test infrastructure =====

  private object EmptyRoot extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /**
   * Set up a fresh capture terminal + event queue + acquired signal + layer
   * for each test. The terminal fires `acquired` when it records `enterRawMode`
   * (the last of the three acquires) so tests can wait for full lifecycle
   * entry before triggering interrupt / quit, and it streams injected events
   * from `eventsQ`.
   */
  private val makeLayer: UIO[(CaptureTerminal, Queue[Event], Promise[Nothing, Unit], ZLayer[Any, Nothing, Terminal & Frame])] =
    for
      events   <- Queue.unbounded[Event]
      acquired <- Promise.make[Nothing, Unit]
      term     <- CaptureTerminal.make(
                    events  = Some(ZStream.fromQueue(events)),
                    signals = Map("enterRawMode" -> acquired)
                  )
      terminal  = ZLayer.succeed[Terminal](term)
      frame     = terminal >>> Frame.live.orDie
    yield (term, events, acquired, terminal ++ frame)

  /** Cap a long-running fiber so a test never hangs the suite. */
  private val testTimeout: Duration = Duration.fromSeconds(5)

  // ===== Specs =====

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Application")(

    test("run acquires alt buffer → wrap off → hidden cursor → raw mode in order") {
      for
        s <- makeLayer
        (term, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- app.quit
        _      <- fiber.join.timeout(testTimeout)
        calls  <- term.capturedOps
      yield
        val acquires = calls.filter(c =>
          c == "enterAlternateBuffer" || c == "disableLineWrap" || c == "hideCursor" || c == "enterRawMode"
        )
        assertTrue(acquires == Chunk("enterAlternateBuffer", "disableLineWrap", "hideCursor", "enterRawMode"))
    } @@ TestAspect.withLiveClock,

    test("release runs in reverse order on clean exit (quit)") {
      for
        s <- makeLayer
        (term, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- app.quit
        _      <- fiber.join.timeout(testTimeout)
        calls  <- term.capturedOps
      yield
        val releases = calls.filter(c =>
          c == "exitAlternateBuffer" || c == "enableLineWrap" || c == "showCursor" || c == "exitRawMode"
        )
        assertTrue(releases == Chunk("exitRawMode", "showCursor", "enableLineWrap", "exitAlternateBuffer"))
    } @@ TestAspect.withLiveClock,

    test("release runs on fiber interruption") {
      for
        s <- makeLayer
        (term, _, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- fiber.interrupt
        calls  <- term.capturedOps
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
        (term, events, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('c', Set(KeyModifier.Ctrl)))
        result <- fiber.await.timeout(testTimeout)
        calls  <- term.capturedOps
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
