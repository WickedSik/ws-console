package io.github.wickedsik.wsconsole
package terminal

import zio.*

import java.io.IOException
import java.lang.{System as JSystem}

/**
 * Factory for creating Terminal instances with capability detection and validation.
 *
 * Provides ZLayer construction with proper resource management - terminal state
 * is guaranteed to be restored on all exit paths including SIGINT (fiber interruption).
 *
 * Two layer variants:
 * - `live`: Detects capabilities, validates requirements, fails fast if unsupported
 * - `unsafe`: Detects capabilities but skips validation (for advanced users)
 */
object TerminalFactory:

  // ===== Supported Terminals Reference =====

  private val supportedTerminals: String =
    """Supported terminals:
      |  macOS: Terminal.app, iTerm2
      |  Linux: GNOME Terminal, Konsole, Alacritty, Kitty
      |  Windows: Windows Terminal
      |  IDE: VS Code, JetBrains integrated terminals""".stripMargin

  // ===== Detection =====

  /** Detect terminal capabilities from the current environment */
  private def detect: IO[IOException, TerminalCapabilities] =
    for
      isTTY   <- HostSystem.detectTTY
      color   <- HostSystem.detectColorSupport
      unicode <- HostSystem.detectUnicode
      size    <- HostSystem.detectSize(TerminalSize(24, 80))
    yield TerminalCapabilities(
      colorSupport = color,
      supportsUnicode = unicode,
      supportsMouseTracking = isTTY,
      supportsAlternateBuffer = isTTY,
      isTTY = isTTY,
      size = size
    )

  // ===== Validation =====

  /** Validate that detected capabilities meet minimum requirements */
  private[terminal] def validate(caps: TerminalCapabilities): IO[UnsupportedTerminalException, Unit] =
    val issues = List.newBuilder[String]

    if !caps.isTTY then
      issues += "Not connected to an interactive terminal (TTY required)"

    if caps.colorSupport == ColorSupport.NoColor then
      issues += "No color support detected (256+ colors required)"

    if !caps.supportsUnicode then
      issues += "No Unicode support detected (UTF-8 required)"

    val issueList = issues.result()
    if issueList.nonEmpty then
      val message = s"""Terminal does not meet ws-console requirements:
                       |${issueList.map(i => s"  - $i").mkString("\n")}
                       |
                       |$supportedTerminals""".stripMargin
      ZIO.fail(new UnsupportedTerminalException(message))
    else
      ZIO.unit

  // ===== Construction =====

  /** Create an AnsiTerminal instance with detected capabilities */
  private def make(caps: TerminalCapabilities): IO[IOException, AnsiTerminal] =
    for
      sttyRef   <- Ref.make[Option[String]](None)
      semaphore <- Semaphore.make(1)
    yield new AnsiTerminal(
      output = JSystem.out,
      input = JSystem.in,
      caps = caps,
      originalSttySettings = sttyRef,
      writeLock = semaphore
    )

  // ===== ZLayer Construction =====

  /**
   * Live ZLayer with full detection and validation.
   * Fails with UnsupportedTerminalException if the terminal doesn't meet requirements.
   * Terminal state is restored on scope closure (including SIGINT).
   */
  val live: ZLayer[Any, IOException, Terminal] = buildLayer(validated = true)

  /**
   * Unsafe ZLayer that skips validation.
   * Use when you know your terminal is supported but detection may be unreliable
   * (e.g., running inside tmux with unusual TERM settings).
   */
  val unsafe: ZLayer[Any, IOException, Terminal] = buildLayer(validated = false)

  private def buildLayer(validated: Boolean): ZLayer[Any, IOException, Terminal] =
    ZLayer.scoped {
      ZIO.acquireRelease(
        for
          caps     <- detect
          _        <- validate(caps).when(validated)
          terminal <- make(caps)
        yield terminal
      )(_.restoreState.ignore)
    }
