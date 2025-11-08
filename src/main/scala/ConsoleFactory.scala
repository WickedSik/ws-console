package io.github.wickedsik.wsconsole

import capabilities.*
import config.*

import zio.*

import java.io.IOException

object ConsoleFactory {

  def create(using config: ConsoleConfig = ConsoleConfig.default): IO[IOException, Terminal] = {
    for {
      capabilities <- detectCapabilities
      terminal <- selectImplementation(capabilities, config)
    } yield terminal
  }

  val layer: ZLayer[Any, IOException, Terminal] = {
    ZLayer {
      for {
        config <- ZIO.succeed(ConsoleConfig.default)
        terminal <- create(using config)
      } yield terminal
    }
  }

  def layer(config: ConsoleConfig): ZLayer[Any, IOException, Terminal] = {
    ZLayer {
      create(using config)
    }
  }

  private def detectCapabilities: IO[IOException, TerminalCapabilities] = {
    ZIO.succeed {
      TerminalCapabilities(
        hasColors = ???,
        colorDepth = ???,
        width = ???,
        height = ???,
        canResize = ???,
        isInteractive = ???,
        terminalType = ???,
        environment = ???
      )
    }
  }

  private def selectImplementation(
    capabilities: TerminalCapabilities,
    config: ConsoleConfig
  ): IO[IOException, Terminal] = {
    if (shouldUseJLineTerminal(capabilities, config)) {
      createJLineTerminal(config)
    } else {
      createAnsiTerminal(config)
    }
  }

  private def shouldUseJLineTerminal(
    capabilities: TerminalCapabilities,
    config: ConsoleConfig
  ): Boolean = {
    ???
  }

  private def createJLineTerminal(config: ConsoleConfig): IO[IOException, Terminal] = {
    ZIO.attempt {
      new JLineTerminal(config)
    }.mapError { ex =>
      new IOException(s"Failed to create JLine terminal: ${ex.getMessage}", ex)
    }
  }

  private def createAnsiTerminal(config: ConsoleConfig): IO[IOException, Terminal] = {
    ZIO.succeed {
      new AnsiTerminal(config)
    }
  }
}

// Skeleton implementations
private class JLineTerminal(config: ConsoleConfig) extends Terminal {
  def write(text: String): IO[IOException, Unit] = ???
  def writeLine(text: String): IO[IOException, Unit] = ???
  def readLine: IO[IOException, String] = ???
  def readPassword(prompt: String): IO[IOException, String] = ???
  def prompt(text: String): IO[IOException, String] = ???
  def confirm(text: String, default: Boolean): IO[IOException, Boolean] = ???
  def writeWrapped(text: String, width: Int): IO[IOException, Unit] = ???
  def writeParagraphs(text: String, width: Int): IO[IOException, Unit] = ???
  def writeIntroduction(text: String): IO[IOException, Unit] = ???
  def clear: IO[IOException, Unit] = ???
  def getCursorPosition: IO[IOException, (Int, Int)] = ???
  def setCursorPosition(row: Int, col: Int): IO[IOException, Unit] = ???
  def getWidth: IO[IOException, Int] = ???
  def getHeight: IO[IOException, Int] = ???
  def hasColorSupport: IO[IOException, Boolean] = ???
  def getColorDepth: IO[IOException, ColorDepth] = ???
}

private class AnsiTerminal(config: ConsoleConfig) extends Terminal {
  def write(text: String): IO[IOException, Unit] = ???
  def writeLine(text: String): IO[IOException, Unit] = ???
  def readLine: IO[IOException, String] = ???
  def readPassword(prompt: String): IO[IOException, String] = ???
  def prompt(text: String): IO[IOException, String] = ???
  def confirm(text: String, default: Boolean): IO[IOException, Boolean] = ???
  def writeWrapped(text: String, width: Int): IO[IOException, Unit] = ???
  def writeParagraphs(text: String, width: Int): IO[IOException, Unit] = ???
  def writeIntroduction(text: String): IO[IOException, Unit] = ???
  def clear: IO[IOException, Unit] = ???
  def getCursorPosition: IO[IOException, (Int, Int)] = ???
  def setCursorPosition(row: Int, col: Int): IO[IOException, Unit] = ???
  def getWidth: IO[IOException, Int] = ???
  def getHeight: IO[IOException, Int] = ???
  def hasColorSupport: IO[IOException, Boolean] = ???
  def getColorDepth: IO[IOException, ColorDepth] = ???
}