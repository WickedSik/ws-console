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
        def width: Int = mgr.current.width
        def height: Int = mgr.current.height
        def canvas: Canvas = Canvas(mgr.current)
        def render: IO[IOException, Unit] = ZIO.unit
        def clear: UIO[Unit] = ZIO.succeed(mgr.current.clearCells())
        def clearScreen: IO[IOException, Unit] = ZIO.succeed(mgr.previous.clearCells())
        def invalidate: UIO[Unit] = ZIO.succeed(mgr.invalidatePrevious())
        def resize(w: Int, h: Int): IO[IOException, Unit] = ZIO.unit
      (frame, mgr)
    }

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Panel")(
    test("default bounds fills whatever host area is granted") {
      val panel = Panel.of(Blank)
      val hostArea = Rect(3, 7, 40, 12)
      assertTrue(panel.bounds(hostArea) == hostArea)
    },
    test("overlay bounds ignore host area and use the fixed rect") {
      val fixed = Rect(2, 1, 5, 3)
      val panel = Panel.overlay(Blank, fixed)
      val hostArea = Rect(0, 0, 80, 24)
      assertTrue(panel.bounds(hostArea) == fixed)
    },
    test("default onUnload is a no-op — cells stay unchanged") {
      val panel = Panel.of(Blank)
      for
        pair <- makeFrame(10, 10)
        (frame, mgr) = pair
        _ <- ZIO.succeed {
          for x <- 0 until 10; y <- 0 until 10 do
            mgr.current.set(x, y, Cell('X'))
        }
        _ <- panel.onUnload.provide(
          CaptureTerminal.layer(),
          ZLayer.succeed[Frame](frame)
        )
      yield
        // Every cell survives — the default onUnload writes nothing.
        val allX = (for x <- 0 until 10; y <- 0 until 10 yield mgr.current.get(x, y).exists(_.char == 'X'))
          .forall(identity)
        assertTrue(allX)
    },
    test("Panel.clearBounds helper clears the requested rect and preserves outside") {
      for
        pair <- makeFrame(20, 10)
        (frame, mgr) = pair
        _ <- ZIO.succeed {
          for x <- 0 until 20; y <- 0 until 10 do
            mgr.current.set(x, y, Cell('X'))
        }
        _ <- Panel.clearBounds(Rect(2, 1, 5, 3)).provide(
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
    test("override onUnload runs the custom effect instead of the default no-op") {
      val sentinel = new java.util.concurrent.atomic.AtomicBoolean(false)
      val customPanel = new Panel:
        def root = Blank
        override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
          ZIO.succeed(sentinel.set(true))

      for
        pair <- makeFrame(10, 10)
        (frame, _) = pair
        _ <- customPanel.onUnload.provide(
          CaptureTerminal.layer(),
          ZLayer.succeed[Frame](frame)
        )
      yield assertTrue(sentinel.get())
    },
    test("onMount defaults to ZIO.unit") {
      val panel = Panel.of(Blank)
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
      val panel = Panel.of(Blank)
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
      val mountCount = new java.util.concurrent.atomic.AtomicInteger(0)
      val remountCount = new java.util.concurrent.atomic.AtomicInteger(0)
      val panel = new Panel:
        def root = Blank
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
