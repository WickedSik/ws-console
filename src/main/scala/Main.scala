package io.github.wickedsik.wsconsole

import buffer.Frame
import demo.DemoApp
import terminal.TerminalFactory
import zio.{ZIO, ZIOAppDefault}

object Main extends ZIOAppDefault:
  def run: ZIO[Any, Any, Unit] =
    DemoApp.run.provide(TerminalFactory.live, Frame.live)
