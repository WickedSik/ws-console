package io.github.wickedsik.wsconsole
package terminal

import zio.*

import java.util.concurrent.atomic.AtomicBoolean

/**
 * OS-level resize notification via `SIGWINCH`.
 *
 * Exists because [[Terminal.size]] is expensive: on the ANSI backend it
 * forks `/bin/sh -c "stty size < /dev/tty"`, and a render loop polling it
 * on a frame-scale cadence spawns hundreds of processes a minute. The
 * kernel already knows when the window changed; this asks it.
 *
 * The handler runs on the JVM's signal-dispatch thread, so it does the
 * least possible work — sets a flag. Consumers read and rearm the flag
 * with [[Watcher.pending]] and only then pay for a real size query.
 *
 * Installation is best-effort. `sun.misc.Signal` lives in the
 * `jdk.unsupported` module and may be absent or blocked by a security
 * policy; when that happens the watcher reports
 * [[Watcher.Mode.Unavailable]] and consumers fall back to querying
 * [[Terminal.size]] directly on a slower cadence. There is no third
 * option — the JVM exposes no portable window-size API.
 *
 * The handler is installed for the life of the process and is never
 * uninstalled. It replaces any previously-registered `SIGWINCH` handler,
 * which for a TUI that owns the terminal is the intended behaviour.
 */
private[wsconsole] object ResizeSignal:

  /**
   * Reads whether the terminal has been resized since the last check.
   * Cheap enough to call on every frame tick — no process, no syscall
   * beyond an atomic read.
   */
  trait Watcher:
    /**
     * Returns `true` when at least one `SIGWINCH` has arrived since the
     * previous call, and rearms. Coalescing is deliberate: a burst of
     * signals during a drag-resize collapses into one size query.
     *
     * Always returns `false` under [[Mode.Unavailable]] — there is no
     * signal source to report from.
     */
    def pending: UIO[Boolean]

    /** Whether the OS handler is live, and therefore whether [[pending]] can be trusted. */
    def mode: Watcher.Mode

  object Watcher:
    enum Mode:
      /** `SIGWINCH` handler installed — query size only when `pending` says so. */
      case Native

      /** No handler; `pending` is always `false` and callers must poll size directly. */
      case Unavailable

  /**
   * Install the handler. Never fails — an unusable signal facility
   * degrades to [[Watcher.Mode.Unavailable]] rather than taking down the
   * render loop.
   */
  def install: UIO[Watcher] =
    ZIO.succeed:
      val flag = new AtomicBoolean(false)

      val installed =
        try
          val handler = new sun.misc.SignalHandler:
            def handle(received: sun.misc.Signal): Unit = flag.set(true)
          sun.misc.Signal.handle(new sun.misc.Signal("WINCH"), handler)
          true
        catch
          // IllegalArgumentException (unknown signal), SecurityException,
          // NoClassDefFoundError (module not present) — all mean the same
          // thing to us, and none of them justify failing startup.
          case _: Throwable => false

      new Watcher:
        val mode: Watcher.Mode = if installed then Watcher.Mode.Native else Watcher.Mode.Unavailable

        def pending: UIO[Boolean] = ZIO.succeed(flag.getAndSet(false))
