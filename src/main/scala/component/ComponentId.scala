package io.github.wickedsik.wsconsole
package component

import java.util.concurrent.atomic.AtomicLong

/**
 * Framework-assigned identity for a [[Component]] instance.
 *
 * Identity is positional + structural in the rendering frame, not
 * consumer-facing. Consumers do not declare ids; the framework assigns
 * them at construction. A future "stable id across re-renders" mechanism
 * (for retained-mode patterns, animation continuity) is a follow-up.
 *
 * `LayoutResult` and `EventDispatcher` key on `ComponentId`. The id flows
 * from layout → dispatch → event delivery without escaping into
 * application code.
 */
opaque type ComponentId = Long

object ComponentId:
  private val counter = new AtomicLong(0L)

  /** Allocate a fresh id. Process-unique for the lifetime of the JVM. */
  def fresh(): ComponentId = counter.incrementAndGet()

  /** Construct from a raw `Long` (for tests / serialization round-trips). */
  def apply(value: Long): ComponentId = value

  extension (id: ComponentId) def value: Long = id
