package io.github.wickedsik.wsconsole
package event

import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode as SK

import zio.Scope
import zio.test.*

object KeymapSpec extends ZIOSpecDefault:

  private val keymap: Keymap[String] = Keymap(
    Keymap.plain(SK.Left) -> "left",
    Keymap.ctrl(SK.Left)  -> "scroll-left",
    Keymap.alt('b')       -> "word-left"
  )

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Keymap")(
    suite("lookup")(
      test("finds the action bound to an exact chord") {
        assertTrue(
          keymap.lookup(SpecialKey(SK.Left, Set.empty)).contains("left"),
          keymap.lookup(SpecialKey(SK.Left, Set(KeyModifier.Ctrl))).contains("scroll-left"),
          keymap.lookup(CharKey('b', Set(KeyModifier.Alt))).contains("word-left")
        )
      },
      test("misses a chord whose modifiers differ") {
        assertTrue(
          keymap.lookup(SpecialKey(SK.Left, Set(KeyModifier.Shift))).isEmpty,
          keymap.lookup(SpecialKey(SK.Left, Set(KeyModifier.Ctrl, KeyModifier.Alt))).isEmpty,
          keymap.lookup(CharKey('b', Set.empty)).isEmpty
        )
      },
      test("misses an unbound key") {
        assertTrue(keymap.lookup(SpecialKey(SK.Right, Set.empty)).isEmpty)
      },
      test("the empty keymap binds nothing") {
        assertTrue(Keymap.empty[String].lookup(SpecialKey(SK.Left, Set.empty)).isEmpty)
      }
    ),
    suite("++")(
      test("the right side wins a chord both bind") {
        val rebound = keymap ++ Keymap(Keymap.plain(SK.Left) -> "previous")
        assertTrue(rebound.lookup(SpecialKey(SK.Left, Set.empty)).contains("previous"))
      },
      test("keeps the bindings only one side has") {
        val extended = keymap ++ Keymap(Keymap.plain('h') -> "left")
        assertTrue(
          extended.lookup(CharKey('h', Set.empty)).contains("left"),
          extended.lookup(SpecialKey(SK.Left, Set(KeyModifier.Ctrl))).contains("scroll-left")
        )
      }
    ),
    test("the chord helpers carry exactly their modifier") {
      assertTrue(
        Keymap.plain(SK.Tab) == SpecialKey(SK.Tab, Set.empty),
        Keymap.shift(SK.Tab) == SpecialKey(SK.Tab, Set(KeyModifier.Shift)),
        Keymap.ctrl(SK.Home) == SpecialKey(SK.Home, Set(KeyModifier.Ctrl)),
        Keymap.alt(SK.Left) == SpecialKey(SK.Left, Set(KeyModifier.Alt)),
        Keymap.alt('f') == CharKey('f', Set(KeyModifier.Alt)),
        Keymap.plain('x') == CharKey('x', Set.empty)
      )
    },
    test("a later pair for the same chord wins") {
      val km = Keymap(Keymap.plain(SK.Up) -> 1, Keymap.plain(SK.Up) -> 2)
      assertTrue(km.lookup(SpecialKey(SK.Up, Set.empty)).contains(2))
    }
  )
