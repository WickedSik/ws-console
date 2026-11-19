package io.github.wickedsik.wsconsole
package event

import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode as SK

import zio.Scope
import zio.test.*

object KeyInputSpec extends ZIOSpecDefault:

  private val keymap: Keymap[String] = Keymap(
    Keymap.plain(SK.Enter) -> "confirm",
    Keymap.plain('h')      -> "left"
  )

  private def decode(event: Event): KeyInput[String] = KeyInput.decode(event, keymap)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("KeyInput.decode")(
    test("a bound chord is Bound") {
      assertTrue(decode(SpecialKey(SK.Enter, Set.empty)) == KeyInput.Bound("confirm"))
    },
    test("a binding wins over text") {
      assertTrue(decode(CharKey('h', Set.empty)) == KeyInput.Bound("left"))
    },
    test("an unbound printable character is Text") {
      assertTrue(
        decode(CharKey('a', Set.empty)) == KeyInput.Text('a'),
        decode(CharKey(' ', Set.empty)) == KeyInput.Text(' ')
      )
    },
    test("Shift keeps a character Text") {
      assertTrue(decode(CharKey('A', Set(KeyModifier.Shift))) == KeyInput.Text('A'))
    },
    test("Unicode characters are Text") {
      assertTrue(
        decode(CharKey('中', Set.empty)) == KeyInput.Text('中'),
        decode(CharKey('Ж', Set.empty)) == KeyInput.Text('Ж'),
        decode(CharKey('☑', Set.empty)) == KeyInput.Text('☑')
      )
    },
    test("Ctrl or Alt makes a character Unbound, not Text") {
      val ctrlC = CharKey('c', Set(KeyModifier.Ctrl))
      val altB = CharKey('b', Set(KeyModifier.Alt))
      assertTrue(
        decode(ctrlC) == KeyInput.Unbound(ctrlC),
        decode(altB) == KeyInput.Unbound(altB)
      )
    },
    test("a control character is Unbound") {
      val bell = CharKey('\u0007', Set.empty)
      assertTrue(decode(bell) == KeyInput.Unbound(bell))
    },
    test("an unbound special key is Unbound, whatever its modifiers") {
      val up = SpecialKey(SK.Up, Set.empty)
      val ctrlEnter = SpecialKey(SK.Enter, Set(KeyModifier.Ctrl))
      assertTrue(
        decode(up) == KeyInput.Unbound(up),
        decode(ctrlEnter) == KeyInput.Unbound(ctrlEnter)
      )
    },
    test("a resize is NotKey") {
      assertTrue(decode(Event.Resize(80, 24)) == KeyInput.NotKey)
    }
  )
