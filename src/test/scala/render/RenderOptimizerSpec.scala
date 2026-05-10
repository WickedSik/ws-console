package io.github.wickedsik.wsconsole
package render

import component.ComponentId
import geometry.Rect

import zio.Scope
import zio.test.*

object RenderOptimizerSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RenderOptimizer")(

    test("alwaysDirty reports every component as dirty") {
      for
        a <- RenderOptimizer.alwaysDirty.shouldRedraw(ComponentId(1L))
        b <- RenderOptimizer.alwaysDirty.shouldRedraw(ComponentId(999L))
      yield assertTrue(a, b)
    },

    test("alwaysDirty reports no specific dirty regions") {
      for
        regions <- RenderOptimizer.alwaysDirty.dirtyRegions
      yield assertTrue(regions.isEmpty)
    },

    test("alwaysDirty.markDirty / clearDirty are no-ops") {
      for
        _       <- RenderOptimizer.alwaysDirty.markDirty(Rect(0, 0, 5, 5))
        _       <- RenderOptimizer.alwaysDirty.clearDirty()
        regions <- RenderOptimizer.alwaysDirty.dirtyRegions
      yield assertTrue(regions.isEmpty)
    },

    test("regionTracking accumulates dirty rects and reports redraw needed") {
      for
        opt <- RenderOptimizer.regionTracking
        none <- opt.shouldRedraw(ComponentId(1L))
        _    <- opt.markDirty(Rect(0, 0, 5, 5))
        _    <- opt.markDirty(Rect(10, 10, 3, 3))
        any  <- opt.shouldRedraw(ComponentId(1L))
        rs   <- opt.dirtyRegions
        _    <- opt.clearDirty()
        empty <- opt.dirtyRegions
      yield assertTrue(
        !none,
        any,
        rs.size == 2,
        empty.isEmpty
      )
    }
  )
