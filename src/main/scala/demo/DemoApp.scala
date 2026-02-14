package io.github.wickedsik.wsconsole
package demo

import demo.panels.*
import terminal.Terminal
import zio.ZIO

import java.io.IOException

/**
 * Panel orchestrator with resource management.
 *
 * Uses Terminal's composable resource helpers to guarantee cleanup
 * even on CTRL+C (which ZIO handles as fiber interruption, still executing
 * the release action). The TerminalFactory layer provides additional
 * state restoration on scope closure.
 */
object DemoApp:

  val run: ZIO[Terminal, IOException, Unit] =
    Terminal.withAlternateBuffer {
      Terminal.withHiddenCursor {
        Terminal.clearScreen *> panels
      }
    }

  private val panels: ZIO[Terminal, IOException, Unit] =
    for
      _ <- WelcomePanel.show
      _ <- DemoUtils.pause(4)
      _ <- ColorGalleryPanel.show
      _ <- DemoUtils.pause(5)
      _ <- StyleShowcasePanel.show
      _ <- DemoUtils.pause(4)
      _ <- CursorDemoPanel.show
      _ <- DemoUtils.pause(4)
      _ <- ScrollRegionPanel.show
      _ <- SpinnerPanel.show
      _ <- DemoUtils.pause(2)
      _ <- ProgressBarPanel.show
      _ <- DemoUtils.pause(2)
      _ <- FarewellPanel.show
      _ <- DemoUtils.pause(3)
    yield ()
