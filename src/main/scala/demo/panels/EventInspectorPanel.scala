package io.github.wickedsik.wsconsole
package demo.panels

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, Frame}
import demo.DemoUtils
import event.{KeyEvent, KeyModifier}
import event.KeyEvent.{CharKey, SpecialKey}
import terminal.Terminal

import zio.{Duration, Ref, ZIO}

import java.io.IOException

/**
 * Live event display - the Layer 5 showcase panel.
 *
 * Renders incoming `KeyEvent`s as a scrollable log; demonstrates the parser
 * surface visibly. Advances on `q` or after a 10s timeout. `Ctrl+C` is
 * propagated as an exit signal so the demo exits cleanly.
 */
object EventInspectorPanel:

  private val MaxLines    = 14
  private val WatchWindow = Duration.fromSeconds(10)

  private val titleStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightCyan), attributes = Set(Attribute.Bold, Attribute.Underline))

  private val helpStyle =
    CellStyle(fg = Foreground.Named(FgColor.White), attributes = Set(Attribute.Dim))

  private val eventStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite))

  private val emptyStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightBlack), attributes = Set(Attribute.Italic, Attribute.Dim))

  /**
   * Run the inspector. Returns:
   *   - `Some(key)` if the user pressed `Ctrl+C` (caller should exit the demo)
   *   - `None` for natural completion (`q` pressed or 10s elapsed)
   */
  def show: ZIO[Terminal & Frame, IOException, Option[KeyEvent]] =
    for
      logRef  <- Ref.make(Vector.empty[String])
      _       <- redraw(Vector.empty)
      lastOpt <- Terminal.events
                   .collect { case k: KeyEvent => k }
                   .mapZIO { key =>
                     for
                       log <- logRef.updateAndGet(appendBounded(_, formatEvent(key)))
                       _   <- redraw(log)
                     yield key
                   }
                   .takeUntil(DemoUtils.isExitKey)
                   .haltWhen(ZIO.sleep(WatchWindow))
                   .runLast
    yield lastOpt match
      case Some(k) if isCtrlC(k) => Some(k)
      case _                     => None

  private def redraw(log: Vector[String]): ZIO[Frame, IOException, Unit] =
    Frame.run { canvas =>
      DemoUtils.drawHeader(canvas, "Event Inspector")
      canvas.putText(2, 4, "Press q to exit | Ctrl+C to quit | Auto-advance in 10s", helpStyle)
      canvas.putText(2, 6, "Events received:", titleStyle)
      if log.isEmpty then
        canvas.putText(4, 8, "(awaiting input...)", emptyStyle)
      else
        log.takeRight(MaxLines).zipWithIndex.foreach { case (line, i) =>
          canvas.putText(4, 8 + i, line, eventStyle)
        }
    }

  private def appendBounded(log: Vector[String], line: String): Vector[String] =
    val updated = log :+ line
    if updated.length > MaxLines then updated.takeRight(MaxLines) else updated

  private def formatEvent(key: KeyEvent): String =
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

  private def isCtrlC(k: KeyEvent): Boolean =
    k match
      case CharKey('c', mods) if mods(KeyModifier.Ctrl) => true
      case _                                            => false
