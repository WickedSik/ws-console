package io.github.wickedsik.wsconsole
package app

import zio.*
import zio.test.*

object StateSpec extends ZIOSpecDefault:

  def spec: Spec[TestEnvironment & Scope, Any] = suite("State")(

    test("get after set returns the new value") {
      for
        s <- State.make(0)
        _ <- s.set(42)
        v <- s.get
      yield assertTrue(v == 42)
    },

    test("update applies the function transactionally") {
      for
        s <- State.make(10)
        _ <- s.update(_ + 1)
        _ <- s.update(_ * 2)
        v <- s.get
      yield assertTrue(v == 22)
    },

    test("subscribe emits every published update") {
      // Fork the subscriber to give the Hub subscription time to register
      // before the main fiber publishes. Hub does not retain past values
      // for late subscribers — registration order is load-bearing.
      for
        s     <- State.make(0)
        fiber <- s.subscribe.take(3).runCollect.fork
        _     <- ZIO.sleep(150.millis)
        _     <- s.set(1)
        _     <- s.set(2)
        _     <- s.set(3)
        chunk <- fiber.join
      yield assertTrue(chunk.toList == List(1, 2, 3))
    } @@ TestAspect.withLiveClock,

    test("multiple subscribers each receive every update") {
      // Two subscribers race the publisher; both must register before set.
      // Each subscription is on its own forked fiber, awaited on a barrier
      // promise to confirm registration before publish.
      for
        s        <- State.make(0)
        // Run both subscribers on forked fibers; the publish only starts
        // after a real-time delay long enough to register both.
        _        <- (ZIO.sleep(75.millis) *>
                     s.set(11)            *>
                     s.set(22)).fork
        fiber1   <- s.subscribe.take(2).runCollect.fork
        fiber2   <- s.subscribe.take(2).runCollect.fork
        chunk1   <- fiber1.join
        chunk2   <- fiber2.join
      yield assertTrue(
        chunk1.toList == List(11, 22),
        chunk2.toList == List(11, 22)
      )
    } @@ TestAspect.withLiveClock,

    test("concurrent updates serialise (Ref.Synchronized semantics)") {
      for
        s <- State.make(0)
        _ <- ZIO.foreachParDiscard(1 to 100)(_ => s.update(_ + 1))
        v <- s.get
      yield assertTrue(v == 100)
    }
  ) @@ TestAspect.timeout(10.seconds)
