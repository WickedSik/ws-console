package io.github.wickedsik.wsconsole
package demo

import ansi.AnsiBuilder
import demo.panels.*
import zio.ZIO

import java.io.IOException

/**
 * Panel orchestrator with resource management.
 *
 * Uses ZIO.acquireRelease in a scoped region to guarantee terminal cleanup
 * even on CTRL+C (which ZIO handles as fiber interruption, still executing
 * the release action).
 */
object DemoApp:

  val run: ZIO[Any, IOException, Unit] =
    ZIO.scoped {
      ZIO.acquireRelease(setup)(_ => cleanup) *> panels
    }

  private val setup: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .enterAltBuffer
        .hideCursor
        .clearScreen
        .home
    )

  private val cleanup: ZIO[Any, Nothing, Unit] =
    DemoUtils.printAnsi(
      AnsiBuilder()
        .resetScrollRegion
        .showCursor
        .exitAltBuffer
    )

  private val panels: ZIO[Any, IOException, Unit] =
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
