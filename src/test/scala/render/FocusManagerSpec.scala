package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.{Component, ComponentId}
import geometry.Rect

import zio.Scope
import zio.test.*

object FocusManagerSpec extends ZIOSpecDefault:

  private final case class Focusable(name: String) extends Component:
    override val focusable: Boolean = true
    def render(area: Rect, canvas: Canvas): Unit = ()

  private final case class NotFocusable(name: String) extends Component:
    def render(area: Rect, canvas: Canvas): Unit = ()

  def spec: Spec[TestEnvironment & Scope, Any] = suite("FocusManager")(

    test("focusNext on empty cycle stays at None") {
      for
        fm <- FocusManager.make
        _  <- fm.focusNext()
        f  <- fm.focused
      yield assertTrue(f.isEmpty)
    },

    test("focusNext skips non-focusable components in tree order") {
      val a = Focusable("a")
      val b = NotFocusable("b")
      val c = Focusable("c")
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(Vector(a, b, c))
        _  <- fm.focusNext()
        f1 <- fm.focused
        _  <- fm.focusNext()
        f2 <- fm.focused
        _  <- fm.focusNext()
        f3 <- fm.focused // wraps to a
      yield assertTrue(
        f1.contains(a.id),
        f2.contains(c.id),
        f3.contains(a.id)
      )
    },

    test("focusPrevious cycles in reverse and wraps") {
      val a = Focusable("a")
      val b = Focusable("b")
      val c = Focusable("c")
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(Vector(a, b, c))
        _  <- fm.focusPrevious()
        f1 <- fm.focused
        _  <- fm.focusPrevious()
        f2 <- fm.focused
      yield assertTrue(
        f1.contains(c.id),
        f2.contains(b.id)
      )
    },

    test("focus(id) returns true for focusable id, false otherwise") {
      val a = Focusable("a")
      val b = NotFocusable("b")
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(Vector(a, b))
        ok <- fm.focus(a.id)
        no <- fm.focus(b.id)
        f  <- fm.focused
      yield assertTrue(ok, !no, f.contains(a.id))
    },

    test("updateFocusables clears focus if previously-focused id is gone") {
      val a = Focusable("a")
      val b = Focusable("b")
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(Vector(a, b))
        _  <- fm.focus(a.id)
        _  <- fm.updateFocusables(Vector(b)) // a removed
        f  <- fm.focused
      yield assertTrue(f.isEmpty)
    },

    test("clear() removes focus") {
      val a = Focusable("a")
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(Vector(a))
        _  <- fm.focus(a.id)
        _  <- fm.clear()
        f  <- fm.focused
      yield assertTrue(f.isEmpty)
    }
  )
