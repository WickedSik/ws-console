package io.github.wickedsik.wsconsole
package event

import event.KeyEvent.{CharKey, SpecialKey}

/**
 * Key bindings as data: each exact chord maps to an action of type `A`.
 *
 * A chord is a [[KeyEvent]] with its full modifier set, so
 * `SpecialKey(Left, Set.empty)` and `SpecialKey(Left, Set(Ctrl))` are
 * different bindings. A key that is not bound is answered by the
 * component, usually by letting it bubble.
 *
 * Widgets expose their default keymap and accept a replacement, so an
 * application rebinds keys by extending the default:
 *
 * {{{
 *   Slider.DefaultKeymap ++ Keymap(
 *     CharKey('h', Set.empty) -> Slider.Action.Step(-1),
 *     CharKey('l', Set.empty) -> Slider.Action.Step(+1)
 *   )
 * }}}
 */
final case class Keymap[A](bindings: Map[KeyEvent, A]):

  /** The action bound to exactly this chord. */
  def lookup(key: KeyEvent): Option[A] = bindings.get(key)

  /** Both keymaps' bindings; where both bind a chord, `other` wins. */
  def ++(other: Keymap[A]): Keymap[A] = Keymap(bindings ++ other.bindings)

object Keymap:

  /** A keymap of the given chord bindings; a later pair for the same chord wins. */
  def apply[A](pairs: (KeyEvent, A)*): Keymap[A] = Keymap(pairs.toMap)

  /** A keymap that binds nothing. */
  def empty[A]: Keymap[A] = Keymap(Map.empty[KeyEvent, A])

  /** `code` with no modifier held. */
  def plain(code: SpecialKeyCode): KeyEvent = SpecialKey(code, Set.empty)

  /** `char` with no modifier held. */
  def plain(char: Char): KeyEvent = CharKey(char, Set.empty)

  /** `code` with `Ctrl` held. */
  def ctrl(code: SpecialKeyCode): KeyEvent = SpecialKey(code, Set(KeyModifier.Ctrl))

  /** `code` with `Alt` held. */
  def alt(code: SpecialKeyCode): KeyEvent = SpecialKey(code, Set(KeyModifier.Alt))

  /** `char` with `Alt` held. */
  def alt(char: Char): KeyEvent = CharKey(char, Set(KeyModifier.Alt))

/**
 * An event as one component sees it through its keymap. A handler
 * matches on this exhaustively: every bound action, text, unbound keys
 * and non-key events each get an explicit answer.
 */
enum KeyInput[+A]:

  /** A chord the keymap binds, carrying its action. */
  case Bound(action: A)

  /**
   * A character to type: a [[KeyEvent.CharKey]] without `Ctrl` or `Alt`
   * that is not a control character, and that the keymap does not bind.
   * `Shift` is allowed, since it selects the character.
   */
  case Text(char: Char)

  /** A key the keymap does not bind and that is not text. */
  case Unbound(key: KeyEvent)

  /** An event that is not a key, such as a resize. */
  case NotKey

object KeyInput:

  /**
   * Classify `event` for a component with `keymap`. A binding wins over
   * text, so a keymap may claim a printable character.
   */
  def decode[A](event: Event, keymap: Keymap[A]): KeyInput[A] =
    event match
      case key: KeyEvent =>
        keymap.lookup(key) match
          case Some(action) => Bound(action)
          case None         => textOf(key).fold(Unbound(key))(Text(_))
      case _: Event.Resize | _: MouseEvent => NotKey

  private def textOf(key: KeyEvent): Option[Char] =
    key match
      case CharKey(char, mods) =>
        Option.when(!mods(KeyModifier.Ctrl) && !mods(KeyModifier.Alt) && !char.isControl)(char)
      case SpecialKey(_, _) => None
