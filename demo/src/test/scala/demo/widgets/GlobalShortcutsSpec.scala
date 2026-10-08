package io.github.wickedsik.wsconsole
package demo.widgets

import buffer.{Canvas, Frame}
import component.{Component, FocusSnapshot, RenderContext}
import event.{Event, EventResult}
import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode
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

  def spec: Spec[TestEnvironment & Scope, Any] = suite("GlobalShortcuts")(
    test("a bound event returns Perform with the mapped effect") {
      for
        shortcuts <- GlobalShortcuts.make(Inert) {
                       case CharKey('q', mods) if mods.isEmpty => markerA
                       case SpecialKey(SpecialKeyCode.Tab, _)  => markerB
                     }
      yield
        val qRes = shortcuts.handleEvent(CharKey('q', Set.empty), ctx)
        val tabRes = shortcuts.handleEvent(SpecialKey(SpecialKeyCode.Tab, Set.empty), ctx)
        assertTrue(
          qRes match
            case EventResult.Perform(effect) => effect eq markerA
            case _                           => false,
          tabRes match
            case EventResult.Perform(effect) => effect eq markerB
            case _                           => false
        )
    },
    test("an unbound event returns Ignored so it bubbles to the application") {
      for
        shortcuts <- GlobalShortcuts.make(Inert) {
                       case CharKey('q', _) => markerA
                     }
      yield
        val res = shortcuts.handleEvent(CharKey('x', Set.empty), ctx)
        assertTrue(res == EventResult.Ignored)
    },
    test("the child fills the container's rect") {
      for
        shortcuts <- GlobalShortcuts.make(Inert)(PartialFunction.empty)
      yield
        val area = Rect(2, 3, 40, 20)
        val layout = shortcuts.childLayouts(area)
        assertTrue(
          layout.size == 1,
          layout.head._1 eq Inert,
          layout.head._2 == area
        )
    }
  )
