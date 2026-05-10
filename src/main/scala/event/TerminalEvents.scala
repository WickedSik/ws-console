package io.github.wickedsik.wsconsole
package event

import terminal.{RawInput, Terminal}

import zio.{Chunk, Duration, Ref, ZIO}
import zio.stream.ZStream

import java.io.IOException

/**
 * The driver that turns a [[Terminal]]'s raw byte input into a stream of
 * typed [[Event]] values via [[EventParser]].
 *
 * Lives in package `event` so the Layer 5 implementation does not pollute
 * Layer 1; the `Terminal.events` default method delegates here.
 */
object TerminalEvents:

  /**
   * Lone-ESC vs alt-prefix disambiguation timeout. After an `ESC` byte the
   * driver issues a finite-timeout `readRaw`; if no follow-up byte arrives
   * within this window, the parser flushes a `SpecialKey(Escape)` event.
   *
   * 50 ms matches `vim`'s default. The value is hard-coded for this
   * iteration; a future configurable form is tracked in the task scroll's
   * Deferred / Follow-up section.
   */
  val LoneEscTimeout: Duration = Duration.fromMillis(50)

  /**
   * Build the event stream for a given Terminal.
   *
   * Internally:
   *   - allocates a `Ref[ParserState]` keyed to this stream
   *   - blocks on `readRaw` while in `Idle` (timeout = 0)
   *   - switches to `readRaw(LoneEscTimeout)` while in `EscapePending`
   *   - on `RawInput.Timeout`, feeds an empty chunk through the parser to
   *     flush a pending `Escape`
   *   - terminates on `RawInput.EndOfInput`
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
      state  <- stateRef.get
      timeout = if state == ParserState.EscapePending then LoneEscTimeout else Duration.Zero
      raw    <- terminal.readRaw(timeout).mapError(Some(_))
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
