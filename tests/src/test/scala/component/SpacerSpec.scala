package io.github.wickedsik.wsconsole
package component

import buffer.{Cell, Canvas, ScreenBuffer}
import geometry.Rect
import zio.Scope
import zio.test.*

object SpacerSpec extends ZIOSpecDefault:

  private val ctx = RenderContext.empty

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Spacer")(
    test("renders nothing into the assigned area") {
      val buf = ScreenBuffer.of(10, 5)
      val canvas = Canvas(buf)
      Spacer.render(Rect(0, 0, 10, 5), canvas, ctx)
      // All cells remain empty
      val anyDrawn = (0 until 10).exists { x =>
        (0 until 5).exists(y => buf.get(x, y).exists(_ != Cell.Empty))
      }
      assertTrue(!anyDrawn)
    },
    test("Spacer is a singleton case object") {
      assertTrue(Spacer eq Spacer)
    }
  )
