package io.github.wickedsik.wsconsole
package app

import buffer.Canvas
import component.{Component, RenderContext}
import geometry.Rect

import zio.{Scope, ZIO, durationInt}
import zio.test.*

import testkit.{CaptureTerminal, FrameHarness}

import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies the animated-panel pattern end-to-end (minus timing): a
 * `Component` reads a mutable frame source and renders based on it;
 * when wrapped in a `Panel` and driven through the real diff→flush→swap
 * pipeline, each frame's cells reach the drawn buffer as expected.
 *
 * Uses a minimal test-only `Component` — a `putChar` cycling through a
 * fixed glyph alphabet — to isolate the animated-content pattern from
 * any specific demo panel's rendering details.
 *
 * Timing concerns (tick-fiber cadence, `Application.requestRedraw`
 * coalescing) are out of scope — the frame source is advanced manually
 * to isolate the render pipeline. A `TestClock`-based cadence spec
 * would be a separate future addition (see `visual-integration-testing.md`
 * Constraint 3 — no `TestClock.adjust` stepping loop exists in the tree yet).
 */
object AnimatedPanelPipelineSpec extends ZIOSpecDefault:

  /**
   * One-glyph-per-frame test alphabet. Ten distinct characters so a
   * successive sequence is unambiguous in the drawn buffer.
   */
  private val glyphs = "0123456789".toVector
  private val cellX = 5
  private val cellY = 3
  private val bounds = Rect(0, 0, 80, 24)

  /**
   * Minimal animated component. Reads the current frame from an
   * `AtomicInteger` and writes the corresponding glyph at (cellX, cellY).
   * The rest of the panel bounds are left blank — the host's opacity
   * pre-fill ensures they're empty rather than stale from a previous frame.
   */
  final private class TestSpinner(frame: AtomicInteger) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      val g = glyphs(math.floorMod(frame.get(), glyphs.length))
      canvas.putChar(cellX, cellY, g)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Animated panel pipeline")(
    test("each frame's glyph reaches the drawn buffer through the diff→flush pipeline") {
      // Drive a test-only animated component through PanelHost + FrameHarness
      // for a sequence of frame indices. Each iteration:
      //   1. Advances the mutable frame source (what a real tick fiber would do)
      //   2. Runs one full pipeline pass: Component.render → BufferManager.diff
      //      → BufferFlusher → CaptureTerminal.writeBuilder → BufferManager.swap
      //   3. Reads the char at (cellX, cellY) from the drawn buffer
      //
      // The observed sequence must match the expected glyph sequence
      // exactly — proving each frame lands intact in the drawn buffer.
      val frameCount = 10

      for
        h <- FrameHarness.make(80, 24)
        host <- PanelHost.make()
        tick = new AtomicInteger(0)
        panel = Panel.of(new TestSpinner(tick), bounds)
        _ <- host.push(panel).provide(CaptureTerminal.layer(), h.frameLayer)

        observed <- ZIO.foreach((0 until frameCount).toVector) { n =>
          for
            _ <- ZIO.succeed(tick.set(n))
            _ <- h.run(host.root)
            actual = h.drawnBuffer.get(cellX, cellY).map(_.char)
          yield actual
        }
      yield
        val expected = (0 until frameCount).map(n => Some(glyphs(n % glyphs.length))).toVector
        assertTrue(observed == expected)
    }
  ) @@ TestAspect.timeout(10.seconds)
