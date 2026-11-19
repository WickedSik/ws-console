package io.github.wickedsik.wsconsole
package demo.widgets

import buffer.{Canvas, Frame}
import component.{Component, FocusSnapshot, RenderContext}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, Keymap, SpecialKeyCode}
import geometry.Rect

import zio.*
import zio.test.*

import java.io.IOException

object GlobalShortcutsSpec extends ZIOSpecDefault:

  private object Inert extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  private val markerA: ZIO[Frame, IOException, Unit] = ZIO.unit
  private val markerB: ZIO[Frame, IOException, Unit] = ZIO.unit.map(_ => ())

  private val ctx = RenderContext(FocusSnapshot(None))

  /** Whether `res` performs exactly `effect`. */
  private def performs(res: EventResult, effect: ZIO[Frame, IOException, Unit]): Boolean =
    res match
      case EventResult.Perform(e)                                                 => e eq effect
      case EventResult.Consumed | EventResult.Ignored | EventResult.RequestRedraw => false

  private val keymap: Keymap[ZIO[Frame, IOException, Unit]] = Keymap(
    Keymap.plain('q')                -> markerA,
    Keymap.plain(SpecialKeyCode.Tab) -> markerB
  )

  def spec: Spec[TestEnvironment & Scope, Any] = suite("GlobalShortcuts")(
    test("a bound chord returns Perform with the mapped effect") {
      for
        shortcuts <- GlobalShortcuts.make(Inert, keymap)
      yield assertTrue(
        performs(shortcuts.handleEvent(CharKey('q', Set.empty), ctx), markerA),
        performs(shortcuts.handleEvent(SpecialKey(SpecialKeyCode.Tab, Set.empty), ctx), markerB)
      )
    },
    test("an unbound key, a modified chord and a resize return Ignored so they bubble") {
      for
        shortcuts <- GlobalShortcuts.make(Inert, keymap)
      yield
        val unbound: Event = CharKey('x', Set.empty)
        val modified: Event = CharKey('q', Set(event.KeyModifier.Ctrl))
        assertTrue(
          shortcuts.handleEvent(unbound, ctx) == EventResult.Ignored,
          shortcuts.handleEvent(modified, ctx) == EventResult.Ignored,
          shortcuts.handleEvent(Event.Resize(80, 24), ctx) == EventResult.Ignored
        )
    },
    test("the child fills the container's rect") {
      for
        shortcuts <- GlobalShortcuts.make(Inert, Keymap.empty)
      yield
        val area = Rect(2, 3, 40, 20)
        val layout = shortcuts.childLayouts(area)
        assertTrue(
          layout.size == 1,
          layout.headOption.exists((child, rect) => (child eq Inert) && rect == area)
        )
    }
  )
