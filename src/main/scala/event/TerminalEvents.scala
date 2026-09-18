package io.github.wickedsik.wsconsole
package event

import terminal.{RawInput, Terminal}

import zio.{Chunk, Duration, Ref, ZIO}
import zio.stream.ZStream

import java.io.IOException

/**
 * Turns a [[Terminal]]'s raw byte input into a stream of typed
 * [[Event]] values via [[EventParser]].
 */
object TerminalEvents:

  /**
   * Lone-ESC vs alt-prefix disambiguation timeout. After `ESC`, a
   * finite-timeout `readRaw`; if no follow-up arrives, the parser
   * flushes `SpecialKey(Escape)`. 50 ms matches `vim`'s default.
   */
  val LoneEscTimeout: Duration = Duration.fromMillis(50)

  /**
   * Build the event stream for a Terminal. Blocks on `readRaw` while
   * `Idle`; switches to `readRaw(LoneEscTimeout)` on `EscapePending`;
   * flushes on `Timeout`; terminates on `EndOfInput`.
   */
  def events(terminal: Terminal): ZStream[Any, IOException, Event] =
    ZStream.unwrap {
      Ref.make[ParserState](ParserState.Idle).map { stateRef =>
        ZStream.repeatZIOChunkOption(pullNext(terminal, stateRef))
      }
    }

  private def pullNext(
    terminal: Terminal,
    stateRef: Ref[ParserState]
  ): ZIO[Any, Option[IOException], Chunk[Event]] =
    for
      state <- stateRef.get
      timeout = if state == ParserState.EscapePending then LoneEscTimeout else Duration.Zero
      raw <- terminal.readRaw(timeout).mapError(Some(_))
      result <- raw match
                  case RawInput.Bytes(data) =>
                    val (newState, events) = EventParser.parse(state, data)
                    stateRef.set(newState).as(events)
                  case RawInput.Timeout =>
                    val (newState, events) = EventParser.parse(state, Chunk.empty)
                    stateRef.set(newState).as(events)
                  case RawInput.EndOfInput =>
                    ZIO.fail(None)
    yield result
