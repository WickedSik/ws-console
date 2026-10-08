package io.github.wickedsik.wsconsole
package app

import zio.*
import zio.stream.ZStream

/**
 * Layer 7 reactive state container.
 *
 * `Ref.Synchronized` for atomic mutation and `Hub` for subscription
 * fan-out. Every `update` / `set` publishes to every active subscriber.
 *
 * `subscribe` returns a `ZStream`; the underlying `Hub.subscribe` is
 * scoped and terminates cleanly when the consuming scope closes.
 *
 * `subscribeScoped` exposes the scoped `Dequeue` so callers can confirm
 * subscription registration synchronously before any publish. The
 * `ZStream` form registers lazily on first pull, making "publish +
 * collect" tests racy — `subscribeScoped` is the deterministic form.
 */
trait State[S]:
  def get: UIO[S]
  def set(s: S): UIO[Unit]
  def update(f: S => S): UIO[Unit]
  def subscribe: ZStream[Any, Nothing, S]

  /**
   * Subscribe synchronously, returning the underlying `Dequeue[S]`
   * inside a `Scope`. Registration completes before this effect returns,
   * so callers can signal "ready" to a publisher without racing.
   *
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
   * Build a fresh `State[S]`. The subscription `Hub` uses a sliding
   * buffer — slow consumers see only the most recent values rather than
   * back-pressuring publishers.
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
