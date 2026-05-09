package io.github.wickedsik.wsconsole
package demo

import buffer.Renderer
import demo.panels.*
import terminal.Terminal

import zio.ZIO

import java.io.IOException

/**
 * Panel orchestrator with resource management.
 *
 * All panels render through the Layer 2 Renderer/Canvas pipeline. Scroll
 * regions are first-class buffer state, expressed via `Canvas.scrollRegion`
 * and the `ScrollableCanvas` handle — no panel reaches for `Terminal` or
 * `AnsiBuilder` directly.
 *
 * Resource management uses ZIO.scoped + ZIO.acquireRelease directly rather
 * than Terminal.withAlternateBuffer / withHiddenCursor — the helper methods'
 * `R <: Terminal` upper bound interacts badly with intersection environments
 * (Terminal & Renderer) at runtime.
 */
object DemoApp:
  private val panels: ZIO[Terminal & Renderer, IOException, Unit] =
    for
      _ <- WelcomePanel.show
      _ <- DemoUtils.pause(4)
      _ <- ColorGalleryPanel.show
      _ <- DemoUtils.pause(5)
      _ <- StyleShowcasePanel.show
      _ <- DemoUtils.pause(4)
      _ <- CursorDemoPanel.show
      _ <- DemoUtils.pause(4)
      _ <- LayoutDemoPanel.show
      _ <- DemoUtils.pause(4)
      _ <- ScrollRegionPanel.show
      _ <- SpinnerPanel.show
      _ <- DemoUtils.pause(2)
      _ <- ProgressBarPanel.show
      _ <- DemoUtils.pause(2)
      _ <- FarewellPanel.show
      _ <- DemoUtils.pause(3)
    yield ()

  val run: ZIO[Terminal & Renderer, IOException, Unit] =
    ZIO.scoped {
      for
        _ <- ZIO.acquireRelease(Terminal.enterAlternateBuffer)(_ => Terminal.exitAlternateBuffer.ignore)
        _ <- ZIO.acquireRelease(Terminal.hideCursor)(_ => Terminal.showCursor.ignore)
        _ <- panels
      yield ()
    }