package io.github.wickedsik.wsconsole
package demo

import buffer.Renderer
import demo.panels.*
import event.KeyEvent
import terminal.Terminal

import zio.{Duration, ZIO}

import java.io.IOException

/**
 * Panel orchestrator. Layer 5 migration:
 *   - raw mode is acquired alongside alt-buffer + hidden-cursor
 *   - static panels advance on keypress (`waitForKey`), not timer
 *   - animated panels race their animation against the next keypress;
 *     either finishing advances the demo
 *   - `q` and `Ctrl+C` (parsed as `CharKey('c', Set(Ctrl))` in raw mode)
 *     short-circuit the panel sequence; release actions still fire and
 *     restore terminal state
 *
 * The for-comprehension is replaced by a list of `DemoStep` values folded
 * with early termination - the diff against the timer-driven version is
 * the load-bearing artefact of Layer 5.
 */
object DemoApp:

  /**
   * A demo step yields:
   *   - `Some(key)` if a key advanced the step (caller checks for exit)
   *   - `None` if the step ended naturally (e.g. animation finished, or the
   *     panel handles its own exit semantics)
   */
  private type DemoStep = ZIO[Terminal & Renderer, IOException, Option[KeyEvent]]

  /** Static panel: render once, then wait for the next keypress. */
  private def staticStep(panel: ZIO[Renderer, IOException, Unit]): DemoStep =
    panel *> DemoUtils.waitForKey.map(Some(_))

  /**
   * Animated panel: race the animation against the next keypress. If the
   * key wins, advance immediately with that key. If the animation wins, the
   * panel becomes static - block on a fresh `waitForKey` so the user paces
   * the transition, matching the static-panel UX.
   */
  private def animatedStep(panel: ZIO[Renderer, IOException, Unit]): DemoStep =
    panel.raceEither(DemoUtils.waitForKey).flatMap {
      case Right(k) => ZIO.succeed(Some(k))
      case Left(_)  => DemoUtils.waitForKey.map(Some(_))
    }

  /**
   * Auto-closing panel: render, then race a fixed-duration sleep against the
   * next keypress. Either trigger advances the demo. Used for `FarewellPanel`
   * so the demo exits cleanly without requiring user action.
   */
  private def autoCloseStep(
    panel: ZIO[Renderer, IOException, Unit],
    after: Duration
  ): DemoStep =
    for
      _      <- panel
      result <- ZIO.sleep(after).raceEither(DemoUtils.waitForKey)
    yield result match
      case Right(k) => Some(k)
      case Left(_)  => None

  /** Inspector panel: handles its own event consumption and exit semantics. */
  private val inspectorStep: DemoStep =
    EventInspectorPanel.show

  private val steps: List[DemoStep] = List(
    staticStep(WelcomePanel.show),
    staticStep(ColorGalleryPanel.show),
    staticStep(StyleShowcasePanel.show),
    staticStep(CursorDemoPanel.show),
    staticStep(LayoutDemoPanel.show),
    inspectorStep,
    animatedStep(ScrollRegionPanel.show),
    animatedStep(SpinnerPanel.show),
    animatedStep(ProgressBarPanel.show),
    autoCloseStep(FarewellPanel.show, Duration.fromSeconds(3))
  )

  private def runSteps(remaining: List[DemoStep]): ZIO[Terminal & Renderer, IOException, Unit] =
    remaining match
      case Nil          => ZIO.unit
      case step :: rest =>
        step.flatMap {
          case Some(k) if DemoUtils.isExitKey(k) => ZIO.unit
          case _                                 => runSteps(rest)
        }

  val run: ZIO[Terminal & Renderer, IOException, Unit] =
    ZIO.scoped {
      for
        _ <- ZIO.acquireRelease(Terminal.enterAlternateBuffer)(_ => Terminal.exitAlternateBuffer.ignore)
        _ <- ZIO.acquireRelease(Terminal.hideCursor)(_ => Terminal.showCursor.ignore)
        _ <- ZIO.acquireRelease(Terminal.enterRawMode)(_ => Terminal.exitRawMode.ignore)
        _ <- runSteps(steps)
      yield ()
    }
