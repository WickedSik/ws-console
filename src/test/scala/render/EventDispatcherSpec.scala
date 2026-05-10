package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.*
import event.{Event, EventResult, KeyEvent}
import geometry.Rect
import layout.Constraint

import zio.Scope
import zio.test.*

object EventDispatcherSpec extends ZIOSpecDefault:

  private final case class Recorder(
    response:                EventResult = EventResult.Ignored,
    override val focusable:  Boolean     = true
  ) extends Component:
    private val log: scala.collection.mutable.ArrayBuffer[Event] =
      scala.collection.mutable.ArrayBuffer.empty
    def render(area: Rect, canvas: Canvas): Unit = ()
    override def handleEvent(event: Event): EventResult =
      log += event
      response
    def received: Vector[Event] = log.toVector

  private val keyA: KeyEvent = KeyEvent.CharKey('a', Set.empty)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("EventDispatcher")(

    test("KeyEvent reaches the focused component") {
      val a = Recorder()
      val b = Recorder()
      val tree = HBox(a, b)
      val layout = LayoutManager.default.resolve(tree, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        _  <- fm.focus(a.id)
        d   = EventDispatcher.make(fm)
        _  <- d.dispatch(keyA, layout, tree)
      yield assertTrue(
        a.received == Vector(keyA),
        b.received.isEmpty
      )
    },

    test("Ignored bubbles to parent; Consumed stops") {
      val child  = Recorder(response = EventResult.Ignored)
      val parent = ParentRecorder(response = EventResult.Consumed, child)
      val tree = parent
      val layout = LayoutManager.default.resolve(tree, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        _  <- fm.focus(child.id)
        d   = EventDispatcher.make(fm)
        r  <- d.dispatch(keyA, layout, tree)
      yield assertTrue(
        child.received == Vector(keyA),
        parent.received == Vector(keyA),
        r == EventResult.Consumed
      )
    },

    test("Consumed at the focused component does not bubble") {
      val child  = Recorder(response = EventResult.Consumed)
      val parent = ParentRecorder(response = EventResult.Consumed, child)
      val layout = LayoutManager.default.resolve(parent, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        _  <- fm.focus(child.id)
        d   = EventDispatcher.make(fm)
        r  <- d.dispatch(keyA, layout, parent)
      yield assertTrue(
        child.received == Vector(keyA),
        parent.received.isEmpty,
        r == EventResult.Consumed
      )
    },

    test("RequestRedraw is returned and stops propagation") {
      val child  = Recorder(response = EventResult.RequestRedraw)
      val parent = ParentRecorder(response = EventResult.Consumed, child)
      val layout = LayoutManager.default.resolve(parent, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        _  <- fm.focus(child.id)
        d   = EventDispatcher.make(fm)
        r  <- d.dispatch(keyA, layout, parent)
      yield assertTrue(
        parent.received.isEmpty,
        r == EventResult.RequestRedraw
      )
    },

    test("with no focused component, event flows to root") {
      val a = Recorder(response = EventResult.Consumed)
      val tree = HBox(a)
      val layout = LayoutManager.default.resolve(tree, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        d   = EventDispatcher.make(fm)
        // No focus set — dispatcher falls back to root.
        // Root is the HBox itself which doesn't override handleEvent (Ignored),
        // so the result is Ignored.
        r  <- d.dispatch(keyA, layout, tree)
      yield assertTrue(r == EventResult.Ignored)
    },

    test("Resize is not delivered to components") {
      val a = Recorder(response = EventResult.Consumed)
      val layout = LayoutManager.default.resolve(a, Rect(0, 0, 20, 5))
      for
        fm <- FocusManager.make
        _  <- fm.updateFocusables(layout.order)
        _  <- fm.focus(a.id)
        d   = EventDispatcher.make(fm)
        r  <- d.dispatch(Event.Resize(80, 24), layout, a)
      yield assertTrue(a.received.isEmpty, r == EventResult.Ignored)
    }
  )

  /** Parent that wraps a single child and records its own events. */
  private final case class ParentRecorder(
    response: EventResult,
    child:    Component
  ) extends Component:
    private val log: scala.collection.mutable.ArrayBuffer[Event] =
      scala.collection.mutable.ArrayBuffer.empty
    override def childLayouts(area: Rect): Seq[(Component, Rect)] =
      Seq((child, area))
    def render(area: Rect, canvas: Canvas): Unit = child.render(area, canvas)
    override def handleEvent(event: Event): EventResult =
      log += event
      response
    def received: Vector[Event] = log.toVector
