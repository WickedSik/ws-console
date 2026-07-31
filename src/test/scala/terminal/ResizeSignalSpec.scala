package io.github.wickedsik.wsconsole
package terminal

import zio.Scope
import zio.test.*

/**
 * The watcher's value is that it lets the render loop skip a subprocess
 * fork on every tick, so what matters here is that installation never
 * fails and that an unsignalled watcher reports nothing pending. The
 * signal-delivery path itself is not exercised — raising a real `SIGWINCH`
 * at the process would be a side effect on the test runner's own terminal.
 */
object ResizeSignalSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ResizeSignal")(

    test("install succeeds and reports a definite mode") {
      for
        watcher <- ResizeSignal.install
      yield assertTrue(
        watcher.mode == ResizeSignal.Watcher.Mode.Native ||
          watcher.mode == ResizeSignal.Watcher.Mode.Unavailable
      )
    },

    test("a freshly-installed watcher has nothing pending") {
      for
        watcher <- ResizeSignal.install
        first   <- watcher.pending
      yield assertTrue(!first)
    },

    test("pending stays false while no signal arrives") {
      for
        watcher <- ResizeSignal.install
        reads   <- watcher.pending.replicateZIO(5)
      yield assertTrue(reads.forall(_ == false))
    }
  )
