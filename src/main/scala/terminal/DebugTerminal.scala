package io.github.wickedsik.wsconsole
package terminal

import ansi.AnsiBuilder
import event.{Event, TerminalEvents}
import zio.*
import zio.stream.ZStream

import java.io.{IOException, PrintWriter}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Transparent wrapper around a `Terminal` that mirrors every operation
 * to a structured log file. Used to diagnose ANSI emission issues that
 * are not reproducible in unit tests — e.g. host-process (SBT) injection
 * of cursor / clear sequences that interleave with the program's own
 * output.
 *
 * Each operation is logged with:
 *   - a monotonic sequence number
 *   - a UTC timestamp
 *   - the operation name
 *   - for `write` / `writeBuilder`: the raw bytes as a Scala-escaped
 *     literal so escape codes are visible verbatim
 *
 * Activation: set the `WS_CONSOLE_DEBUG_LOG` env var to a writable path,
 * or use `DebugTerminal.wrap(inner, path)` directly.
 */
final class DebugTerminal(
  inner:    Terminal,
  logPath:  String,
  writer:   PrintWriter,
  counter:  AtomicLong
) extends Terminal:

  private def log(op: String, detail: String = ""): UIO[Unit] =
    ZIO.succeed {
      val seq = counter.incrementAndGet()
      val ts  = Instant.now.toString
      writer.println(s"[$seq] $ts $op${if detail.isEmpty then "" else s" $detail"}")
      writer.flush()
    }

  private def escapeLiteral(s: String): String =
    val sb = new StringBuilder
    var i = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '' => sb.append("\\e")
        case '\n'     => sb.append("\\n")
        case '\r'     => sb.append("\\r")
        case '\t'     => sb.append("\\t")
        case _ if c < 0x20 || c == 0x7f => sb.append(f"\\x${c.toInt}%02x")
        case _        => sb.append(c)
      i += 1
    sb.toString

  // ===== Lifecycle =====
  def enterRawMode:         IO[IOException, Unit] = log("enterRawMode")          *> inner.enterRawMode
  def exitRawMode:          IO[IOException, Unit] = log("exitRawMode")           *> inner.exitRawMode
  def enterAlternateBuffer: IO[IOException, Unit] = log("enterAlternateBuffer")  *> inner.enterAlternateBuffer
  def exitAlternateBuffer:  IO[IOException, Unit] = log("exitAlternateBuffer")   *> inner.exitAlternateBuffer
  def hideCursor:           IO[IOException, Unit] = log("hideCursor")            *> inner.hideCursor
  def showCursor:           IO[IOException, Unit] = log("showCursor")            *> inner.showCursor
  def saveCursor:           IO[IOException, Unit] = log("saveCursor")            *> inner.saveCursor
  def restoreCursor:        IO[IOException, Unit] = log("restoreCursor")         *> inner.restoreCursor
  def clearScreen:          IO[IOException, Unit] = log("clearScreen")           *> inner.clearScreen
  def clearLine:            IO[IOException, Unit] = log("clearLine")             *> inner.clearLine
  def resetScrollRegion:    IO[IOException, Unit] = log("resetScrollRegion")     *> inner.resetScrollRegion

  def moveCursor(row: Int, col: Int):            IO[IOException, Unit] =
    log("moveCursor", s"row=$row col=$col") *> inner.moveCursor(row, col)

  def setScrollRegion(top: Int, bottom: Int):    IO[IOException, Unit] =
    log("setScrollRegion", s"top=$top bottom=$bottom") *> inner.setScrollRegion(top, bottom)

  def write(text: String): IO[IOException, Unit] =
    log("write", s"bytes=${text.length} content=${escapeLiteral(text)}") *> inner.write(text)

  def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit] =
    val s = builder.build
    log("writeBuilder", s"bytes=${s.length} content=${escapeLiteral(s)}") *> inner.writeBuilder(builder)

  def flush: IO[IOException, Unit] =
    log("flush") *> inner.flush

  def readRaw(timeout: Duration): IO[IOException, RawInput] =
    inner.readRaw(timeout).tap(r => log("readRaw", s"timeout=$timeout result=$r"))

  def size: IO[IOException, TerminalSize] =
    inner.size.tap(s => log("size", s"rows=${s.rows} cols=${s.cols}"))

  def capabilities: IO[IOException, TerminalCapabilities] = inner.capabilities

  override def events: ZStream[Any, IOException, Event] =
    inner.events.tap(e => log("event", s"$e"))

  /** Close the log writer. Idempotent; safe to call multiple times. */
  def close(): Unit =
    writer.flush()
    writer.close()

object DebugTerminal:

  /**
   * Wrap `inner` and tee every operation to `logPath`. The log file is
   * truncated on creation. Returns a `ZIO.Scoped` so the writer closes
   * on scope exit.
   */
  def wrap(inner: Terminal, logPath: String): ZIO[Scope, IOException, DebugTerminal] =
    ZIO
      .attemptBlockingIO {
        val path = Paths.get(logPath)
        Option(path.getParent).foreach(Files.createDirectories(_))
        val writer = new PrintWriter(
          Files.newBufferedWriter(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
          )
        )
        writer.println(s"# ws-console DebugTerminal log, opened ${Instant.now}")
        writer.flush()
        new DebugTerminal(inner, logPath, writer, new AtomicLong(0))
      }
      .withFinalizer(t => ZIO.succeed(t.close()))

  /**
   * ZLayer that conditionally wraps the underlying `Terminal` based on
   * the `WS_CONSOLE_DEBUG_LOG` env var. When unset, returns the inner
   * terminal unchanged.
   */
  val live: ZLayer[Terminal, IOException, Terminal] =
    ZLayer.scoped {
      ZIO.service[Terminal].flatMap { inner =>
        val path = Option(java.lang.System.getenv("WS_CONSOLE_DEBUG_LOG")).filter(_.nonEmpty)
        path match
          case Some(p) => wrap(inner, p).map(t => t: Terminal)
          case None    => ZIO.succeed(inner)
      }
    }
