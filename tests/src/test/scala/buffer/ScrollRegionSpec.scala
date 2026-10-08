package io.github.wickedsik.wsconsole
package buffer

import zio.Scope
import zio.test.*

object ScrollRegionSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ScrollRegion")(
    suite("construction")(
      test("accepts valid bounds") {
        val region = ScrollRegion(top = 3, bottom = 22)
        assertTrue(region.top == 3, region.bottom == 22)
      },
      test("accepts a single-row region (top == bottom)") {
        assertTrue(ScrollRegion(5, 5).height == 1)
      },
      test("rejects negative top") {
        assertTrue(scala.util.Try(ScrollRegion(-1, 5)).isFailure)
      },
      test("rejects bottom < top") {
        assertTrue(scala.util.Try(ScrollRegion(10, 5)).isFailure)
      }
    ),
    suite("height")(
      test("counts inclusive of both ends") {
        assertTrue(
          ScrollRegion(0, 0).height == 1,
          ScrollRegion(3, 22).height == 20,
          ScrollRegion(0, 79).height == 80
        )
      }
    )
  )
