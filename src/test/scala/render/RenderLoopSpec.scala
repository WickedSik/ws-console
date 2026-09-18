package io.github.wickedsik.wsconsole
package render

import buffer.{Canvas, Cell}
import component.{Component, RenderContext}
import event.EventResult
import geometry.Rect
import terminal.Terminal
import testkit.FrameHarness

import zio.*
import zio.test.*

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration coverage for the loop itself, driven over the real
 * [[testkit.FrameHarness]] / [[testkit.CaptureTerminal]] pipeline.
 *
 * The loop is the branch's most stateful component — the redraw queue, the
 * invalidate / refresh consumption, and the ordering of `setOrder` against
 * the draw phase all live here, and none of it is reachable from the
 * component- or buffer-level specs.
 *
 * '''Timing.''' These tests run the loop on a forked fiber against the live
 * clock and poll the probe's render history rather than sleeping a fixed
 * amount. Every wait is bounded by [[SettleTimeout]] so a regression fails
 * the assertion instead of hanging the suite.
 */
object RenderLoopSpec extends ZIOSpecDefault:

  /** Upper bound on how long a correct loop may take to produce a frame. */
  private val SettleTimeout: Duration = 10.seconds

  /**
   * Focusable leaf that records the focus flag it saw on each render and
   * paints a glyph that differs by focus state — so the render history and
   * the emitted wire bytes can be asserted about the same frame.
   *
   * The history is an `AtomicReference` because `Component.render` is
   * synchronous and cannot touch a `Ref`.
   */
  final private class FocusProbe extends Component:
    override val focusable: Boolean = true

    private val seen = new AtomicReference[Vector[Boolean]](Vector.empty)
    private val stamps = new AtomicReference[Vector[Instant]](Vector.empty)

    /** Focus flag observed on every render so far, in frame order. */
    def renders: Vector[Boolean] = seen.get()

    /** Wall-clock timestamp observed on every render so far, in frame order. */
    def timestamps: Vector[Instant] = stamps.get()

    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      val focused = ctx.focus.isFocused(id)
      seen.updateAndGet(_ :+ focused)
      stamps.updateAndGet(_ :+ ctx.timestamp)
      canvas.fillRect(Rect(area.x, area.y, 1, 1), Cell(if focused then 'F' else 'u'))

  /** Poll `probe` until `pred` holds over its render history, or time out. */
  private def settleUntil(probe: FocusProbe)(pred: Vector[Boolean] => Boolean): UIO[Option[Vector[Boolean]]] =
    (ZIO.sleep(5.millis) *> ZIO.succeed(probe.renders))
      .repeatUntil(pred)
      .timeout(SettleTimeout)

  private def runLoop(harness: FrameHarness, loop: RenderLoop, root: Component) =
    loop
      .start(root, (_, _: EventResult) => ZIO.succeed(true))
      .provide(ZLayer.succeed[Terminal](harness.terminal), harness.frameLayer)
      .fork

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RenderLoop")(
    // Regression: `redraw` snapshots focus, renders, and only then calls
    // `setOrder`. When the policy auto-focuses an entry during that
    // reconciliation, the frame already on screen was drawn without it. With
    // no signal from `setOrder`, no further frame is ever scheduled and the
    // display contradicts the FocusManager until an unrelated event arrives.
    test("a focus move made during reconciliation reaches the screen") {
      for
        harness <- FrameHarness.make(8, 3)
        loop    <- RenderLoop.make()
        probe = new FocusProbe
        fiber   <- runLoop(harness, loop, probe)
        settled <- settleUntil(probe)(_.contains(true))
        _       <- loop.stop
        _       <- fiber.join
        wire    <- harness.captured
        history = probe.renders
      yield assertTrue(
        // The focused frame arrived at all...
        settled.isDefined,
        // ...it was genuinely a *later* frame, not the first one...
        history.headOption.contains(false),
        history.contains(true),
        // ...and it was flushed to the terminal, not merely computed.
        wire.contains('F')
      )
    } @@ TestAspect.withLiveClock,
    test("the loop settles: no redraw is scheduled once focus is stable") {
      for
        harness <- FrameHarness.make(8, 3)
        loop    <- RenderLoop.make()
        probe = new FocusProbe
        fiber <- runLoop(harness, loop, probe)
        _     <- settleUntil(probe)(_.contains(true))
        before = probe.renders.size
        _ <- ZIO.sleep(300.millis)
        after = probe.renders.size
        _ <- loop.stop
        _ <- fiber.join
      yield assertTrue(
        // Reconciliation is idempotent, so the setOrder signal must not
        // feed itself an endless stream of frames.
        after == before
      )
    } @@ TestAspect.withLiveClock,

    // Timestamp reaches components through `ctx`, sampled per frame from
    // `Clock.instant`. The default `RenderContext.empty` timestamp is
    // `Instant.EPOCH`, so any post-epoch reading proves the loop sampled the
    // clock rather than falling through to the default. "Stable within a
    // frame" is a structural property of `Renderer.renderFull` threading one
    // ctx through the whole tree walk — not tested here because this probe is
    // a single leaf.
    test("ctx.timestamp reaches components from Clock.instant on each frame") {
      for
        harness <- FrameHarness.make(8, 3)
        loop    <- RenderLoop.make()
        probe = new FocusProbe
        fiber <- runLoop(harness, loop, probe)
        _     <- settleUntil(probe)(_.nonEmpty)
        _     <- loop.stop
        _     <- fiber.join
        stamps = probe.timestamps
      yield assertTrue(
        stamps.nonEmpty,
        stamps.forall(_.isAfter(Instant.EPOCH))
      )
    } @@ TestAspect.withLiveClock,
    test("requestFullRedraw clears the display, requestRefresh does not") {
      for
        harness <- FrameHarness.make(8, 3)
        loop    <- RenderLoop.make()
        probe = new FocusProbe
        fiber <- runLoop(harness, loop, probe)
        _     <- settleUntil(probe)(_.nonEmpty)

        beforeFull = probe.renders.size
        _        <- harness.clearCaptured
        _        <- loop.requestFullRedraw
        _        <- settleUntil(probe)(_.size > beforeFull)
        fullWire <- harness.captured

        beforeSoft = probe.renders.size
        _        <- harness.clearCaptured
        _        <- loop.requestRefresh
        _        <- settleUntil(probe)(_.size > beforeSoft)
        softWire <- harness.captured

        _ <- loop.stop
        _ <- fiber.join
      yield assertTrue(
        fullWire.contains("[2J"),
        !softWire.contains("[2J"),
        // The refresh still re-establishes the frame — flicker-free, not silent.
        softWire.nonEmpty
      )
    } @@ TestAspect.withLiveClock
  ) @@ TestAspect.sequential @@ TestAspect.timeout(60.seconds)
