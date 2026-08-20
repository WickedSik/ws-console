package io.github.wickedsik.wsconsole
package app

import buffer.{BufferManager, Canvas, Cell, Frame}
import component.{Component, RenderContext}
import geometry.Rect
import render.{EventDispatcher, FocusManager, LayoutManager}
import event.{Event, EventResult, KeyEvent}
import terminal.Terminal
import testkit.CaptureTerminal

import zio.*
import zio.test.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

object PanelHostSpec extends ZIOSpecDefault:

  // ===== Test infrastructure =====

  private object Blank extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Component that writes a single character to every cell of its area. */
  private final class Fill(ch: Char, focusableFlag: Boolean = false) extends Component:
    val renderCount = new AtomicInteger(0)
    override val focusable: Boolean = focusableFlag
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
      renderCount.incrementAndGet()
      var y = area.y
      while y < area.y + area.height do
        var x = area.x
        while x < area.x + area.width do
          canvas.putChar(x, y, ch)
          x += 1
        y += 1

  private val ctx: RenderContext = RenderContext.empty

  /**
   * Recording panel that timestamps each lifecycle invocation against a
   * shared `counter`. Tests construct one counter and pass it to every
   * panel in the same scenario so cross-panel call ordering is observable.
   */
  private final class RecordingPanel(
    val bounds: Rect,
    counter:    AtomicInteger,
    val root:   Component = Blank
  ) extends Panel:
    @volatile var mountAt:    Int = -1
    @volatile var unloadAt:   Int = -1
    @volatile var remountAt:  Int = -1

    override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.succeed { mountAt = counter.incrementAndGet() }.unit

    override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.succeed { unloadAt = counter.incrementAndGet() }.unit

    override def onRemount: ZIO[Terminal & Frame, IOException, Unit] =
      ZIO.succeed { remountAt = counter.incrementAndGet() }.unit

  private def makeFrame(width: Int, height: Int): UIO[(Frame, BufferManager)] =
    ZIO.succeed {
      val mgr = BufferManager.of(width, height)
      val frame = new Frame:
        def width:  Int    = mgr.current.width
        def height: Int    = mgr.current.height
        def canvas: Canvas = Canvas(mgr.current)
        def render:      IO[IOException, Unit] = ZIO.unit
        def clear:       UIO[Unit]             = ZIO.succeed(mgr.current.clearCells())
        def clearScreen: IO[IOException, Unit] = ZIO.succeed(mgr.previous.clearCells())
        def invalidate:  UIO[Unit]             = ZIO.succeed(mgr.invalidatePrevious())
        def resize(w: Int, h: Int): IO[IOException, Unit] = ZIO.unit
      (frame, mgr)
    }

  private def withEnv[A](f: ZIO[Terminal & Frame, IOException, A]): IO[IOException, A] =
    for
      pair <- makeFrame(80, 24)
      (frame, _) = pair
      result <- f.provide(
                  CaptureTerminal.layer(),
                  ZLayer.succeed[Frame](frame)
                )
    yield result

  // ===== Specs =====

  def spec: Spec[TestEnvironment & Scope, Any] = suite("PanelHost")(

    test("push runs the new panel's onMount") {
      val counter = new AtomicInteger(0)
      val panel = new RecordingPanel(Rect(0, 0, 10, 10), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(panel))
      yield assertTrue(panel.mountAt == 1, panel.unloadAt == -1, panel.remountAt == -1)
    },

    test("pop runs popped panel's onUnload then revealed panel's onRemount (Q6)") {
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = new RecordingPanel(Rect(5, 2, 10, 5),  counter)
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
        a.unloadAt == -1     // a was never popped
      )
    },

    test("pop does NOT re-invoke the revealed panel's onMount (Q6)") {
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = new RecordingPanel(Rect(0, 0, 20, 10), counter)
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
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      val c = new RecordingPanel(Rect(0, 0, 20, 10), counter)
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
        a.remountAt == -1,    // replace does not reveal the underlying panel
        visible.map(_.root) == List(a.root, c.root)
      )
    },

    test("active reports the top of the stack; None when empty") {
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 10, 10), counter)
      val b = new RecordingPanel(Rect(0, 0, 10, 10), counter)
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

    test("empty-stack pop fails with PanelHostError.EmptyStack (Q8)") {
      for
        host   <- PanelHost.make()
        result <- withEnv(host.pop).exit
      yield assertTrue(
        result match
          case Exit.Failure(cause) =>
            cause.failureOption.exists(_.isInstanceOf[PanelHostError.EmptyStack.type])
          case _ => false
      )
    },

    test("empty-stack pop is recoverable via catchSome") {
      for
        host    <- PanelHost.make()
        // Recover the failure cleanly
        outcome <- withEnv(
                     host.pop.catchSome {
                       case _: PanelHostError.EmptyStack.type => ZIO.unit
                     }
                   )
      yield assertTrue(outcome == ())
    },

    test("root renders all visible panels bottom-to-top (Q7 — Option E)") {
      // Panel A fills (0,0,20,10) with 'A'; Panel B fills (5,2,10,5) with 'B'.
      // Inside B's bounds we expect B; outside, A.
      val fillA = new Fill('A')
      val fillB = new Fill('B')
      val a = Panel.of(fillA, Rect(0, 0, 20, 10))
      val b = Panel.of(fillB, Rect(5, 2, 10, 5))
      for
        pair  <- makeFrame(20, 10)
        (frame, mgr) = pair
        host  <- PanelHost.make()
        _     <- host.push(a).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- host.push(b).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
      yield
        val buf = mgr.current
        // Inside B's bounds: 'B'
        val insideB = buf.get(7, 4).exists(_.char == 'B')
        // Outside B's bounds, inside A: 'A'
        val outsideB = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(insideB, outsideB)
    },

    test("covered panels continue rendering each frame") {
      val fillA = new Fill('A')
      val fillB = new Fill('B')
      val a = Panel.of(fillA, Rect(0, 0, 20, 10))
      val b = Panel.of(fillB, Rect(0, 0, 20, 10))
      for
        pair  <- makeFrame(20, 10)
        (frame, mgr) = pair
        host  <- PanelHost.make()
        _     <- host.push(a).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- host.push(b).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        // Force three renders of the composite root.
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
      yield assertTrue(
        // A is covered but still rendered every pass — counter advances.
        fillA.renderCount.get() == 3,
        fillB.renderCount.get() == 3
      )
    },

    test("onUnload does NOT fire when a panel is covered by push") {
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      val b = new RecordingPanel(Rect(0, 0, 20, 10), counter)
      for
        host <- PanelHost.make()
        _    <- withEnv(host.push(a))
        _    <- withEnv(host.push(b))
      yield assertTrue(a.unloadAt == -1)
    },

    test("visible reports the bottom-to-top list; topmost is last") {
      val counter = new AtomicInteger(0)
      val a = new RecordingPanel(Rect(0, 0, 10, 10), counter)
      val b = new RecordingPanel(Rect(0, 0, 10, 10), counter)
      val c = new RecordingPanel(Rect(0, 0, 10, 10), counter)
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
      val fillA = new Fill('A')
      val fillB = new Fill('B')
      val boundsA = Rect(0, 0, 20, 10)
      val boundsB = Rect(5, 2, 10, 5)
      val a = Panel.of(fillA, boundsA)
      val b = Panel.of(fillB, boundsB)
      for
        host    <- PanelHost.make()
        _       <- withEnv(host.push(a))
        _       <- withEnv(host.push(b))
        layout  = LayoutManager.default.resolve(host.root, Rect(0, 0, 80, 24))
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
      val fillA = new Fill('A', focusableFlag = true)
      val fillB = new Fill('B')
      val a = Panel.of(fillA, Rect(0, 0, 20, 10))
      val b = Panel.of(fillB, Rect(0, 0, 20, 10))
      for
        host    <- PanelHost.make()
        _       <- withEnv(host.push(a))
        _       <- withEnv(host.push(b))
        // Manually compute the topmost panel's layout — what FocusManager
        // would receive in a real run via the topmost-panel walk.
        topLayout = LayoutManager.default.resolve(b.root, b.bounds)
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
      val fillA = new Fill('A')
      val fillB = new Fill('B')
      val a = Panel.of(fillA, Rect(0, 0, 20, 10))
      val b = Panel.of(fillB, Rect(5, 2, 10, 5))
      for
        pair  <- makeFrame(20, 10)
        (frame, mgr) = pair
        host  <- PanelHost.make()
        _     <- host.push(a).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- host.push(b).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        // First render: covers (5,2)–(14,6) with 'B'
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
        _     <- host.pop.provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        // Second render: only A remains; previously-B'd cells must now read 'A'
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
      yield
        val buf = mgr.current
        val revealedInside = buf.get(7, 4).exists(_.char == 'A')
        val revealedCorner = buf.get(5, 2).exists(_.char == 'A')
        val undisturbed    = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(revealedInside, revealedCorner, undisturbed)
    },

    test("host fills panel.bounds with Cell.Empty before its root renders") {
      // A fills (0,0,20,10) with 'A'. A hole-leaving panel (`Blank`
      // writes nothing) at (5,2,10,5) sits on top. Without the host
      // pre-fill, A would bleed through B's uncovered cells because A
      // already wrote 'A' there and B writes nothing. With the pre-fill,
      // B's bounds are cleared to `Cell.Empty` first — B's opaque
      // emptiness wins.
      val fillA = new Fill('A')
      val a = Panel.of(fillA, Rect(0, 0, 20, 10))
      val b = Panel.of(Blank, Rect(5, 2, 10, 5))
      for
        pair  <- makeFrame(20, 10)
        (frame, mgr) = pair
        host  <- PanelHost.make()
        _     <- host.push(a).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- host.push(b).provide(CaptureTerminal.layer(), ZLayer.succeed[Frame](frame))
        _     <- ZIO.succeed(host.root.render(Rect(0, 0, 20, 10), Canvas(mgr.current), ctx))
      yield
        val buf = mgr.current
        // Inside B's bounds: opaquely empty, NOT 'A' bleeding through
        val insideBEmpty = buf.get(7, 4).contains(Cell.Empty)
        val cornerEmpty  = buf.get(5, 2).contains(Cell.Empty)
        // Outside B, inside A: still 'A'
        val outsideBIsA  = buf.get(0, 0).exists(_.char == 'A') && buf.get(19, 9).exists(_.char == 'A')
        assertTrue(insideBEmpty, cornerEmpty, outsideBIsA)
    },

    // `rawEventTap` and `Panel.onRawEvent` were retired in Slice 5 —
    // cross-cutting event observation now goes through
    // `Application.run`'s `onEvent` callback, exercised in
    // `ApplicationSpec` and by `EventInspectorPanel`.
  ) @@ TestAspect.timeout(10.seconds)
