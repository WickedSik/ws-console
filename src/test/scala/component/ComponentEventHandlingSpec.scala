package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import event.{Event, EventResult, KeyEvent, KeyModifier}
import geometry.Rect

import zio.Scope
import zio.test.*

object ComponentEventHandlingSpec extends ZIOSpecDefault:

  /** Minimal stub component for handler tests. */
  private final case class StubComponent(
    override val focusable: Boolean        = false,
    handler:                Event => EventResult = _ => EventResult.Ignored
  ) extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()
    override def handleEvent(event: Event, ctx: RenderContext): EventResult = handler(event)

  private val sampleKey: KeyEvent = KeyEvent.CharKey('a', Set.empty)
  private val ctx                 = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Component event handling")(

    test("default handleEvent returns Ignored") {
      assertTrue(StubComponent().handleEvent(sampleKey, ctx) == EventResult.Ignored)
    },

    test("default focusable is false") {
      assertTrue(!StubComponent().focusable)
    },

    test("override returning Consumed wins over default") {
      val c = StubComponent(handler = _ => EventResult.Consumed)
      assertTrue(c.handleEvent(sampleKey, ctx) == EventResult.Consumed)
    },

    test("override returning RequestRedraw is preserved") {
      val c = StubComponent(handler = _ => EventResult.RequestRedraw)
      assertTrue(c.handleEvent(sampleKey, ctx) == EventResult.RequestRedraw)
    },

    test("each construction allocates a fresh id") {
      val a = StubComponent()
      val b = StubComponent()
      assertTrue(a.id != b.id)
    }
  )
