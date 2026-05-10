package io.github.wickedsik.wsconsole
package event

import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode as SK

import zio.Chunk

import java.nio.charset.StandardCharsets

/**
 * Parser state. Threaded across `parse` calls so partial sequences (a CSI
 * arrow split across two reads, a lone ESC awaiting timeout, a multi-byte
 * UTF-8 codepoint mid-arrival) are completed correctly.
 *
 * Public so consumers wiring their own stream can inspect the state - the
 * shipped `Terminal.events` reader needs to detect `EscapePending` to switch
 * to a finite-timeout `readRaw` for lone-ESC disambiguation.
 */
sealed trait ParserState

object ParserState:
  case object Idle extends ParserState
  case object EscapePending extends ParserState
  /** Buffering bytes after `ESC [`, awaiting a final byte (0x40-0x7E). */
  final case class Csi(buf: Vector[Byte]) extends ParserState
  /** Single-byte SS3 sequence after `ESC O`, awaiting one more byte. */
  case object Ss3 extends ParserState
  /** Mid-decode UTF-8 multi-byte sequence; `buf` already contains the lead byte. */
  final case class Utf8(buf: Vector[Byte], expectedLen: Int) extends ParserState

/**
 * Pure stateful parser: bytes -> typed events.
 *
 * `parse` is a referentially transparent function. The Layer 1 input bytes
 * (from `Terminal.readRaw`) flow in as `Chunk[Byte]`; events flow out as
 * `Chunk[Event]`; the parser's state is threaded explicitly so the caller
 * holds the only reference to it.
 *
 *   - An empty input chunk is a valid "timeout flush" - if the parser is
 *     in `EscapePending`, that flush emits `SpecialKey(Escape)`.
 *   - Unrecognised escape sequences are silently consumed; the parser
 *     never fails the stream over malformed input.
 *   - UTF-8: full BMP support (1-3 byte sequences). 4-byte sequences are
 *     consumed but produce no event (SMP codepoints require surrogate-pair
 *     handling in `Cell`, deferred).
 *
 * See `docs/terminal-architecture.md` Layer 5 for the Tab/Enter/Backspace
 * encoding rulings (Q1/Q2 in the task scroll).
 */
object EventParser:

  /**
   * Feed a chunk of bytes through the parser; return the new state and any
   * events that completed during this chunk.
   *
   * Empty input flushes pending state - in particular, an `EscapePending`
   * state transitions to `Idle` and emits `SpecialKey(Escape)`. The stream
   * layer drives this behaviour after a finite-timeout `readRaw`.
   */
  def parse(state: ParserState, bytes: Chunk[Byte]): (ParserState, Chunk[Event]) =
    if bytes.isEmpty then flushEmpty(state)
    else
      var s = state
      var events: Chunk[Event] = Chunk.empty
      val arr = bytes.toArray
      var i = 0
      while i < arr.length do
        val b = arr(i) & 0xFF
        val (ns, evs) = stepByte(s, b)
        s = ns
        if evs.nonEmpty then events = events ++ evs
        i += 1
      (s, events)

  // ===== Top-level dispatch =====

  private def flushEmpty(state: ParserState): (ParserState, Chunk[Event]) =
    state match
      case ParserState.EscapePending =>
        (ParserState.Idle, Chunk.single(SpecialKey(SK.Escape, Set.empty)))
      case _ =>
        (state, Chunk.empty)

  private def stepByte(state: ParserState, b: Int): (ParserState, Chunk[Event]) =
    state match
      case ParserState.Idle                  => idleByte(b)
      case ParserState.EscapePending         => escapePendingByte(b)
      case ParserState.Csi(buf)              => csiByte(buf, b)
      case ParserState.Ss3                   => ss3Byte(b)
      case ParserState.Utf8(buf, expectedLen) => utf8Byte(buf, expectedLen, b)

  // ===== Idle =====

  private def idleByte(b: Int): (ParserState, Chunk[Event]) =
    if b == 0x09 then
      (ParserState.Idle, Chunk.single(SpecialKey(SK.Tab, Set.empty)))
    else if b == 0x0D then
      (ParserState.Idle, Chunk.single(SpecialKey(SK.Enter, Set.empty)))
    else if b == 0x1B then
      (ParserState.EscapePending, Chunk.empty)
    else if b == 0x7F then
      (ParserState.Idle, Chunk.single(SpecialKey(SK.Backspace, Set.empty)))
    else if b >= 0x01 && b <= 0x1A then
      // Ctrl+letter: 0x01 -> 'a', 0x1A -> 'z'. Tab/Enter/Esc are pre-empted above.
      (ParserState.Idle, Chunk.single(CharKey((b + 0x60).toChar, Set(KeyModifier.Ctrl))))
    else if b >= 0x1C && b <= 0x1F then
      // Ctrl+\, Ctrl+], Ctrl+^, Ctrl+_
      (ParserState.Idle, Chunk.single(CharKey((b + 0x40).toChar, Set(KeyModifier.Ctrl))))
    else if b >= 0x20 && b <= 0x7E then
      (ParserState.Idle, Chunk.single(CharKey(b.toChar, Set.empty)))
    else if b >= 0xC0 && b <= 0xDF then
      (ParserState.Utf8(Vector(b.toByte), 2), Chunk.empty)
    else if b >= 0xE0 && b <= 0xEF then
      (ParserState.Utf8(Vector(b.toByte), 3), Chunk.empty)
    else if b >= 0xF0 && b <= 0xF7 then
      (ParserState.Utf8(Vector(b.toByte), 4), Chunk.empty)
    else
      // 0x00 (NUL), 0x80-0xBF (stray UTF-8 continuation), 0xF8-0xFF (invalid) - silent
      (ParserState.Idle, Chunk.empty)

  // ===== EscapePending =====

  private def escapePendingByte(b: Int): (ParserState, Chunk[Event]) =
    if b == 0x5B then // '['
      (ParserState.Csi(Vector.empty), Chunk.empty)
    else if b == 0x4F then // 'O'
      (ParserState.Ss3, Chunk.empty)
    else if b == 0x1B then
      // ESC ESC: emit Escape for the first, stay pending for the second.
      (ParserState.EscapePending, Chunk.single(SpecialKey(SK.Escape, Set.empty)))
    else if b >= 0x20 && b <= 0x7E then
      // Alt+<printable> - vim-style alt-prefix encoding.
      (ParserState.Idle, Chunk.single(CharKey(b.toChar, Set(KeyModifier.Alt))))
    else
      // Anything else: emit lone Escape, then re-process the byte from Idle.
      val (s2, evs) = idleByte(b)
      (s2, SpecialKey(SK.Escape, Set.empty) +: evs)

  // ===== CSI =====

  private def csiByte(buf: Vector[Byte], b: Int): (ParserState, Chunk[Event]) =
    if b >= 0x40 && b <= 0x7E then
      (ParserState.Idle, decodeCsi(buf, b.toChar))
    else if b >= 0x20 && b <= 0x3F then
      (ParserState.Csi(buf :+ b.toByte), Chunk.empty)
    else
      // Malformed CSI: drop and reset.
      (ParserState.Idle, Chunk.empty)

  private def decodeCsi(buf: Vector[Byte], finalByte: Char): Chunk[Event] =
    val params = parseParams(buf)
    finalByte match
      case 'A' => Chunk.single(SpecialKey(SK.Up, modifiersFromParams(params)))
      case 'B' => Chunk.single(SpecialKey(SK.Down, modifiersFromParams(params)))
      case 'C' => Chunk.single(SpecialKey(SK.Right, modifiersFromParams(params)))
      case 'D' => Chunk.single(SpecialKey(SK.Left, modifiersFromParams(params)))
      case 'H' => Chunk.single(SpecialKey(SK.Home, modifiersFromParams(params)))
      case 'F' => Chunk.single(SpecialKey(SK.End, modifiersFromParams(params)))
      case 'Z' => Chunk.single(SpecialKey(SK.Tab, Set(KeyModifier.Shift)))
      case 'P' => Chunk.single(SpecialKey(SK.F1, modifiersFromParams(params)))
      case 'Q' => Chunk.single(SpecialKey(SK.F2, modifiersFromParams(params)))
      case 'R' => Chunk.single(SpecialKey(SK.F3, modifiersFromParams(params)))
      case 'S' => Chunk.single(SpecialKey(SK.F4, modifiersFromParams(params)))
      case '~' => decodeTilde(params)
      case _   => Chunk.empty

  private def parseParams(buf: Vector[Byte]): List[Int] =
    if buf.isEmpty then List.empty
    else
      val s = new String(buf.toArray, StandardCharsets.US_ASCII)
      s.split(';').toList.map { p =>
        try p.toInt
        catch case _: NumberFormatException => 0
      }

  /**
   * Decode the modifier byte from a CSI key sequence.
   * Convention: code = (flags + 1), where flag bit 0 = Shift, bit 1 = Alt,
   * bit 2 = Ctrl. Code 1 = no modifiers, code 2 = Shift, code 5 = Ctrl, etc.
   */
  private def decodeModifier(modCode: Int): Set[KeyModifier] =
    val flags = modCode - 1
    var mods = Set.empty[KeyModifier]
    if (flags & 1) != 0 then mods += KeyModifier.Shift
    if (flags & 2) != 0 then mods += KeyModifier.Alt
    if (flags & 4) != 0 then mods += KeyModifier.Ctrl
    mods

  private def modifiersFromParams(params: List[Int]): Set[KeyModifier] =
    params match
      case _ :: mod :: _ => decodeModifier(mod)
      case _             => Set.empty

  private def decodeTilde(params: List[Int]): Chunk[Event] =
    params match
      case Nil => Chunk.empty
      case keyCode :: rest =>
        val mods = rest.headOption.fold(Set.empty[KeyModifier])(decodeModifier)
        keyForTildeCode(keyCode) match
          case Some(sk) => Chunk.single(SpecialKey(sk, mods))
          case None     => Chunk.empty

  private def keyForTildeCode(code: Int): Option[SpecialKeyCode] =
    code match
      case 1 | 7 => Some(SK.Home)
      case 2     => Some(SK.Insert)
      case 3     => Some(SK.Delete)
      case 4 | 8 => Some(SK.End)
      case 5     => Some(SK.PgUp)
      case 6     => Some(SK.PgDn)
      case 15    => Some(SK.F5)
      case 17    => Some(SK.F6)
      case 18    => Some(SK.F7)
      case 19    => Some(SK.F8)
      case 20    => Some(SK.F9)
      case 21    => Some(SK.F10)
      case 23    => Some(SK.F11)
      case 24    => Some(SK.F12)
      case _     => None

  // ===== SS3 =====

  private def ss3Byte(b: Int): (ParserState, Chunk[Event]) =
    val event = b.toChar match
      case 'P' => Some(SpecialKey(SK.F1, Set.empty))
      case 'Q' => Some(SpecialKey(SK.F2, Set.empty))
      case 'R' => Some(SpecialKey(SK.F3, Set.empty))
      case 'S' => Some(SpecialKey(SK.F4, Set.empty))
      case _   => None
    (ParserState.Idle, event.fold(Chunk.empty[Event])(Chunk.single))

  // ===== UTF-8 =====

  private def utf8Byte(buf: Vector[Byte], expectedLen: Int, b: Int): (ParserState, Chunk[Event]) =
    if (b & 0xC0) == 0x80 then
      val newBuf = buf :+ b.toByte
      if newBuf.length == expectedLen then
        val str = new String(newBuf.toArray, StandardCharsets.UTF_8)
        // BMP only - SMP codepoints emit a surrogate pair (length 2) and are dropped.
        val event =
          if str.length == 1 then Chunk.single[Event](CharKey(str.charAt(0), Set.empty))
          else Chunk.empty[Event]
        (ParserState.Idle, event)
      else
        (ParserState.Utf8(newBuf, expectedLen), Chunk.empty)
    else
      // Malformed UTF-8: abort current sequence and re-process this byte from Idle.
      idleByte(b)
