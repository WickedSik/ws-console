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
import java.util.concurrent.atomic.AtomicReference

object ApplicationSpec extends ZIOSpecDefault:

  // ===== Test infrastructure =====

  private object EmptyRoot extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Swallows `Ctrl+C` with `Consumed` — the §6.3 veto case. */
  private object CtrlCVetoRoot extends Component:
    override def handleEvent(event: Event, ctx: RenderContext): EventResult =
      event match
        case CharKey('c', mods) if mods.contains(KeyModifier.Ctrl) => EventResult.Consumed
        case _                                                     => EventResult.Ignored
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Answers every event with `Perform(effect)` — for §5.2 step-4 tests. */
  private final class PerformRoot(effect: ZIO[Frame, IOException, Unit]) extends Component:
    override def handleEvent(event: Event, ctx: RenderContext): EventResult =
      EventResult.Perform(effect)
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

    test("'q' triggers quit only when explicitly added to quitOn") {
      // `q` is not in the default set — Ctrl+C alone is. Consumers who
      // want a `q` shortcut supply it themselves.
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app    <- Application.make(Application.defaultQuitOn + CharKey('q', Set.empty))
        fiber  <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('q', Set.empty))
        result <- fiber.await.timeout(testTimeout)
      yield assertTrue(result match
        case Some(Exit.Success(_)) => true
        case _                     => false
      )
    } @@ TestAspect.withLiveClock,

    test("'q' does not quit under the default quitOn (Ctrl+C alone)") {
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app        <- Application.make
        fiber      <- app.run(EmptyRoot).provideSomeLayer[Any](layer).fork
        _          <- acquired.await
        _          <- events.offer(CharKey('q', Set.empty))
        _          <- ZIO.sleep(100.millis)
        stillAlive <- fiber.poll.map(_.isEmpty)
        _          <- app.quit
        _          <- fiber.await.timeout(testTimeout)
      yield assertTrue(stillAlive)
    } @@ TestAspect.withLiveClock,

    test("quitOn is vetoed when a component returns non-Ignored (§6.3)") {
      // A root that swallows Ctrl+C with `Consumed` vetoes the
      // framework's quit binding — the loop keeps running.
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app        <- Application.make
        fiber      <- app.run(CtrlCVetoRoot).provideSomeLayer[Any](layer).fork
        _          <- acquired.await
        _          <- events.offer(CharKey('c', Set(KeyModifier.Ctrl)))
        _          <- ZIO.sleep(100.millis)
        stillAlive <- fiber.poll.map(_.isEmpty)
        _          <- app.quit
        _          <- fiber.await.timeout(testTimeout)
      yield assertTrue(stillAlive)
    } @@ TestAspect.withLiveClock,

    test("Perform's effect runs on the loop fiber before onEvent (§5.2 step 4)") {
      // The root returns Perform for every event; the effect appends
      // "effect" to a log, onEvent appends "onEvent". Correct ordering
      // puts "effect" first.
      val log = new AtomicReference[Vector[String]](Vector.empty)
      val effect: ZIO[Frame, IOException, Unit] =
        ZIO.succeed { log.updateAndGet(_ :+ "effect"); () }
      val onEvent: (Event, EventResult) => ZIO[Frame, IOException, Boolean] =
        (_, _) =>
          ZIO.succeed { log.updateAndGet(_ :+ "onEvent"); () }.as(true)
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(new PerformRoot(effect), onEvent).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('x', Set.empty))
        _      <- ZIO.sleep(150.millis)
        _      <- app.quit
        _      <- fiber.await.timeout(testTimeout)
      yield
        val entries = log.get()
        val effectIdx  = entries.indexOf("effect")
        val onEventIdx = entries.indexOf("onEvent")
        assertTrue(
          effectIdx >= 0,
          onEventIdx >= 0,
          effectIdx < onEventIdx
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
    } @@ TestAspect.withLiveClock,

    test("onEvent observes every event including quit keys (§6.3)") {
      // With `quitOn` firing *after* `onEvent`, the consumer sees the
      // quit keystroke and can veto it by returning `false` — no, wait,
      // returning `false` stops the loop, so that would agree with quit.
      // The point here: nothing is hidden from `onEvent`; it observes
      // the event and receives the dispatcher's `Ignored` result even
      // for the framework's exit keys.
      val recorded = new AtomicReference[List[Event]](List.empty)
      val onEvent: (Event, EventResult) => ZIO[Frame, IOException, Boolean] =
        (event, _) =>
          ZIO.succeed {
            recorded.updateAndGet(_ :+ event)
            true
          }
      for
        s <- makeLayer
        (_, events, acquired, layer) = s
        app    <- Application.make
        fiber  <- app.run(EmptyRoot, onEvent).provideSomeLayer[Any](layer).fork
        _      <- acquired.await
        _      <- events.offer(CharKey('x', Set.empty))
        _      <- events.offer(CharKey('c', Set(KeyModifier.Ctrl)))
        result <- fiber.await.timeout(testTimeout)
      yield
        val log = recorded.get()
        assertTrue(
          result match
            case Some(Exit.Success(_)) => true
            case _                     => false,
          log.contains(CharKey('x', Set.empty)),
          log.contains(CharKey('c', Set(KeyModifier.Ctrl)))
        )
    } @@ TestAspect.withLiveClock
  ) @@ TestAspect.timeout(15.seconds)
