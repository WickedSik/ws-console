package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import app.{Application, Panel as AppPanel}
import buffer.{Attribute, Canvas, CellStyle, Foreground, Frame}
import component.{Component, RenderContext}
import demo.{DemoLayout, DemoUtils}
import event.{Event, EventResult, KeyEvent, KeyModifier}
import event.KeyEvent.{CharKey, SpecialKey}
import geometry.Rect

import zio.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Live event display — every event that reaches the application's
 * `onEvent` hook is recorded here.
 *
 * Layer 7 wiring (target design, §6.2 "cross-cutting policy"):
 *   - `EventInspectorPanel.make` returns an [[EventInspector]] carrying
 *     the panel and an `observe` callback.
 *   - The consumer composes `observe` into `Application.run`'s
 *     `onEvent` — every event, including those a component answered
 *     with `Perform` / `RequestRedraw` / `Consumed`, reaches the
 *     inspector along with its dispatch result.
 *   - Recording is unconditional: the log keeps rolling even while the
 *     panel is invisible, so the last N events are already on screen
 *     the moment the user reveals the panel.
 *
 * The panel replaces the retired `Panel.onRawEvent` side channel — a
 * component observing events without consuming them is now expressed
 * directly on the answer channel.
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

  /**
   * Bundle of the panel and its consumer-side hook.
   *
   * `panel` goes into the `PanelHost` panel stack; `observe` is
   * composed into `Application.run`'s `onEvent` so the inspector
   * records every event the application receives.
   */
  final case class EventInspector(
    panel:   AppPanel,
    observe: (Event, EventResult) => UIO[Unit]
  )

  /**
   * Build an inspector bound to `app.requestRedraw` — each recorded
   * event triggers a redraw so the visible log stays fresh.
   */
  def make(app: Application): UIO[EventInspector] =
    for
      cache <- ZIO.succeed(new AtomicReference[Vector[String]](Vector.empty))
    yield
      val panel = new AppPanel:
        def bounds: Rect      = EventInspectorPanel.bounds
        def root:   Component = inspectorComponent(cache)

      val observe: (Event, EventResult) => UIO[Unit] =
        (event, _) => event match
          case k: KeyEvent =>
            ZIO.succeed(cache.updateAndGet(log => appendBounded(log, formatKey(k)))).unit *>
              app.requestRedraw
          case _ =>
            ZIO.unit

      EventInspector(panel, observe)

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
    canvas.putText(2, 4, "Every event reaching Application.onEvent — including 'q' and Ctrl+C", helpStyle)
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
