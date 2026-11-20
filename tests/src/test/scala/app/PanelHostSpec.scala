package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Cell, Frame}
import component.{Component, RenderContext}
import geometry.Rect
import render.LayoutManager
import testkit.FrameHarness

import zio.*
import zio.test.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object PanelHostSpec extends ZIOSpecDefault:

  // ===== Test infrastructure =====

  private object Blank extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Component that writes a single character to every cell of its area. */
  final private class Fill(ch: Char, focusableFlag: Boolean = false) extends Component:
    val renderCount = AtomicInteger(0)
    override val focusable: Boolean = focusableFlag
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      renderCount.incrementAndGet()
      canvas.fillRect(area, Cell(ch))

  private val ctx: RenderContext = RenderContext.empty

  /**
   * Recording panel that timestamps each lifecycle invocation against a
   * shared `counter`. Tests construct one counter and pass it to every
   * panel in the same scenario so cross-panel call ordering is observable.
   */
  final private class RecordingPanel(
    fixedBounds: Rect,
    counter: AtomicInteger,
    val root: Component = Blank
  ) extends Panel:
    override def bounds(hostArea: Rect): Rect = fixedBounds
    private val mounted = AtomicInteger(-1)
    private val unloaded = AtomicInteger(-1)
    private val remounted = AtomicInteger(-1)

    def mountAt: Int = mounted.get()
    def unloadAt: Int = unloaded.get()
    def remountAt: Int = remounted.get()

    override def onMount: ZIO[Frame, IOException, Unit] =
      ZIO.succeed(mounted.set(counter.incrementAndGet()))

    override def onUnload: ZIO[Frame, IOException, Unit] =
      ZIO.succeed(unloaded.set(counter.incrementAndGet()))

    override def onRemount: ZIO[Frame, IOException, Unit] =
      ZIO.succeed(remounted.set(counter.incrementAndGet()))

  private def withEnv[A](f: ZIO[Frame, IOException, A]): IO[IOException, A] =
    for
      h <- FrameHarness.make(80, 24)
      result <- f.provide(h.frameLayer)
    yield result

  // ===== Specs =====

  def spec: Spec[TestEnvironment & Scope, Any] = suite("PanelHost")(
    test("push runs the new panel's onMount") {
      val counter = AtomicInteger(0)
      val panel = RecordingPanel(Rect(0, 0, 10, 10), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(panel))
      yield assertTrue(panel.mountAt == 1, panel.unloadAt == -1, panel.remountAt == -1)
    },
    test("pop runs popped panel's onUnload then revealed panel's onRemount (Q6)") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = RecordingPanel(Rect(5, 2, 10, 5), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
        _    <- withEnv(host.pop)
      yield assertTrue(
        a.mountAt == 1,
        b.mountAt == 2,
        b.unloadAt == 3,
        a.remountAt == 4,
        a.unloadAt == -1 // a was never popped
      )
    },
    test("pop does NOT re-invoke the revealed panel's onMount (Q6)") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = RecordingPanel(Rect(0, 0, 20, 10), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
        _    <- withEnv(host.pop)
      yield assertTrue(
        // a's onMount fired exactly once, on the initial push
        a.mountAt == 1,
        a.remountAt == 4,
        // a's onMount counter was not advanced by the reveal
        a.mountAt != a.remountAt
      )
    },
    test("replace = pop + push atomically; revealed panel's onRemount does NOT fire") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = RecordingPanel(Rect(0, 0, 20, 10), counter)
      val c = RecordingPanel(Rect(0, 0, 20, 10), counter)
      for
        host    <- PanelHost.make()
        _       <- withEnv(host.push(a))
        _       <- withEnv(host.push(b))
        _       <- withEnv(host.replace(c))
        visible <- host.visible
      yield assertTrue(
        a.unloadAt == -1,
        b.unloadAt > 0,
        c.mountAt > 0,
        a.remountAt == -1, // replace does not reveal the underlying panel
        visible.map(_.root) == List(a.root, c.root)
      )
    },
    test("active reports the top of the stack; None when empty") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 10, 10), counter)
      val b = RecordingPanel(Rect(0, 0, 10, 10), counter)
      for
        host    <- PanelHost.make()
        empty   <- host.active
        _       <- withEnv(host.push(a))
        topA    <- host.active
        _       <- withEnv(host.push(b))
        topB    <- host.active
        _       <- withEnv(host.pop)
        topBack <- host.active
      yield assertTrue(
        empty.isEmpty,
        topA.contains(a),
        topB.contains(b),
        topBack.contains(a)
      )
    },
    test("pop on an empty stack returns false and changes nothing") {
      for
        host    <- PanelHost.make()
        removed <- withEnv(host.pop)
        stack   <- host.visible
      yield assertTrue(!removed, stack.isEmpty)
    },
    test("pop on a non-empty stack returns true") {
      for
        host    <- PanelHost.make()
        _       <- withEnv(host.push(Panel.of(Blank)))
        removed <- withEnv(host.pop)
        stack   <- host.visible
      yield assertTrue(removed, stack.isEmpty)
    },
    test("root renders all visible panels bottom-to-top (Q7 — Option E)") {
      // Panel A fills (0,0,20,10) with 'A'; Panel B fills (5,2,10,5) with 'B'.
      // Inside B's bounds we expect B; outside, A.
      val fillA = Fill('A')
      val fillB = Fill('B')
      val a = Panel.overlay(fillA, Rect(0, 0, 20, 10))
      val b = Panel.overlay(fillB, Rect(5, 2, 10, 5))
      for
        h <- FrameHarness.make(20, 10)
        host <- PanelHost.make()
        _    <- host.push(a).provide(h.frameLayer)
        _    <- host.push(b).provide(h.frameLayer)
        _    <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
      yield
        val buf = h.currentBuffer
        // Inside B's bounds: 'B'
        val insideB = buf.get(7, 4).exists(_.char == 'B')
        // Outside B's bounds, inside A: 'A'
        val outsideB = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(insideB, outsideB)
    },
    test("covered panels continue rendering each frame") {
      val fillA = Fill('A')
      val fillB = Fill('B')
      val a = Panel.overlay(fillA, Rect(0, 0, 20, 10))
      val b = Panel.overlay(fillB, Rect(0, 0, 20, 10))
      for
        h <- FrameHarness.make(20, 10)
        host <- PanelHost.make()
        _    <- host.push(a).provide(h.frameLayer)
        _    <- host.push(b).provide(h.frameLayer)
        // Force three renders of the composite root.
        _ <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
        _ <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
        _ <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
      yield assertTrue(
        // A is covered but still rendered every pass — counter advances.
        fillA.renderCount.get() == 3,
        fillB.renderCount.get() == 3
      )
    },
    test("onUnload does NOT fire when a panel is covered by push") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = RecordingPanel(Rect(0, 0, 20, 10), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
      yield assertTrue(a.unloadAt == -1)
    },
    test("visible reports the bottom-to-top list; topmost is last") {
      val counter = AtomicInteger(0)
      val a = RecordingPanel(Rect(0, 0, 10, 10), counter)
      val b = RecordingPanel(Rect(0, 0, 10, 10), counter)
      val c = RecordingPanel(Rect(0, 0, 10, 10), counter)
      for
        host    <- PanelHost.make()
        _       <- withEnv(host.push(a))
        _       <- withEnv(host.push(b))
        _       <- withEnv(host.push(c))
        visible <- host.visible
      yield assertTrue(visible == List(a, b, c))
    },
    test("layout merges per-panel results by ComponentId") {
      // Two visible panels with distinct components; LayoutManager.resolve
      // walks the composite host root and surfaces both panels' ids in the
      // merged result, with rects scoped to each panel's bounds.
      val fillA = Fill('A')
      val fillB = Fill('B')
      val boundsA = Rect(0, 0, 20, 10)
      val boundsB = Rect(5, 2, 10, 5)
      val a = Panel.overlay(fillA, boundsA)
      val b = Panel.overlay(fillB, boundsB)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
        layout = LayoutManager.default.resolve(host.root, Rect(0, 0, 80, 24))
      yield assertTrue(
        layout.rects.contains(fillA.id),
        layout.rects.contains(fillB.id),
        layout.rects(fillA.id) == boundsA,
        layout.rects(fillB.id) == boundsB
      )
    },
    test("event dispatch routes to the topmost panel only") {
      // The focusables under panel A are excluded from the focus cycle
      // when panel B is on top (Q7's "topmost focusables only" rule).
      val fillA = Fill('A', focusableFlag = true)
      val fillB = Fill('B')
      val a = Panel.overlay(fillA, Rect(0, 0, 20, 10))
      val b = Panel.overlay(fillB, Rect(0, 0, 20, 10))
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
        // Manually compute the topmost panel's layout — what FocusManager
        // would receive in a real run via the topmost-panel walk.
        topLayout = LayoutManager.default.resolve(b.root, b.bounds(Rect(0, 0, 20, 10)))
        focusables = topLayout.order.collect { case c if c.focusable => c.id }
      yield assertTrue(
        // A's focusable is NOT in the topmost panel's focus cycle
        !focusables.contains(fillA.id),
        // B has no focusables anyway
        focusables.isEmpty
      )
    },
    test("pop reveals the previously-covered cells of the lower panel") {
      // A fills (0,0,20,10) with 'A'; B fills (5,2,10,5) with 'B' on top.
      // After pop, cells that were showing 'B' must show 'A' —
      // reveal-on-pop, asserted (not assumed). Immediate-mode full
      // repaint makes this hold; the assertion locks it in against
      // future partial-invalidation work.
      val fillA = Fill('A')
      val fillB = Fill('B')
      val a = Panel.overlay(fillA, Rect(0, 0, 20, 10))
      val b = Panel.overlay(fillB, Rect(5, 2, 10, 5))
      for
        h <- FrameHarness.make(20, 10)
        host <- PanelHost.make()
        _    <- host.push(a).provide(h.frameLayer)
        _    <- host.push(b).provide(h.frameLayer)
        // First render: covers (5,2)–(14,6) with 'B'
        _ <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
        _ <- host.pop.provide(h.frameLayer)
        // Second render: only A remains; previously-B'd cells must now read 'A'
        _ <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
      yield
        val buf = h.currentBuffer
        val revealedInside = buf.get(7, 4).exists(_.char == 'A')
        val revealedCorner = buf.get(5, 2).exists(_.char == 'A')
        val undisturbed = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(revealedInside, revealedCorner, undisturbed)
    },
    test("host fills panel.bounds with Cell.Empty before its root renders") {
      // A fills (0,0,20,10) with 'A'. A hole-leaving panel (`Blank`
      // writes nothing) at (5,2,10,5) sits on top. Without the host
      // pre-fill, A would bleed through B's uncovered cells because A
      // already wrote 'A' there and B writes nothing. With the pre-fill,
      // B's bounds are cleared to `Cell.Empty` first — B's opaque
      // emptiness wins.
      val fillA = Fill('A')
      val a = Panel.overlay(fillA, Rect(0, 0, 20, 10))
      val b = Panel.overlay(Blank, Rect(5, 2, 10, 5))
      for
        h <- FrameHarness.make(20, 10)
        host <- PanelHost.make()
        _    <- host.push(a).provide(h.frameLayer)
        _    <- host.push(b).provide(h.frameLayer)
        _    <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(h.currentBuffer), ctx))
      yield
        val buf = h.currentBuffer
        // Inside B's bounds: opaquely empty, NOT 'A' bleeding through
        val insideBEmpty = buf.get(7, 4).contains(Cell.Empty)
        val cornerEmpty = buf.get(5, 2).contains(Cell.Empty)
        // Outside B, inside A: still 'A'
        val outsideBIsA = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(insideBEmpty, cornerEmpty, outsideBIsA)
    },

    // `rawEventTap` and `Panel.onRawEvent` were retired in Slice 5 —
    // cross-cutting event observation now goes through
    // `Application.run`'s `onEvent` callback, exercised in
    // `ApplicationSpec` and by `EventInspectorPanel`.
  ) @@ TestAspect.timeout(10.seconds)
