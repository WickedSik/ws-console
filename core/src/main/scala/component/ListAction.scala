package io.github.wickedsik.wsconsole
package component

import event.{Keymap, SpecialKeyCode}

/**
 * What a key can ask an open option list to do: the list a [[Select]] or
 * a [[TagInput]] shows while open. Each widget decides what `Confirm` and
 * `DeleteBack` mean for its own selection.
 */
enum ListAction:
  /** Move the highlight up one option. */
  case Up

  /** Move the highlight down one option. */
  case Down

  /** Move the highlight up by the rows the list shows. */
  case PageUp

  /** Move the highlight down by the rows the list shows. */
  case PageDown

  /** Highlight the first option. */
  case First

  /** Highlight the last option. */
  case Last

  /** Act on the highlighted option: select it, or add or remove it. */
  case Confirm

  /** Close the list and keep the key. */
  case Close

  /** Close the list and let the key bubble, so focus moves on. */
  case Leave

  /** Delete the last filter grapheme, or what the widget does with an empty filter. */
  case DeleteBack

object ListAction:

  /**
   * The bare navigation keys, `Enter` to confirm, `Escape` to close,
   * `Tab` and `Shift+Tab` to leave in either direction, and `Backspace`.
   */
  val DefaultKeymap: Keymap[ListAction] = Keymap(
    Keymap.plain(SpecialKeyCode.Up)        -> ListAction.Up,
    Keymap.plain(SpecialKeyCode.Down)      -> ListAction.Down,
    Keymap.plain(SpecialKeyCode.PgUp)      -> ListAction.PageUp,
    Keymap.plain(SpecialKeyCode.PgDn)      -> ListAction.PageDown,
    Keymap.plain(SpecialKeyCode.Home)      -> ListAction.First,
    Keymap.plain(SpecialKeyCode.End)       -> ListAction.Last,
    Keymap.plain(SpecialKeyCode.Enter)     -> ListAction.Confirm,
    Keymap.plain(SpecialKeyCode.Escape)    -> ListAction.Close,
    Keymap.plain(SpecialKeyCode.Tab)       -> ListAction.Leave,
    Keymap.shift(SpecialKeyCode.Tab)       -> ListAction.Leave,
    Keymap.plain(SpecialKeyCode.Backspace) -> ListAction.DeleteBack
  )
