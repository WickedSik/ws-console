package io.github.wickedsik.wsconsole
package app

import zio.*
import zio.stream.ZStream

/**
 * Layer 7 reactive state container — ZIO-native (Q1 ratified).
 *
 * Backed by `Ref.Synchronized` for atomic state mutation and `Hub` for
 * subscription fan-out. Every `update` / `set` publishes the new value
 * to every active subscriber.
 *
 * `subscribe` returns a `ZStream`; multiple subscribers each receive
 * every update. The underlying `Hub.subscribe` is scoped — the stream
 * cleanly terminates when its consuming scope closes.
 *
 * `subscribeScoped` exposes the underlying scoped `Dequeue`, letting
 * callers synchronously confirm subscription registration before any
 * publish happens. The `ZStream`-shaped `subscribe` registers lazily on
 * first pull, which makes "publish + collect" tests racy under load —
 * `subscribeScoped` is the deterministic primitive.
 *
 * The shape is deliberately minimal — no middleware, no action ADT,
 * no reducer. Consumers that need structured dispatch build it on top.
 */
trait State[S]:
  def get:                UIO[S]
  def set(s: S):          UIO[Unit]
  def update(f: S => S):  UIO[Unit]
  def subscribe:          ZStream[Any, Nothing, S]

  /**
   * Subscribe synchronously, returning the underlying `Dequeue[S]`
   * inside a `Scope`. The subscription is registered the moment this
   * effect completes — callers may then signal "ready" to a publisher
   * without relying on wall-clock timing to guess when the
   * `ZStream`-shaped `subscribe` has registered its consumer.
   *
   * Typical pattern:
   * {{{
   * ZIO.scoped {
   *   for
   *     dq      <- state.subscribeScoped
   *     _       <- registered.succeed(())
   *     results <- ZStream.fromQueue(dq).take(n).runCollect
   *   yield results
   * }
   * }}}
   */
  def subscribeScoped: ZIO[Scope, Nothing, Dequeue[S]]

object State:

  /** Default subscription `Hub` capacity. */
  val DefaultHubCapacity: Int = 16

  /**
   * Build a fresh `State[S]` with an initial value. The subscription `Hub`
   * uses a sliding buffer of `hubCapacity` — slow consumers see only the
   * most recent values rather than back-pressuring publishers.
   */
  def make[S](initial: S, hubCapacity: Int = DefaultHubCapacity): UIO[State[S]] =
    for
      ref <- Ref.Synchronized.make(initial)
      hub <- Hub.sliding[S](hubCapacity)
    yield new State[S]:

      def get: UIO[S] = ref.get

      def set(s: S): UIO[Unit] =
        ref.updateZIO(_ => ZIO.succeed(s)) *> hub.publish(s).unit

      def update(f: S => S): UIO[Unit] =
        ref.updateAndGetZIO(s => ZIO.succeed(f(s))).flatMap(newVal => hub.publish(newVal).unit)

      def subscribe: ZStream[Any, Nothing, S] =
        ZStream.fromHub(hub)

      def subscribeScoped: ZIO[Scope, Nothing, Dequeue[S]] =
        hub.subscribe
