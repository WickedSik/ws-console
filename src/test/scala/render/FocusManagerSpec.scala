package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.{Component, ComponentId, RenderContext}
import geometry.Rect

import zio.{Ref, Scope}
import zio.test.*

object FocusManagerSpec extends ZIOSpecDefault:

  final private case class Focusable(name: String) extends Component:
    override val focusable: Boolean = true
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  final private case class NotFocusable(name: String) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  /**
   * Build a `FocusOrder` from a list of components — only `focusable = true`
   *  ones are included, all with placeholder zero rects (sufficient for
   *  cycle-cycling tests; rect content is not exercised here).
   */
  private def orderOf(components: Component*): FocusOrder =
    FocusOrder.fromFocusables(components.toVector)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("FocusManager")(
    test("focusNext on empty cycle stays at None") {
      for
        fm <- FocusManager.make
        _ <- fm.focusNext()
        f <- fm.focused
      yield assertTrue(f.isEmpty)
    },
    test("focusNext skips non-focusable components in tree order") {
      // DropOnRemoval policy so the cycle starts from None and the
      // sequence below is deterministic — under the default
      // MoveToFirstOnRemoval, setOrder auto-focuses to the first entry.
      val a = Focusable("a")
      val b = NotFocusable("b")
      val c = Focusable("c")
      for
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        _ <- fm.setOrder(orderOf(a, b, c))
        _ <- fm.focusNext()
        f1 <- fm.focused
        _ <- fm.focusNext()
        f2 <- fm.focused
        _ <- fm.focusNext()
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
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        _ <- fm.setOrder(orderOf(a, b, c))
        _ <- fm.focusPrevious()
        f1 <- fm.focused
        _ <- fm.focusPrevious()
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
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        _ <- fm.setOrder(orderOf(a, b))
        ok <- fm.focus(a.id)
        no <- fm.focus(b.id)
        f <- fm.focused
      yield assertTrue(ok, !no, f.contains(a.id))
    },
    test("default policy auto-focuses first entry on setOrder from None") {
      val a = Focusable("a")
      val b = Focusable("b")
      for
        fm <- FocusManager.make // default MoveToFirstOnRemoval
        _ <- fm.setOrder(orderOf(a, b))
        f <- fm.focused
      yield assertTrue(f.contains(a.id))
    },
    test("default policy MoveToFirstOnRemoval: removed focus rolls to first") {
      val a = Focusable("a")
      val b = Focusable("b")
      for
        fm <- FocusManager.make // default MoveToFirstOnRemoval
        _ <- fm.setOrder(orderOf(a, b))
        _ <- fm.focus(a.id)
        _ <- fm.setOrder(orderOf(b)) // a removed; focus rolls to b
        f <- fm.focused
      yield assertTrue(f.contains(b.id))
    },
    test("DropOnRemoval policy: removed focus clears to None") {
      val a = Focusable("a")
      val b = Focusable("b")
      for
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        _ <- fm.setOrder(orderOf(a, b))
        _ <- fm.focus(a.id)
        _ <- fm.setOrder(orderOf(b)) // a removed; focus drops
        f <- fm.focused
      yield assertTrue(f.isEmpty)
    },
    test("custom policy receives previous focus + new order, returns new focus") {
      // Always pick the LAST entry of the new order; demonstrates that
      // consumers can express arbitrary reconciliation strategies.
      val a = Focusable("a")
      val b = Focusable("b")
      val c = Focusable("c")
      val pickLast = FocusPolicy.custom { (_, order) =>
        order.entries.lastOption.map(_.id)
      }
      for
        fm <- FocusManager.make(pickLast)
        _ <- fm.setOrder(orderOf(a, b, c))
        f <- fm.focused
      yield assertTrue(f.contains(c.id))
    },
    test("preserved focus survives setOrder when id is still present") {
      val a = Focusable("a")
      val b = Focusable("b")
      for
        fm <- FocusManager.make
        _ <- fm.setOrder(orderOf(a, b))
        _ <- fm.focus(b.id)
        _ <- fm.setOrder(orderOf(a, b)) // unchanged; focus preserved
        f <- fm.focused
      yield assertTrue(f.contains(b.id))
    },
    test("clear() removes focus") {
      val a = Focusable("a")
      for
        fm <- FocusManager.make
        _ <- fm.setOrder(orderOf(a))
        _ <- fm.focus(a.id)
        _ <- fm.clear()
        f <- fm.focused
      yield assertTrue(f.isEmpty)
    },

    // `RenderLoop.redraw` installs the new order *after* the render walk, so a
    // policy-driven focus move lands too late for the frame just drawn. Unless
    // `setOrder` signals, nothing schedules the frame that would show it and
    // the screen contradicts the manager indefinitely.
    suite("setOrder redraw signalling")(
      test("fires onChange when the policy moves focus") {
        val a = Focusable("a")
        for
          hits <- Ref.make(0)
          fm <- FocusManager.make(FocusPolicy.MoveToFirstOnRemoval, hits.update(_ + 1))
          _ <- fm.setOrder(orderOf(a)) // None -> a: the policy moved focus
          once <- hits.get
          f <- fm.focused
        yield assertTrue(once == 1, f.contains(a.id))
      },
      test("stays silent when reconciliation is a no-op") {
        val a = Focusable("a")
        for
          hits <- Ref.make(0)
          fm <- FocusManager.make(FocusPolicy.MoveToFirstOnRemoval, hits.update(_ + 1))
          _ <- fm.setOrder(orderOf(a))
          _ <- hits.set(0)
          _ <- fm.setOrder(orderOf(a)) // a -> a: nothing to redraw for
          _ <- fm.setOrder(orderOf(a))
          quiet <- hits.get
        yield assertTrue(quiet == 0)
      },
      test("fires onChange when the focused component leaves the cycle") {
        val a = Focusable("a")
        val b = Focusable("b")
        for
          hits <- Ref.make(0)
          fm <- FocusManager.make(FocusPolicy.DropOnRemoval, hits.update(_ + 1))
          _ <- fm.setOrder(orderOf(a, b))
          _ <- fm.focus(a.id)
          _ <- hits.set(0)
          _ <- fm.setOrder(orderOf(b)) // a is gone -> focus drops
          once <- hits.get
          f <- fm.focused
        yield assertTrue(once == 1, f.isEmpty)
      }
    )
  )
