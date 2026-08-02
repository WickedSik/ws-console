package io.github.wickedsik.wsconsole
package event

/**
 * Layer 5 event ADT - the typed representation of terminal input.
 *
 * The `EventParser` consumes raw bytes (from `Terminal.readRaw`) and produces
 * `Event` values. The `Terminal.events` ZStream is the canonical pipeline.
 *
 * `MouseEvent` and `Resize` are reserved sub-types: they exist in the ADT so
 * that future emission lands non-breakingly, but Layer 5 emits only
 * `KeyEvent` cases. See `docs/reference/terminal-architecture.md` for the deferred
 * scope (`EventDispatcher`, `FocusManager`, `Component.handleEvent`).
 */
sealed trait Event

object Event:
  /**
   * Reserved: terminal-resize event. Emission is deferred until a detection
   * mechanism (poll vs SIGWINCH vs JNA) is ratified - see Layer 5 task scroll
   * Open Question Q3.
   */
  final case class Resize(width: Int, height: Int) extends Event

/**
 * Keyboard input event. Two concrete cases: `CharKey` for printable
 * characters and `SpecialKey` for non-character keys.
 */
sealed trait KeyEvent extends Event

object KeyEvent:
  /**
   * A printable Unicode codepoint (BMP) with an optional modifier set.
   *
   * C0 control bytes are surfaced here as `CharKey(letter, Set(Ctrl))` -
   * e.g. byte `0x03` produces `CharKey('c', Set(Ctrl))`. The four
   * special-cased bytes (Tab, Enter, Backspace, Escape) are emitted as
   * `SpecialKey` instead.
   */
  final case class CharKey(char: Char, modifiers: Set[KeyModifier]) extends KeyEvent

  /**
   * A non-character key: navigation, editing, or function key, optionally
   * with modifiers.
   */
  final case class SpecialKey(key: SpecialKeyCode, modifiers: Set[KeyModifier]) extends KeyEvent

/**
 * Reserved sub-trait for future mouse events (`MouseClick`, `MouseDrag`,
 * `MouseScroll`). Mouse-tracking mode requires a `Terminal.enableMouseTracking`
 * API and SGR/X10 decoding - both deferred from this iteration.
 */
sealed trait MouseEvent extends Event
