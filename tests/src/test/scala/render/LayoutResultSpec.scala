package io.github.wickedsik.wsconsole
package render

import buffer.Canvas
import component.{Component, ComponentId, RenderContext}
import geometry.Rect

import zio.Scope
import zio.test.*

object LayoutResultSpec extends ZIOSpecDefault:

  final private class Stub extends Component:
    def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit = ()

  private val root = Stub()
  private val middle = Stub()
  private val leaf = Stub()

  /** root ← middle ← leaf, laid out in that order. */
  private val layout = LayoutResult(
    rects = Map(root.id -> Rect(0, 0, 10, 10), middle.id -> Rect(0, 0, 10, 5), leaf.id -> Rect(0, 0, 10, 1)),
    parents = Map(middle.id -> root.id, leaf.id -> middle.id),
    order = Vector(root, middle, leaf),
    focusOrder = FocusOrder.empty
  )

  private def ids(cs: Iterator[Component]): List[ComponentId] = cs.map(_.id).toList

  def spec: Spec[TestEnvironment & Scope, Any] = suite("LayoutResult")(
    suite("selfAndAncestors")(
      test("walks from the component to the root, innermost first") {
        assertTrue(ids(layout.selfAndAncestors(leaf.id)) == List(leaf.id, middle.id, root.id))
      },
      test("is the root alone for the root") {
        assertTrue(ids(layout.selfAndAncestors(root.id)) == List(root.id))
      },
      test("is empty for an id that is not laid out") {
        assertTrue(layout.selfAndAncestors(ComponentId(-1)).isEmpty)
      }
    ),
    suite("ancestorsOf")(
      test("leaves the component itself out") {
        assertTrue(ids(layout.ancestorsOf(leaf.id)) == List(middle.id, root.id))
      },
      test("is empty for the root") {
        assertTrue(layout.ancestorsOf(root.id).isEmpty)
      },
      test("stops at a parent that is not laid out") {
        val orphaned = layout.copy(order = Vector(middle, leaf))
        assertTrue(ids(orphaned.ancestorsOf(leaf.id)) == List(middle.id))
      }
    )
  )
