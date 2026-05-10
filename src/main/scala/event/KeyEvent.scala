package io.github.wickedsik.wsconsole
package event

/**
 * Keyboard modifier flag. `Meta` is deferred - terminal support varies and
 * adding it later is non-breaking.
 */
enum KeyModifier:
  case Ctrl, Alt, Shift

/**
 * Non-character keys recognised by the parser.
 *
 *   - Navigation: `Up`, `Down`, `Left`, `Right`, `Home`, `End`, `PgUp`, `PgDn`
 *   - Editing:    `Enter`, `Escape`, `Tab`, `Backspace`, `Insert`, `Delete`
 *   - Function:   `F1`-`F12`
 */
enum SpecialKeyCode:
  case Up, Down, Left, Right
  case Home, End, PgUp, PgDn
  case Enter, Escape, Tab, Backspace, Insert, Delete
  case F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12
