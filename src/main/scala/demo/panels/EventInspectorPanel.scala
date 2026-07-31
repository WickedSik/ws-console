package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Application, Panel as AppPanel, State}
import buffer.{Attribute, Canvas, CellStyle, Foreground, Frame}
import component.{Component, RenderContext}
import demo.{DemoLayout, DemoUtils}
import event.{Event, KeyEvent, KeyModifier}
import event.KeyEvent.{CharKey, SpecialKey}
import geometry.Rect
import terminal.Terminal

import zio.*
import zio.stream.ZStream

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Live event display — the WI-3 State-activation demo.
 *
 * Layer 7 wiring:
 *   - `Panel.onRawEvent` opt-in tap receives every event **before**
 *     `quitOn` absorption, so `q` and `Ctrl+C` appear in the log
 *     before the framework consumes them. The tap writes new lines
 *     into a [[State]] `[Vector[String]]`.
 *   - `onMount` forks a drain fiber that subscribes to the State
 *     (`state.subscribeScoped` — deterministic registration) and
 *     mirrors the current log into an `AtomicReference` cache while
 *     calling `Application.requestRedraw`.
 *   - The `Component` renders synchronously from the cache.
 *   - `onUnload` interrupts the drain fiber and clears bounds.
 *
 * The `State` → drain → `requestRedraw` chain is the canonical
 * state-invalidation source (ADR-003 source 1), demonstrated here on
 * live event content.
 */
object EventInspectorPanel:

  val bounds: Rect      = DemoLayout.contentBounds
  private val MaxLines  = 14

  private val titleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold, Attribute.Underline))
  private val helpStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Dim))
  private val eventStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite))
  private val emptyStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Italic, Attribute.Dim))

  /** Construct an event-inspector panel. Requires `Application` for the redraw signal. */
  def make(app: Application): UIO[AppPanel] =
    for
      state    <- State.make[Vector[String]](Vector.empty)
      cache    <- ZIO.succeed(new AtomicReference[Vector[String]](Vector.empty))
      fiberRef <- Ref.make[Option[Fiber.Runtime[?, ?]]](None)
    yield new AppPanel:
      def bounds: Rect      = EventInspectorPanel.bounds
      def root:   Component = inspectorComponent(cache)

      override def onRawEvent: Option[Event => ZIO[Terminal & Frame, IOException, Boolean]] =
        Some { event =>
          event match
            case k: KeyEvent =>
              state.update(log => appendBounded(log, formatKey(k))).as(true)
            case _ =>
              ZIO.succeed(true)  // ignore non-key events for display
        }

      override def onMount: ZIO[Terminal & Frame, IOException, Unit] =
        val drain =
          ZIO.scoped {
            state.subscribeScoped.flatMap { dq =>
              ZStream.fromQueue(dq).foreach { newLog =>
                ZIO.succeed(cache.set(newLog)) *> app.requestRedraw
              }
            }
          }
        for
          fiber <- drain.fork
          _     <- fiberRef.set(Some(fiber))
        yield ()

      override def onUnload: ZIO[Terminal & Frame, IOException, Unit] =
        for
          fiberOpt <- fiberRef.get
          _        <- fiberOpt.fold(ZIO.unit)(_.interrupt)
          _        <- AppPanel.clearBounds(bounds)
        yield ()

  private def inspectorComponent(cache: AtomicReference[Vector[String]]): Component =
    new Component:
      def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
        renderLog(canvas, cache.get())

  /**
   * Pure render seam: draw the header + help text + event log for the
   * supplied snapshot. Package-private so tests can render a specific
   * log directly, without forking fibers.
   */
  private[panels] def renderLog(canvas: Canvas, log: Vector[String]): Unit =
    DemoUtils.drawHeader(canvas, "Event Inspector")
    canvas.putText(2, 4, "All events captured — including q and Ctrl+C", helpStyle)
    canvas.putText(2, 6, "Events received:", titleStyle)
    if log.isEmpty then
      canvas.putText(4, 8, "(awaiting input...)", emptyStyle)
    else
      log.takeRight(MaxLines).zipWithIndex.foreach { case (line, i) =>
        canvas.putText(4, 8 + i, line, eventStyle)
      }

  private def appendBounded(log: Vector[String], line: String): Vector[String] =
    val updated = log :+ line
    if updated.length > MaxLines then updated.takeRight(MaxLines) else updated

  private def formatKey(key: KeyEvent): String =
    key match
      case CharKey(c, mods) =>
        val display = c match
          case ' '  => "' '"
          case '\t' => "'\\t'"
          case _    => s"'$c'"
        s"CharKey $display ${formatMods(mods)}"
      case SpecialKey(k, mods) =>
        s"SpecialKey $k ${formatMods(mods)}"

  private def formatMods(mods: Set[KeyModifier]): String =
    if mods.isEmpty then "[]"
    else mods.toList.sortBy(_.ordinal).mkString("[", "+", "]")
