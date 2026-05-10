package io.github.wickedsik.wsconsole
package component

import zio.Scope
import zio.test.*

object ComponentIdSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ComponentId")(

    test("fresh ids are unique") {
      val ids = (1 to 1000).map(_ => ComponentId.fresh()).toSet
      assertTrue(ids.size == 1000)
    },

    test("apply preserves the underlying Long") {
      val id = ComponentId(42L)
      assertTrue(id.value == 42L)
    },

    test("equality is by underlying value") {
      val a = ComponentId(7L)
      val b = ComponentId(7L)
      val c = ComponentId(8L)
      assertTrue(
        a == b,
        a != c
      )
    }
  )
