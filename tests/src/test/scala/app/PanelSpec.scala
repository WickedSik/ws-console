package io.github.wickedsik.wsconsole
package app

import buffer.{Canvas, Cell, Frame}
import component.{Component, RenderContext}
import geometry.Rect
import terminal.Terminal
import testkit.{CaptureTerminal, FrameHarness}

import zio.*
import zio.test.*

import java.io.IOException

object PanelSpec extends ZIOSpecDefault:

  /** Minimal blank component that draws nothing. */
  private object Blank extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

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
        h <- FrameHarness.make(10, 10)
        _ <- ZIO.succeed {
               for x <- 0 until 10; y <- 0 until 10 do
                 h.currentBuffer.set(x, y, Cell('X'))
             }
        _ <- panel.onUnload.provide(
               CaptureTerminal.layer(),
               h.frameLayer
             )
      yield
        // Every cell survives — the default onUnload writes nothing.
        val allX = (for x <- 0 until 10; y <- 0 until 10 yield h.currentBuffer.get(x, y).exists(_.char == 'X'))
          .forall(identity)
        assertTrue(allX)
    },
    test("Panel.clearBounds helper clears the requested rect and preserves outside") {
      for
        h <- FrameHarness.make(20, 10)
        _ <- ZIO.succeed {
               for x <- 0 until 20; y <- 0 until 10 do
                 h.currentBuffer.set(x, y, Cell('X'))
             }
        _ <- Panel.clearBounds(Rect(2, 1, 5, 3)).provide(
               h.frameLayer
             )
      yield
        val buf = h.currentBuffer
        val insideClear =
          (for x <- 2 until 7; y <- 1 until 4 yield buf.get(x, y).exists(_.char == ' ')).forall(identity)
        val outsidePreserved =
          buf.get(0, 0).exists(_.char == 'X') && buf.get(10, 5).exists(_.char == 'X')
        assertTrue(insideClear, outsidePreserved)
    },
    test("override onUnload runs the custom effect instead of the default no-op") {
      val sentinel = java.util.concurrent.atomic.AtomicBoolean(false)
      val customPanel = new Panel:
        def root = Blank
        override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
          ZIO.succeed(sentinel.set(true))

      for
        h <- FrameHarness.make(10, 10)
        _ <- customPanel.onUnload.provide(
               CaptureTerminal.layer(),
               h.frameLayer
             )
      yield assertTrue(sentinel.get())
    },
    test("onMount defaults to ZIO.unit") {
      val panel = Panel.of(Blank)
      for
        h <- FrameHarness.make(10, 10)
        _ <- panel.onMount.provide(
               CaptureTerminal.layer(),
               h.frameLayer
             )
      yield assertCompletes
    },
    test("onRemount defaults to ZIO.unit (Q6)") {
      val panel = Panel.of(Blank)
      for
        h <- FrameHarness.make(10, 10)
        _ <- panel.onRemount.provide(
               CaptureTerminal.layer(),
               h.frameLayer
             )
      yield assertCompletes
    },
    test("onMount and onRemount are distinct lifecycle phases (Q6)") {
      val mountCount = java.util.concurrent.atomic.AtomicInteger(0)
      val remountCount = java.util.concurrent.atomic.AtomicInteger(0)
      val panel = new Panel:
        def root = Blank
        override def onMount = ZIO.succeed(mountCount.incrementAndGet()).unit
        override def onRemount = ZIO.succeed(remountCount.incrementAndGet()).unit

      for
        h <- FrameHarness.make(10, 10)
        layer = CaptureTerminal.layer() ++ h.frameLayer
        _ <- panel.onMount.provide(layer)
        _ <- panel.onRemount.provide(layer)
        _ <- panel.onRemount.provide(layer)
      yield assertTrue(
        mountCount.get() == 1,
        remountCount.get() == 2
      )
    }
  ) @@ TestAspect.timeout(10.seconds)
