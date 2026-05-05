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
 * Layer 2 migration: most panels render through the Renderer/Canvas pipeline.
 * ScrollRegionPanel deliberately stays on direct Layer 1 ANSI because its
 * subject — terminal-native scroll regions (DECSTBM) — cannot be expressed
 * through the buffer abstraction without losing what the panel demonstrates.
 *
 * Panel order: static introductions → static showcases → animated demos →
 * static farewell. ScrollRegionPanel sits with the animated group.
 *
 * Resource management uses ZIO.scoped + ZIO.acquireRelease directly rather
 * than Terminal.withAlternateBuffer / withHiddenCursor — the helper methods'
 * `R <: Terminal` upper bound interacts badly with intersection environments
 * (Terminal & Renderer) at runtime.
 */
object DemoApp:
  private val panels: ZIO[Terminal & Renderer, IOException, Unit] =
    for
      // Static panels
      _ <- WelcomePanel.show
      _ <- DemoUtils.pause(4)
      _ <- ColorGalleryPanel.show
      _ <- DemoUtils.pause(5)
      _ <- StyleShowcasePanel.show
      _ <- DemoUtils.pause(4)
      _ <- CursorDemoPanel.show
      _ <- DemoUtils.pause(4)
      // Animated panels — ScrollRegionPanel uses Layer 1 directly
      _ <- ScrollRegionPanel.show
      _ <- SpinnerPanel.show
      _ <- DemoUtils.pause(2)
      _ <- ProgressBarPanel.show
      _ <- DemoUtils.pause(2)
      // Static farewell
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