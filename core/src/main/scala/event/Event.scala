package io.github.wickedsik.wsconsole
package event

/**
 * Layer 5 event ADT — typed representation of terminal input.
 *
 * `EventParser` consumes raw bytes (from `Terminal.readRaw`) and
 * produces `Event` values. `Terminal.events` is the canonical pipeline.
 *
 * `MouseEvent` and `Resize` are reserved sub-types so future emission
 * lands non-breakingly; Layer 5 currently emits only `KeyEvent` cases.
 */
sealed trait Event

object Event:
  /** Terminal-resize event. */
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

/** Reserved for future mouse events (`MouseClick`, `MouseDrag`, `MouseScroll`). */
sealed trait MouseEvent extends Event
