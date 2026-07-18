package io.github.wickedsik.wsconsole
package app

import buffer.{BufferManager, Canvas, Cell, Frame}
import component.{Component, RenderContext}
import geometry.Rect
import terminal.Terminal
import testkit.CaptureTerminal

import zio.*
import zio.test.*

import java.io.IOException

object PanelSpec extends ZIOSpecDefault:

  /** Minimal blank component that draws nothing. */
  private object Blank extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /** Pre-filled Frame whose canvas we can inspect after `onUnload` runs. */
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

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Panel")(

    test("default onUnload clears every cell in bounds") {
      for
        pair <- makeFrame(20, 10)
        (frame, mgr) = pair
        // Pre-fill the buffer with X so we can detect the clear.
        _ <- ZIO.succeed {
               for x <- 0 until 20; y <- 0 until 10 do
                 mgr.current.set(x, y, Cell('X'))
             }
        panel = Panel.of(Blank, Rect(2, 1, 5, 3))
        _ <- panel.onUnload.provide(
               CaptureTerminal.layer(),
               ZLayer.succeed[Frame](frame)
             )
      yield
        val buf = mgr.current
        val insideClear =
          (for x <- 2 until 7; y <- 1 until 4 yield buf.get(x, y).exists(_.char == ' ')).forall(identity)
        val outsidePreserved =
          buf.get(0, 0).exists(_.char == 'X') && buf.get(10, 5).exists(_.char == 'X')
        assertTrue(insideClear, outsidePreserved)
    },

    test("override onUnload replaces the default and does not run the clear") {
      val sentinel = new java.util.concurrent.atomic.AtomicBoolean(false)
      val customPanel = new Panel:
        def bounds = Rect(0, 0, 5, 5)
        def root   = Blank
        override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
          ZIO.succeed(sentinel.set(true))

      for
        pair <- makeFrame(10, 10)
        (frame, mgr) = pair
        _ <- ZIO.succeed {
               for x <- 0 until 10; y <- 0 until 10 do
                 mgr.current.set(x, y, Cell('X'))
             }
        _ <- customPanel.onUnload.provide(
               CaptureTerminal.layer(),
               ZLayer.succeed[Frame](frame)
             )
      yield assertTrue(
        sentinel.get(),
        // Cells unchanged: the default fillRect did NOT run.
        mgr.current.get(0, 0).exists(_.char == 'X')
      )
    },

    test("onMount defaults to ZIO.unit") {
      val panel = Panel.of(Blank, Rect(0, 0, 5, 5))
      for
        pair <- makeFrame(10, 10)
        (frame, _) = pair
        _ <- panel.onMount.provide(
               CaptureTerminal.layer(),
               ZLayer.succeed[Frame](frame)
             )
      yield assertCompletes
    },

    test("onRemount defaults to ZIO.unit (Q6)") {
      val panel = Panel.of(Blank, Rect(0, 0, 5, 5))
      for
        pair <- makeFrame(10, 10)
        (frame, _) = pair
        _ <- panel.onRemount.provide(
               CaptureTerminal.layer(),
               ZLayer.succeed[Frame](frame)
             )
      yield assertCompletes
    },

    test("onMount and onRemount are distinct lifecycle phases (Q6)") {
      // A panel that records each invocation separately. The test panel
      // proves that mount-vs-remount are distinct entry points: a fresh
      // panel only fires onMount; revealing it later fires only onRemount.
      val mountCount   = new java.util.concurrent.atomic.AtomicInteger(0)
      val remountCount = new java.util.concurrent.atomic.AtomicInteger(0)
      val panel = new Panel:
        def bounds = Rect(0, 0, 5, 5)
        def root   = Blank
        override def onMount = ZIO.succeed(mountCount.incrementAndGet()).unit
        override def onRemount = ZIO.succeed(remountCount.incrementAndGet()).unit

      for
        pair <- makeFrame(10, 10)
        (frame, _) = pair
        layer = CaptureTerminal.layer() ++ ZLayer.succeed[Frame](frame)
        _ <- panel.onMount.provide(layer)
        _ <- panel.onRemount.provide(layer)
        _ <- panel.onRemount.provide(layer)
      yield assertTrue(
        mountCount.get() == 1,
        remountCount.get() == 2
      )
    }
  ) @@ TestAspect.timeout(10.seconds)
