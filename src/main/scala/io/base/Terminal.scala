package io.github.wickedsik.wsconsole
package io.base

import zio.*

import java.io.IOException

trait Terminal:
  // Core I/O Operations
  def write(text: String): IO[IOException, Unit]
  def writeLine(text: String): IO[IOException, Unit]
  def readLine: IO[IOException, String]
  def readPassword(prompt: String): IO[IOException, String]

  // Interactive Operations
  def prompt(text: String): IO[IOException, String]
  def confirm(text: String, default: Boolean = false): IO[IOException, Boolean]

  // Text Formatting Operations
  def writeWrapped(text: String, width: Int = 80): IO[IOException, Unit]
  def writeParagraphs(text: String, width: Int = 80): IO[IOException, Unit]
  def writeIntroduction(text: String): IO[IOException, Unit]

  // Terminal Control Operations
  def clear: IO[IOException, Unit]
  def getCursorPosition: IO[IOException, (Int, Int)]
  def setCursorPosition(row: Int, col: Int): IO[IOException, Unit]

  // Terminal Information
  def getWidth: IO[IOException, Int]
  def getHeight: IO[IOException, Int]
  def hasColorSupport: IO[IOException, Boolean]
  def getColorDepth: IO[IOException, ColorDepth]

enum ColorDepth:
  case NoColor
  case Basic16
  case Extended256
  case TrueColor

object Terminal:
  def write(text: String): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.write(text))

  def writeLine(text: String): ZIO[Terminal, IOException, Unit] =
    ZIO.serviceWithZIO[Terminal](_.writeLine(text))

  def readLine: ZIO[Terminal, IOException, String] =
    ZIO.serviceWithZIO[Terminal](_.readLine)

  def readPassword(prompt: String): ZIO[Terminal, IOException, String] =
    ZIO.serviceWithZIO[Terminal](_.readPassword(prompt))

  def prompt(text: String): ZIO[Terminal, IOException, String] =
    ZIO.serviceWithZIO[Terminal](_.prompt(text))

  def confirm(text: String, default: Boolean = false): ZIO[Terminal, IOException, Boolean] =
    ZIO.serviceWithZIO[Terminal](_.confirm(text, default))
