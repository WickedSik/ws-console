package io.github.wickedsik.wsconsole
package terminal

import ansi.AnsiBuilder
import zio.*

import java.io.{IOException, InputStream, OutputStream}

/**
 * Concrete Terminal implementation using ANSI escape sequences.
 *
 * Bridges the AnsiBuilder API to actual terminal I/O via System.out/in.
 * Thread safety is ensured by a Semaphore guarding write+flush pairs.
 * Raw mode is managed via stty commands delegated to HostSystem.
 *
 * Created exclusively through TerminalFactory - constructor is package-private.
 *
 * @param output Stream for terminal output (typically System.out)
 * @param input Stream for terminal input (typically System.in)
 * @param caps Detected terminal capabilities
 * @param originalSttySettings Saved stty settings for raw mode restoration
 * @param writeLock Semaphore ensuring atomic write+flush across fibers
 */
final class AnsiTerminal private[terminal] (
  private val output: OutputStream,
  private val input: InputStream,
  private val caps: TerminalCapabilities,
  private val originalSttySettings: Ref[Option[String]],
  private val writeLock: Semaphore
) extends Terminal:

  // ===== Internal Helpers =====

  /** Write an AnsiBuilder's output and flush atomically */
  private def writeAndFlush(builder: AnsiBuilder): IO[IOException, Unit] =
    writeLock.withPermit {
      ZIO.attemptBlockingIO {
        val bytes = builder.build.getBytes("UTF-8")
        output.write(bytes)
        output.flush()
      }
    }

  // ===== Lifecycle =====

  override def enterRawMode: IO[IOException, Unit] =
    for
      current <- HostSystem.executeStty("-g")
      _       <- originalSttySettings.set(Some(current))
      _       <- HostSystem.executeStty("raw -echo -icanon min 1 time 0")
    yield ()

  override def exitRawMode: IO[IOException, Unit] =
    for
      saved <- originalSttySettings.get
      _ <- saved match
        case Some(settings) =>
          HostSystem.executeStty(settings) *> originalSttySettings.set(None)
        case None =>
          HostSystem.executeStty("sane")
    yield ()

  override def enterAlternateBuffer: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().enterAltBuffer)

  override def exitAlternateBuffer: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().exitAltBuffer)

  override def disableLineWrap: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().lineWrapOff)

  override def enableLineWrap: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().lineWrapOn)

  // ===== Cursor =====

  override def moveCursor(row: Int, col: Int): IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().moveTo(row, col))

  override def hideCursor: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().hideCursor)

  override def showCursor: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().showCursor)

  override def saveCursor: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().saveCursor)

  override def restoreCursor: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().restoreCursor)

  // ===== Screen =====

  override def clearScreen: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().clearScreen.home)

  override def clearLine: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().clearLine)

  // ===== Scroll =====

  override def setScrollRegion(top: Int, bottom: Int): IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().setScrollRegion(top, bottom))

  override def resetScrollRegion: IO[IOException, Unit] =
    writeAndFlush(AnsiBuilder().resetScrollRegion)

  // ===== Output =====

  override def write(text: String): IO[IOException, Unit] =
    writeLock.withPermit {
      ZIO.attemptBlockingIO {
        output.write(text.getBytes("UTF-8"))
      }
    }

  override def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit] =
    writeAndFlush(builder)

  override def flush: IO[IOException, Unit] =
    writeLock.withPermit {
      ZIO.attemptBlockingIO {
        output.flush()
      }
    }

  // ===== Input =====

  override def readRaw(timeout: Duration): IO[IOException, RawInput] =
    val readBytes: IO[IOException, RawInput] = ZIO.attemptBlockingInterrupt {
      val available = input.available()
      if available > 0 then
        val buffer = new Array[Byte](math.min(available, 1024))
        val bytesRead = input.read(buffer)
        if bytesRead == -1 then RawInput.EndOfInput
        else RawInput.Bytes(Chunk.fromArray(buffer.take(bytesRead)))
      else
        // Block for a single byte if nothing available
        val b = input.read()
        if b == -1 then RawInput.EndOfInput
        else
          // Check if more bytes arrived while we were blocked
          val remaining = input.available()
          if remaining > 0 then
            val buffer = new Array[Byte](math.min(remaining, 1023))
            val moreRead = input.read(buffer)
            RawInput.Bytes(Chunk(b.toByte) ++ Chunk.fromArray(buffer.take(moreRead)))
          else
            RawInput.Bytes(Chunk(b.toByte))
    }.refineToOrDie[IOException]

    if timeout.isZero then readBytes
    else
      readBytes
        .timeout(timeout)
        .map(_.getOrElse(RawInput.Timeout))

  // ===== Info =====

  override def size: IO[IOException, TerminalSize] =
    HostSystem.detectSize(caps.size)

  override def capabilities: IO[IOException, TerminalCapabilities] =
    for
      currentSize <- HostSystem.detectSize(caps.size)
    yield caps.copy(size = currentSize)

  // ===== State Restoration (called by factory release action) =====

  /** Restore terminal to a clean state. Package-private for TerminalFactory. */
  private[terminal] def restoreState: IO[IOException, Unit] =
    for
      _ <- resetScrollRegion.ignore
      _ <- enableLineWrap.ignore
      _ <- showCursor.ignore
      _ <- exitRawMode.ignore
    yield ()
