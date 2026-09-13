package io.github.wickedsik.wsconsole
package terminal

import zio.*
import zio.test.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

object AnsiTerminalSpec extends ZIOSpecDefault:

  /** Create an AnsiTerminal with injectable streams for testing */
  private def makeTerminal(
    inputBytes: Array[Byte] = Array.empty,
    inputStream: Option[java.io.InputStream] = None
  ): ZIO[Any, Nothing, (AnsiTerminal, ByteArrayOutputStream)] =
    val out = new ByteArrayOutputStream()
    val in = inputStream.getOrElse(new ByteArrayInputStream(inputBytes))
    val caps = TerminalCapabilities(
      ColorSupport.TrueColor,
      true,
      true,
      true,
      true,
      TerminalSize(24, 80)
    )
    for
      sttyRef <- Ref.make[Option[String]](None)
      semaphore <- Semaphore.make(1)
    yield (new AnsiTerminal(out, in, caps, sttyRef, semaphore), out)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("AnsiTerminal")(
    readRawSuite,
    writeSuite
  )

  private val readRawSuite = suite("readRaw")(
    test("returns Bytes when data is available") {
      for
        pair <- makeTerminal(inputBytes = Array(65, 66, 67))
        result <- pair._1.readRaw(Duration.Zero)
      yield result match
        case RawInput.Bytes(data) => assertTrue(data.toArray.sameElements(Array[Byte](65, 66, 67)))
        case _                    => assertTrue(false)
    },
    test("returns single byte when it arrives") {
      for
        pair <- makeTerminal(inputBytes = Array(27))
        result <- pair._1.readRaw(Duration.Zero)
      yield result match
        case RawInput.Bytes(data) => assertTrue(data.length == 1, data.head == 27.toByte)
        case _                    => assertTrue(false)
    },
    test("returns EndOfInput when stream signals EOF") {
      // available() > 0 followed by read() == -1 exercises the EOF branch.
      // An empty ByteArrayInputStream reports available() == 0 and would
      // spin the poll loop — do not use one here.
      val eofStream = new java.io.InputStream:
        private var reported = false
        override def available(): Int = if reported then 0 else 1
        override def read(): Int = { reported = true; -1 }
      for
        pair <- makeTerminal(inputStream = Some(eofStream))
        result <- pair._1.readRaw(Duration.Zero)
      yield assertTrue(result == RawInput.EndOfInput)
    },
    test("returns Timeout when timeout expires with no input") {
      // PipedInputStream blocks on read() when the other end hasn't written.
      // attemptBlockingInterrupt allows ZIO.timeout to interrupt the thread.
      val pipedOut = new java.io.PipedOutputStream()
      val pipedIn = new java.io.PipedInputStream(pipedOut)

      for
        pair <- makeTerminal(inputStream = Some(pipedIn))
        result <- pair._1.readRaw(50.millis)
      yield assertTrue(result == RawInput.Timeout)
    } @@ TestAspect.withLiveClock,
    test("zero timeout reads without waiting") {
      for
        pair <- makeTerminal(inputBytes = Array(42))
        result <- pair._1.readRaw(Duration.Zero)
      yield result match
        case RawInput.Bytes(data) => assertTrue(data.head == 42.toByte)
        case _                    => assertTrue(false)
    }
  )

  private val writeSuite = suite("write and flush")(
    test("write sends bytes to output stream") {
      for
        pair <- makeTerminal()
        _ <- pair._1.write("hello")
      yield assertTrue(pair._2.toString("UTF-8") == "hello")
    },
    test("write handles unicode correctly") {
      for
        pair <- makeTerminal()
        _ <- pair._1.write("你好🚀")
      yield assertTrue(pair._2.toString("UTF-8") == "你好🚀")
    },
    test("writeBuilder writes and flushes") {
      import ansi.AnsiBuilder
      for
        pair <- makeTerminal()
        _ <- pair._1.writeBuilder(AnsiBuilder().text("test"))
      yield assertTrue(pair._2.toString("UTF-8") == "test")
    },
    test("multiple writes accumulate") {
      for
        pair <- makeTerminal()
        _ <- pair._1.write("one")
        _ <- pair._1.write("two")
      yield assertTrue(pair._2.toString("UTF-8") == "onetwo")
    }
  )
