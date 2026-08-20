package io.github.wickedsik.wsconsole
package demo.widgets

import buffer.Frame
import component.{FocusSnapshot, RenderContext}
import event.EventResult
import event.KeyEvent.{CharKey, SpecialKey}
import event.SpecialKeyCode

import zio.*
import zio.test.*

import java.io.IOException

object ToolbarButtonSpec extends ZIOSpecDefault:

  private val marker: ZIO[Frame, IOException, Unit] = ZIO.unit

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ToolbarButton")(

    test("Enter on a focused button returns Perform bound to onActivate") {
      for
        btn <- ToolbarButton.make("Test", marker)
      yield
        val ctx = RenderContext(FocusSnapshot(Some(btn.id)))
        val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), ctx)
        assertTrue(res match
          case EventResult.Perform(effect) => effect eq marker
          case _                           => false
        )
    },

    test("Space on a focused button returns Perform bound to onActivate") {
      for
        btn <- ToolbarButton.make("Test", marker)
      yield
        val ctx = RenderContext(FocusSnapshot(Some(btn.id)))
        val res = btn.handleEvent(CharKey(' ', Set.empty), ctx)
        assertTrue(res match
          case EventResult.Perform(effect) => effect eq marker
          case _                           => false
        )
    },

    test("Enter on an unfocused button returns Ignored (§3.2 focus guard)") {
      for
        btn <- ToolbarButton.make("Test", marker)
      yield
        val ctx = RenderContext(FocusSnapshot(None))
        val res = btn.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), ctx)
        assertTrue(res == EventResult.Ignored)
    },

    test("Other keys on a focused button return Ignored") {
      for
        btn <- ToolbarButton.make("Test", marker)
      yield
        val ctx = RenderContext(FocusSnapshot(Some(btn.id)))
        val res = btn.handleEvent(CharKey('x', Set.empty), ctx)
        assertTrue(res == EventResult.Ignored)
    }
  )
