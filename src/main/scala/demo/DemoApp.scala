package io.github.wickedsik.wsconsole
package demo

import buffer.Frame
import demo.panels.*
import event.KeyEvent
import terminal.Terminal

import zio.{Duration, ZIO}

import java.io.IOException

/**
 * Panel orchestrator. Layer 6 additions on top of Layer 5's keypress-driven
 * advance:
 *   - `FocusDemoPanel` runs a `RenderLoop` end-to-end with focus + dispatch,
 *     demonstrating the Layer 6 surface in the live demo
 *   - all other panels remain on the Layer 2 `Frame.run` rendering path;
 *     migrating them to component-tree-on-RenderLoop is a follow-up
 *
 * Inherited from Layer 5:
 *   - raw mode acquired alongside alt-buffer + hidden-cursor
 *   - static panels advance on keypress (`waitForKey`), not timer
 *   - animated panels race their animation against the next keypress
 *   - `q` / `Ctrl+C` short-circuit the sequence with state restored on every exit path
 */
object DemoApp:

  /**
   * A demo step yields:
   *   - `Some(key)` if a key advanced the step (caller checks for exit)
   *   - `None` if the step ended naturally (e.g. animation finished, or the
   *     panel handles its own exit semantics)
   */
  private type DemoStep = ZIO[Terminal & Frame, IOException, Option[KeyEvent]]

  /** Static panel: render once, then wait for the next keypress. */
  private def staticStep(panel: ZIO[Frame, IOException, Unit]): DemoStep =
    panel *> DemoUtils.waitForKey.map(Some(_))

  /**
   * Animated panel: race the animation against the next keypress. If the
   * key wins, advance immediately with that key. If the animation wins, the
   * panel becomes static — keep the already-forked key fiber alive so the
   * user paces the transition with a single keypress.
   *
   * Why fork-once: `System.in.read()` is uninterruptible at the JVM level
   * (Thread.interrupt() does not unblock a pending native read on stdin).
   * If we naively re-call `waitForKey` after the animation wins, the
   * previous reader stays blocked on stdin — when the user presses a key,
   * the zombie reader steals the byte and the visible reader keeps
   * waiting, forcing a second keypress to actually advance.
   *
   * Forking once and using `keyFiber.await` (which does not interrupt the
   * underlying fiber when the joining fiber is interrupted) keeps a single
   * reader on stdin for the whole step.
   */
  private def animatedStep(panel: ZIO[Frame, IOException, Unit]): DemoStep =
    for
      keyFiber <- DemoUtils.waitForKey.fork
      outcome  <- panel.raceEither(keyFiber.await)
      key      <- outcome match
                    case Right(exit) => ZIO.done(exit)
                    case Left(_)     => keyFiber.join
    yield Some(key)

  /**
   * Auto-closing panel: render, then race a fixed-duration sleep against the
   * next keypress. Either trigger advances the demo. Used for `FarewellPanel`
   * so the demo exits cleanly without requiring user action.
   */
  private def autoCloseStep(
    panel: ZIO[Frame, IOException, Unit],
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

  /** Layer 6 focus + dispatch demonstration. */
  private val focusStep: DemoStep =
    FocusDemoPanel.show

  private val steps: List[DemoStep] = List(
    staticStep(WelcomePanel.show),
    staticStep(ColorGalleryPanel.show),
    staticStep(StyleShowcasePanel.show),
    staticStep(CursorDemoPanel.show),
    staticStep(LayoutDemoPanel.show),
    inspectorStep,
    focusStep,
    animatedStep(ScrollRegionPanel.show),
    animatedStep(SpinnerPanel.show),
    animatedStep(ProgressBarPanel.show),
    autoCloseStep(FarewellPanel.show, Duration.fromSeconds(3))
  )

  private def runSteps(remaining: List[DemoStep]): ZIO[Terminal & Frame, IOException, Unit] =
    remaining match
      case Nil          => ZIO.unit
      case step :: rest =>
        step.flatMap {
          case Some(k) if DemoUtils.isExitKey(k) => ZIO.unit
          case _                                 => runSteps(rest)
        }

  val run: ZIO[Terminal & Frame, IOException, Unit] =
    ZIO.scoped {
      for
        _ <- ZIO.acquireRelease(Terminal.enterAlternateBuffer)(_ => Terminal.exitAlternateBuffer.ignore)
        _ <- ZIO.acquireRelease(Terminal.hideCursor)(_ => Terminal.showCursor.ignore)
        _ <- ZIO.acquireRelease(Terminal.enterRawMode)(_ => Terminal.exitRawMode.ignore)
        _ <- runSteps(steps)
      yield ()
    }
