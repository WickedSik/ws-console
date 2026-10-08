package io.github.wickedsik.wsconsole
package terminal

import ansi.AnsiBuilder

import zio.*

import java.io.{IOException, InputStream, OutputStream}
import scala.annotation.tailrec

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

  @tailrec
  // If we do not check for input, the stream will always hang on "waiting for the next byte"
  private def readByte(in: InputStream): Int =
    if Thread.currentThread().isInterrupted then throw new InterruptedException()

    if in.available() > 0 then
      in.read()
    else
      Thread.sleep(20)
      readByte(in)

  override def readRaw(timeout: Duration): IO[IOException, RawInput] =
    val readBytes: IO[IOException, RawInput] = ZIO.attemptBlockingInterrupt {
      // Always fetch the first byte via the interrupt-checking poll loop,
      // so shutdown wakes us regardless of buffered vs. underlying state.
      // The prior fast-path (input.read(buffer) when available > 0) could
      // park inside BufferedInputStream.fill() and ignore Thread.interrupt().
      val b = readByte(input)
      if b == -1 then RawInput.EndOfInput
      else
        // Drain the rest, capped by available() so no blocking fill can occur.
        val remaining = input.available()
        if remaining > 0 then
          val len = math.min(remaining, 1023)
          val buffer = new Array[Byte](len)
          val moreRead = input.read(buffer, 0, len)
          if moreRead > 0 then
            RawInput.Bytes(Chunk(b.toByte) ++ Chunk.fromArray(buffer.take(moreRead)))
          else
            RawInput.Bytes(Chunk(b.toByte))
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

  /**
   * Restore terminal to a clean state. Package-private for
   * TerminalFactory.
   *
   * Emits the scroll-region reset directly rather than through a trait
   * method — the render path routes all scroll ANSI through
   * `writeBuilder(BufferFlusher.toAnsi(ops))`, and this failsafe is the
   * one remaining caller.
   */
  private[terminal] def restoreState: IO[IOException, Unit] =
    for
      _ <- writeAndFlush(AnsiBuilder().resetScrollRegion).ignore
      _ <- enableLineWrap.ignore
      _ <- showCursor.ignore
      _ <- exitRawMode.ignore
    yield ()
