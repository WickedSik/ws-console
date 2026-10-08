package io.github.wickedsik.wsconsole
package demo.panels

import app.PanelHost
import buffer.{BoxStyle, Frame}
import component.{Button, FocusSnapshot, RenderContext}
import event.KeyEvent.SpecialKey
import event.{EventResult, SpecialKeyCode}
import geometry.Rect
import render.LayoutManager
import terminal.Terminal

import zio.{IO, Scope, ZIO, ZLayer}
import zio.test.*

import java.io.IOException

import testkit.{CaptureTerminal, FrameHarness}
import testkit.RenderHarness.charAt

/**
 * [[ModalDemoPanel]] driven through a real [[app.PanelHost]]: the
 * dialog's placement, the Open / Close round trip, and the duplicate-
 * open guard. Buttons are activated by handing them Enter with a focus
 * snapshot naming them, then running the returned `Perform` effect.
 */
object ModalDemoPanelSpec extends ZIOSpecDefault:

  private val W = 120
  private val H = 21
  private val area = Rect(0, 0, W, H)

  final private case class Env(harness: FrameHarness, host: PanelHost, terminal: Terminal):
    def run[A](f: ZIO[Terminal & Frame, IOException, A]): IO[IOException, A] =
      f.provide(ZLayer.succeed(terminal), harness.frameLayer)

    def buttons: Vector[Button] =
      LayoutManager.default.resolve(host.root, area).order.collect { case b: Button => b }

    def button(label: String): Option[Button] = buttons.find(_.label == label)

    def activate(label: String): IO[IOException, Unit] =
      button(label) match
        case None => ZIO.fail(new IOException(s"no '$label' button in the tree"))
        case Some(b) =>
          b.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), RenderContext(FocusSnapshot(Some(b.id)))) match
            case EventResult.Perform(effect) => effect.provideLayer(harness.frameLayer)
            case other                       => ZIO.fail(new IOException(s"'$label' answered $other"))

  private val mounted: IO[IOException, Env] =
    for
      harness  <- FrameHarness.make(W, H)
      terminal <- CaptureTerminal.make()
      host     <- PanelHost.make()
      panel    <- ModalDemoPanel.make(host, terminal)
      env = Env(harness, host, terminal)
      _ <- env.run(host.push(panel))
    yield env

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ModalDemoPanel")(
    suite("dialogBounds")(
      test("centres the preferred size in a roomy host area") {
        val r = ModalDemoPanel.dialogBounds(Rect(0, 0, 120, 21))
        assertTrue(
          r == Rect((120 - ModalDemoPanel.DialogWidth) / 2, (21 - ModalDemoPanel.DialogHeight) / 2,
            ModalDemoPanel.DialogWidth, ModalDemoPanel.DialogHeight)
        )
      },
      test("respects the host origin") {
        val r = ModalDemoPanel.dialogBounds(Rect(10, 5, 120, 21))
        assertTrue(r.x == 10 + (120 - ModalDemoPanel.DialogWidth) / 2, r.y == 5 + (21 - ModalDemoPanel.DialogHeight) / 2)
      },
      test("clamps to a host smaller than the preferred size") {
        val r = ModalDemoPanel.dialogBounds(Rect(0, 0, 30, 4))
        assertTrue(r == Rect(0, 0, 30, 4))
      }
    ),
    test("before opening, only the opener is in the panel's tree") {
      for env <- mounted
        yield assertTrue(
          env.button("Open modal").isDefined,
          env.button("Close").isEmpty
        )
    },
    test("Open pushes the dialog; it renders centred with its Close button") {
      for
        env   <- mounted
        _     <- env.activate("Open modal")
        stack <- env.host.visible
        _     <- env.harness.run(env.host.root)
      yield
        val r = ModalDemoPanel.dialogBounds(area)
        val buf = env.harness.drawnBuffer
        assertTrue(
          stack.size == 2,
          env.button("Close").isDefined,
          buf.charAt(r.x, r.y).contains(BoxStyle.Double.topLeft),
          buf.charAt(r.x + r.width - 1, r.y + r.height - 1).contains(BoxStyle.Double.bottomRight)
        )
    },
    test("a second Open while the dialog is up does not stack a second copy") {
      for
        env   <- mounted
        _     <- env.activate("Open modal")
        _     <- env.activate("Open modal")
        stack <- env.host.visible
      yield assertTrue(stack.size == 2)
    },
    test("Close pops the dialog and leaves the demo panel on the stack") {
      for
        env   <- mounted
        _     <- env.activate("Open modal")
        _     <- env.activate("Close")
        stack <- env.host.visible
      yield assertTrue(
        stack.size == 1,
        env.button("Close").isEmpty,
        env.button("Open modal").isDefined
      )
    }
  )
