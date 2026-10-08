package io.github.wickedsik.wsconsole

import buffer.Frame
import demo.DemoApp
import terminal.{DebugTerminal, TerminalFactory}
import zio.{ZIO, ZIOAppDefault}

object Main extends ZIOAppDefault:
  // Transparent wrapper around TerminalFactory.live that mirrors every
  // terminal op to a log file when WS_CONSOLE_DEBUG_LOG is set. No-op
  // when the env var is absent.
  private val terminalLayer = TerminalFactory.live >>> DebugTerminal.live

  def run: ZIO[Any, Any, Unit] =
    DemoApp.run.provide(terminalLayer, Frame.live)
