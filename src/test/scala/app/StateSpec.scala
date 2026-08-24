package io.github.wickedsik.wsconsole
package app

import zio.*
import zio.stream.ZStream
import zio.test.*

object StateSpec extends ZIOSpecDefault:

  /**
   * Collect `n` values from a fresh subscription, signalling `registered`
   * the moment the underlying `Hub` subscription is live. The publisher
   * can then `registered.await` for a deterministic barrier — no
   * wall-clock sleeps, no fork-order races.
   */
  private def collectN(
    state: State[Int],
    registered: Promise[Nothing, Unit],
    n: Int
  ): UIO[Chunk[Int]] =
    ZIO.scoped {
      for
        dequeue <- state.subscribeScoped
        _ <- registered.succeed(())
        chunks <- ZStream.fromQueue(dequeue).take(n.toLong).runCollect
      yield chunks
    }

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
      // Deterministic: the subscriber signals `registered` after its Hub
      // subscription is live, so the publisher's first set() is guaranteed
      // to reach the queue. No wall-clock sleep, no flake.
      for
        s <- State.make(0)
        registered <- Promise.make[Nothing, Unit]
        fiber <- collectN(s, registered, 3).fork
        _ <- registered.await
        _ <- s.set(1)
        _ <- s.set(2)
        _ <- s.set(3)
        chunk <- fiber.join
      yield assertTrue(chunk.toList == List(1, 2, 3))
    },
    test("multiple subscribers each receive every update") {
      // Two subscribers each get their own registration promise; the
      // publisher awaits both before publishing. No race between fork
      // order and Hub subscription registration.
      for
        s <- State.make(0)
        reg1 <- Promise.make[Nothing, Unit]
        reg2 <- Promise.make[Nothing, Unit]
        fib1 <- collectN(s, reg1, 2).fork
        fib2 <- collectN(s, reg2, 2).fork
        _ <- reg1.await
        _ <- reg2.await
        _ <- s.set(11)
        _ <- s.set(22)
        ch1 <- fib1.join
        ch2 <- fib2.join
      yield assertTrue(
        ch1.toList == List(11, 22),
        ch2.toList == List(11, 22)
      )
    },
    test("concurrent updates serialise (Ref.Synchronized semantics)") {
      for
        s <- State.make(0)
        _ <- ZIO.foreachParDiscard(1 to 100)(_ => s.update(_ + 1))
        v <- s.get
      yield assertTrue(v == 100)
    },
    test("subscribeScoped registers synchronously and receives subsequent publishes") {
      // Directly exercises the new primitive: a value published after
      // subscribeScoped returns must reach the Dequeue.
      ZIO.scoped {
        for
          s <- State.make(0)
          dequeue <- s.subscribeScoped
          _ <- s.set(99)
          taken <- dequeue.take
        yield assertTrue(taken == 99)
      }
    }
  ) @@ TestAspect.timeout(10.seconds)
