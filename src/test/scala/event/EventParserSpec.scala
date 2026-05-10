package io.github.wickedsik.wsconsole
package event

import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode as SK

import zio.{Chunk, Scope}
import zio.test.*
import zio.test.Assertion.*

object EventParserSpec extends ZIOSpecDefault:

  /** Run a single chunk through the parser starting from Idle. */
  private def parseFromIdle(bytes: Int*): (ParserState, Chunk[Event]) =
    EventParser.parse(ParserState.Idle, Chunk.fromArray(bytes.map(_.toByte).toArray))

  /** Run a sequence of chunks through the parser, threading state. */
  private def parseChunks(chunks: Chunk[Byte]*): (ParserState, Chunk[Event]) =
    chunks.foldLeft((ParserState.Idle: ParserState, Chunk.empty[Event])) { case ((st, acc), bs) =>
      val (ns, evs) = EventParser.parse(st, bs)
      (ns, acc ++ evs)
    }

  private def chunkOf(bytes: Int*): Chunk[Byte] =
    Chunk.fromArray(bytes.map(_.toByte).toArray)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("EventParser")(
    asciiSuite,
    controlBytesSuite,
    specialBytesSuite,
    csiSuite,
    ss3Suite,
    tildeSuite,
    utf8Suite,
    partialAndStateSuite,
    escapeAndAltSuite,
    silentConsumptionSuite
  )

  // ===== Plain ASCII =====

  private val asciiSuite = suite("ASCII printable bytes")(
    test("0x41 ('A') -> CharKey('A')") {
      val (state, events) = parseFromIdle(0x41)
      assertTrue(
        state == ParserState.Idle,
        events.toList == List(CharKey('A', Set.empty))
      )
    },
    test("0x20 (' ') -> CharKey(' ')") {
      val (_, events) = parseFromIdle(0x20)
      assertTrue(events.toList == List(CharKey(' ', Set.empty)))
    },
    test("0x7E ('~') -> CharKey('~')") {
      val (_, events) = parseFromIdle(0x7E)
      assertTrue(events.toList == List(CharKey('~', Set.empty)))
    },
    test("multiple printable bytes in a single chunk produce multiple events") {
      val (_, events) = parseFromIdle('h', 'i')
      assertTrue(events.toList == List(CharKey('h', Set.empty), CharKey('i', Set.empty)))
    }
  )

  // ===== C0 controls -> Ctrl+letter =====

  private val controlBytesSuite = suite("C0 control bytes -> Ctrl+letter")(
    test("0x01 -> CharKey('a', Ctrl)") {
      val (_, events) = parseFromIdle(0x01)
      assertTrue(events.toList == List(CharKey('a', Set(KeyModifier.Ctrl))))
    },
    test("0x03 -> CharKey('c', Ctrl) (the canonical Ctrl+C in raw mode)") {
      val (_, events) = parseFromIdle(0x03)
      assertTrue(events.toList == List(CharKey('c', Set(KeyModifier.Ctrl))))
    },
    test("0x1A -> CharKey('z', Ctrl)") {
      val (_, events) = parseFromIdle(0x1A)
      assertTrue(events.toList == List(CharKey('z', Set(KeyModifier.Ctrl))))
    },
    test("0x1C-0x1F decode to Ctrl+\\ ] ^ _") {
      val (_, e1c) = parseFromIdle(0x1C)
      val (_, e1d) = parseFromIdle(0x1D)
      val (_, e1e) = parseFromIdle(0x1E)
      val (_, e1f) = parseFromIdle(0x1F)
      assertTrue(
        e1c.toList == List(CharKey('\\', Set(KeyModifier.Ctrl))),
        e1d.toList == List(CharKey(']', Set(KeyModifier.Ctrl))),
        e1e.toList == List(CharKey('^', Set(KeyModifier.Ctrl))),
        e1f.toList == List(CharKey('_', Set(KeyModifier.Ctrl)))
      )
    },
    test("0x00 (NUL) silently consumed") {
      val (state, events) = parseFromIdle(0x00)
      assertTrue(state == ParserState.Idle, events.isEmpty)
    }
  )

  // ===== Tab/Enter/Backspace rulings (Q1, Q2) =====

  private val specialBytesSuite = suite("Tab/Enter/Backspace rulings")(
    test("0x09 -> SpecialKey(Tab) (Tab and Ctrl+I share 0x09; collapse forced)") {
      val (_, events) = parseFromIdle(0x09)
      assertTrue(events.toList == List(SpecialKey(SK.Tab, Set.empty)))
    },
    test("0x0D -> SpecialKey(Enter)") {
      val (_, events) = parseFromIdle(0x0D)
      assertTrue(events.toList == List(SpecialKey(SK.Enter, Set.empty)))
    },
    test("0x0A -> CharKey('j', Ctrl) (Q1 ruling: distinguish from Enter)") {
      val (_, events) = parseFromIdle(0x0A)
      assertTrue(events.toList == List(CharKey('j', Set(KeyModifier.Ctrl))))
    },
    test("0x7F -> SpecialKey(Backspace)") {
      val (_, events) = parseFromIdle(0x7F)
      assertTrue(events.toList == List(SpecialKey(SK.Backspace, Set.empty)))
    },
    test("0x08 -> CharKey('h', Ctrl) (Q2 ruling: distinguish from Backspace)") {
      val (_, events) = parseFromIdle(0x08)
      assertTrue(events.toList == List(CharKey('h', Set(KeyModifier.Ctrl))))
    }
  )

  // ===== CSI sequences =====

  private val csiSuite = suite("CSI sequences")(
    test("ESC [ A -> SpecialKey(Up)") {
      val (_, events) = parseFromIdle(0x1B, '['.toInt, 'A'.toInt)
      assertTrue(events.toList == List(SpecialKey(SK.Up, Set.empty)))
    },
    test("ESC [ B -> SpecialKey(Down)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'B')
      assertTrue(events.toList == List(SpecialKey(SK.Down, Set.empty)))
    },
    test("ESC [ C -> SpecialKey(Right)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'C')
      assertTrue(events.toList == List(SpecialKey(SK.Right, Set.empty)))
    },
    test("ESC [ D -> SpecialKey(Left)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'D')
      assertTrue(events.toList == List(SpecialKey(SK.Left, Set.empty)))
    },
    test("ESC [ H -> SpecialKey(Home)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'H')
      assertTrue(events.toList == List(SpecialKey(SK.Home, Set.empty)))
    },
    test("ESC [ F -> SpecialKey(End)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'F')
      assertTrue(events.toList == List(SpecialKey(SK.End, Set.empty)))
    },
    test("ESC [ Z -> SpecialKey(Tab, Shift) (BackTab)") {
      val (_, events) = parseFromIdle(0x1B, '[', 'Z')
      assertTrue(events.toList == List(SpecialKey(SK.Tab, Set(KeyModifier.Shift))))
    },
    test("ESC [ 1 ; 2 A -> SpecialKey(Up, Shift)") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', ';', '2', 'A')
      assertTrue(events.toList == List(SpecialKey(SK.Up, Set(KeyModifier.Shift))))
    },
    test("ESC [ 1 ; 5 A -> SpecialKey(Up, Ctrl)") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', ';', '5', 'A')
      assertTrue(events.toList == List(SpecialKey(SK.Up, Set(KeyModifier.Ctrl))))
    },
    test("ESC [ 1 ; 6 D -> SpecialKey(Left, Shift+Ctrl)") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', ';', '6', 'D')
      assertTrue(events.toList == List(SpecialKey(SK.Left, Set(KeyModifier.Shift, KeyModifier.Ctrl))))
    },
    test("ESC [ 1 ; 8 B -> SpecialKey(Down, Shift+Alt+Ctrl)") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', ';', '8', 'B')
      assertTrue(
        events.toList == List(
          SpecialKey(SK.Down, Set(KeyModifier.Shift, KeyModifier.Alt, KeyModifier.Ctrl))
        )
      )
    }
  )

  // ===== SS3 (function keys F1-F4) =====

  private val ss3Suite = suite("SS3 sequences (F1-F4)")(
    test("ESC O P -> F1") {
      val (_, events) = parseFromIdle(0x1B, 'O', 'P')
      assertTrue(events.toList == List(SpecialKey(SK.F1, Set.empty)))
    },
    test("ESC O Q -> F2") {
      val (_, events) = parseFromIdle(0x1B, 'O', 'Q')
      assertTrue(events.toList == List(SpecialKey(SK.F2, Set.empty)))
    },
    test("ESC O R -> F3") {
      val (_, events) = parseFromIdle(0x1B, 'O', 'R')
      assertTrue(events.toList == List(SpecialKey(SK.F3, Set.empty)))
    },
    test("ESC O S -> F4") {
      val (_, events) = parseFromIdle(0x1B, 'O', 'S')
      assertTrue(events.toList == List(SpecialKey(SK.F4, Set.empty)))
    }
  )

  // ===== CSI tilde (F5-F12, navigation) =====

  private val tildeSuite = suite("CSI tilde sequences (F5-F12 and navigation)")(
    test("ESC [ 5 ~ -> PgUp") {
      val (_, events) = parseFromIdle(0x1B, '[', '5', '~')
      assertTrue(events.toList == List(SpecialKey(SK.PgUp, Set.empty)))
    },
    test("ESC [ 6 ~ -> PgDn") {
      val (_, events) = parseFromIdle(0x1B, '[', '6', '~')
      assertTrue(events.toList == List(SpecialKey(SK.PgDn, Set.empty)))
    },
    test("ESC [ 2 ~ -> Insert") {
      val (_, events) = parseFromIdle(0x1B, '[', '2', '~')
      assertTrue(events.toList == List(SpecialKey(SK.Insert, Set.empty)))
    },
    test("ESC [ 3 ~ -> Delete") {
      val (_, events) = parseFromIdle(0x1B, '[', '3', '~')
      assertTrue(events.toList == List(SpecialKey(SK.Delete, Set.empty)))
    },
    test("ESC [ 1 ~ -> Home (legacy linux encoding)") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', '~')
      assertTrue(events.toList == List(SpecialKey(SK.Home, Set.empty)))
    },
    test("ESC [ 4 ~ -> End (legacy linux encoding)") {
      val (_, events) = parseFromIdle(0x1B, '[', '4', '~')
      assertTrue(events.toList == List(SpecialKey(SK.End, Set.empty)))
    },
    test("ESC [ 15 ~ -> F5") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', '5', '~')
      assertTrue(events.toList == List(SpecialKey(SK.F5, Set.empty)))
    },
    test("ESC [ 17 ~ -> F6") {
      val (_, events) = parseFromIdle(0x1B, '[', '1', '7', '~')
      assertTrue(events.toList == List(SpecialKey(SK.F6, Set.empty)))
    },
    test("ESC [ 24 ~ -> F12") {
      val (_, events) = parseFromIdle(0x1B, '[', '2', '4', '~')
      assertTrue(events.toList == List(SpecialKey(SK.F12, Set.empty)))
    },
    test("ESC [ 5 ; 5 ~ -> PgUp with Ctrl") {
      val (_, events) = parseFromIdle(0x1B, '[', '5', ';', '5', '~')
      assertTrue(events.toList == List(SpecialKey(SK.PgUp, Set(KeyModifier.Ctrl))))
    }
  )

  // ===== UTF-8 multi-byte =====

  private val utf8Suite = suite("UTF-8 multi-byte")(
    test("0xC3 0xA9 -> CharKey('é') (2-byte sequence)") {
      val (_, events) = parseFromIdle(0xC3, 0xA9)
      assertTrue(events.toList == List(CharKey('é', Set.empty)))
    },
    test("0xE2 0x98 0x83 -> CharKey('☃') (3-byte sequence, BMP)") {
      val (_, events) = parseFromIdle(0xE2, 0x98, 0x83)
      assertTrue(events.toList == List(CharKey('☃', Set.empty)))
    },
    test("4-byte SMP sequence consumed but no event emitted (out of scope)") {
      // U+1F600 GRINNING FACE = F0 9F 98 80
      val (state, events) = parseFromIdle(0xF0, 0x9F, 0x98, 0x80)
      assertTrue(state == ParserState.Idle, events.isEmpty)
    }
  )

  // ===== Partial-sequence buffering =====

  private val partialAndStateSuite = suite("Partial sequences and state threading")(
    test("ESC [ split across two chunks - first emits nothing, second completes") {
      val (s1, e1) = EventParser.parse(ParserState.Idle, chunkOf(0x1B, '['))
      val (s2, e2) = EventParser.parse(s1, chunkOf('A'))
      assertTrue(
        e1.isEmpty,
        s1.isInstanceOf[ParserState.Csi],
        e2.toList == List(SpecialKey(SK.Up, Set.empty)),
        s2 == ParserState.Idle
      )
    },
    test("CSI parameter bytes split across chunks") {
      val (s1, e1) = EventParser.parse(ParserState.Idle, chunkOf(0x1B, '[', '1'))
      val (s2, e2) = EventParser.parse(s1, chunkOf(';', '2'))
      val (s3, e3) = EventParser.parse(s2, chunkOf('A'))
      assertTrue(
        e1.isEmpty,
        e2.isEmpty,
        e3.toList == List(SpecialKey(SK.Up, Set(KeyModifier.Shift))),
        s3 == ParserState.Idle
      )
    },
    test("UTF-8 multi-byte split across chunks") {
      val (s1, e1) = EventParser.parse(ParserState.Idle, chunkOf(0xC3))
      val (s2, e2) = EventParser.parse(s1, chunkOf(0xA9))
      assertTrue(
        e1.isEmpty,
        s1.isInstanceOf[ParserState.Utf8],
        e2.toList == List(CharKey('é', Set.empty)),
        s2 == ParserState.Idle
      )
    }
  )

  // ===== Lone ESC & alt-prefix =====

  private val escapeAndAltSuite = suite("Lone ESC and alt-prefix disambiguation")(
    test("Lone ESC followed by no further bytes -> EscapePending; empty chunk flushes Escape") {
      val (s1, e1) = EventParser.parse(ParserState.Idle, chunkOf(0x1B))
      val (s2, e2) = EventParser.parse(s1, Chunk.empty)
      assertTrue(
        e1.isEmpty,
        s1 == ParserState.EscapePending,
        e2.toList == List(SpecialKey(SK.Escape, Set.empty)),
        s2 == ParserState.Idle
      )
    },
    test("ESC followed by printable -> Alt+<char>") {
      val (s, events) = parseFromIdle(0x1B, 'a')
      assertTrue(
        events.toList == List(CharKey('a', Set(KeyModifier.Alt))),
        s == ParserState.Idle
      )
    },
    test("ESC ESC emits one Escape, stays EscapePending for the second") {
      val (s, events) = parseFromIdle(0x1B, 0x1B)
      assertTrue(
        events.toList == List(SpecialKey(SK.Escape, Set.empty)),
        s == ParserState.EscapePending
      )
    }
  )

  // ===== Silent consumption =====

  private val silentConsumptionSuite = suite("Silent consumption of unrecognised input")(
    test("Unrecognised CSI final byte -> no event, state returns to Idle") {
      // ESC [ 9 9 j is not a recognised final-byte; 'j' is in 0x40-0x7E so it terminates
      val (state, events) = parseFromIdle(0x1B, '[', '9', '9', 'j')
      assertTrue(state == ParserState.Idle, events.isEmpty)
    },
    test("Malformed CSI (control byte mid-sequence) drops to Idle") {
      val (state, events) = EventParser.parse(
        ParserState.Idle,
        chunkOf(0x1B, '[', 0x07) // BEL inside CSI - malformed
      )
      assertTrue(state == ParserState.Idle, events.isEmpty)
    },
    test("Unknown SS3 final byte -> no event, state returns to Idle") {
      val (state, events) = parseFromIdle(0x1B, 'O', 'X')
      assertTrue(state == ParserState.Idle, events.isEmpty)
    },
    test("Unknown CSI tilde code -> no event, state returns to Idle") {
      val (state, events) = parseFromIdle(0x1B, '[', '9', '9', '~')
      assertTrue(state == ParserState.Idle, events.isEmpty)
    }
  )
