package io.github.wickedsik.wsconsole
package terminal

import zio.*

import java.io.IOException
import java.lang.{System as JSystem}

/**
 * Facade for host system interactions.
 *
 * Encapsulates all process execution and environment detection so that
 * neither AnsiTerminal nor TerminalFactory need to know how the host
 * system is queried. Package-private to the terminal package.
 */
private[terminal] object HostSystem:

  // ===== Low-Level Process Execution =====

  /**
   * Execute a stty command via /dev/tty redirect.
   *
   * @param args Arguments to pass to stty (e.g. "-g", "raw -echo", "size")
   * @return Command stdout, trimmed
   */
  def executeStty(args: String): IO[IOException, String] =
    executeShell(s"stty $args < /dev/tty")

  /**
   * Execute a tput command.
   *
   * @param param The tput parameter (e.g. "lines", "cols")
   * @return Command stdout, trimmed
   */
  private def executeTput(param: String): IO[IOException, String] =
    executeProcess("tput", param)

  // ===== Size Detection Facade =====

  /**
   * Detect terminal size using the best available method.
   *
   * Tries stty first, then tput, then falls back to the provided default.
   * Callers never need to know which method succeeded.
   *
   * @param fallback Size to use if all detection methods fail
   */
  def detectSize(fallback: TerminalSize): IO[IOException, TerminalSize] =
    sizeViaStty.orElse(sizeViaTput).orElse(ZIO.succeed(fallback))

  // ===== Environment Detection =====

  /** Check if stdout is connected to an interactive terminal */
  def detectTTY: IO[IOException, Boolean] =
    ZIO.attemptBlockingIO {
      JSystem.console() != null
    }

  /**
   * Detect the level of color support from standardized environment signals.
   *
   * Relies on COLORTERM and TERM - the standard mechanisms terminals use
   * to advertise their capabilities. No terminal names are hardcoded.
   */
  def detectColorSupport: IO[IOException, ColorSupport] =
    ZIO.attemptBlockingIO {
      resolveColorSupport(envOrEmpty("COLORTERM"), envOrEmpty("TERM"))
    }

  /** Detect Unicode support from locale environment variables */
  def detectUnicode: IO[IOException, Boolean] =
    ZIO.attemptBlockingIO {
      resolveUnicode(envOrEmpty("LANG"), envOrEmpty("LC_ALL"), envOrEmpty("LC_CTYPE"))
    }

  // ===== Pure Decision Functions (package-private for testability) =====

  /** Resolve color support from raw environment variable values */
  private[terminal] def resolveColorSupport(colorTerm: String, term: String): ColorSupport =
    val ct = colorTerm.toLowerCase
    val t = term.toLowerCase

    if ct == "truecolor" || ct == "24bit" then
      ColorSupport.TrueColor
    else if t.contains("256color") then
      ColorSupport.Extended256
    else if t == "dumb" || t.isEmpty then
      ColorSupport.NoColor
    else
      ColorSupport.Basic16

  /** Resolve unicode support from raw locale variable values */
  private[terminal] def resolveUnicode(lang: String, lcAll: String, lcCtype: String): Boolean =
    List(lang, lcAll, lcCtype).exists(v =>
      v.toUpperCase.contains("UTF-8") || v.toUpperCase.contains("UTF8")
    )

  // ===== Private Helpers =====

  private def envOrEmpty(name: String): String =
    Option(JSystem.getenv(name)).getOrElse("")

  private def executeShell(command: String): IO[IOException, String] =
    ZIO.attemptBlockingIO {
      val pb = new ProcessBuilder("/bin/sh", "-c", command)
      pb.redirectErrorStream(true)
      val process = pb.start()
      val result = new String(process.getInputStream.readAllBytes(), "UTF-8").trim
      val exitCode = process.waitFor()
      if exitCode != 0 then
        throw new IOException(s"Command '$command' failed with exit code $exitCode: $result")
      result
    }

  private def executeProcess(command: String*): IO[IOException, String] =
    ZIO.attemptBlockingIO {
      val pb = new ProcessBuilder(command*)
      pb.redirectErrorStream(true)
      val process = pb.start()
      val result = new String(process.getInputStream.readAllBytes(), "UTF-8").trim
      val exitCode = process.waitFor()
      if exitCode != 0 then
        throw new IOException(s"Command '${command.mkString(" ")}' failed with exit code $exitCode: $result")
      result
    }

  private def sizeViaStty: IO[IOException, TerminalSize] =
    for
      output <- executeStty("size")
      size   <- ZIO.attempt {
        val parts = output.split("\\s+")
        TerminalSize(rows = parts(0).toInt, cols = parts(1).toInt)
      }.mapError(e => new IOException(s"Failed to parse stty size output '$output': ${e.getMessage}"))
    yield size

  private def sizeViaTput: IO[IOException, TerminalSize] =
    for
      rows <- executeTput("lines").flatMap(parseIntOutput("tput lines"))
      cols <- executeTput("cols").flatMap(parseIntOutput("tput cols"))
    yield TerminalSize(rows = rows, cols = cols)

  private def parseIntOutput(label: String)(raw: String): IO[IOException, Int] =
    ZIO.attempt(raw.toInt)
      .mapError(e => new IOException(s"Failed to parse $label output '$raw': ${e.getMessage}"))
