package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, CellStyle}

/**
 * State modulation for interactive components.
 *
 * The styleguide splits visual meaning across two orthogonal axes
 * (§2.1 and §2.2): a **semantic role** carried by a [[CellStyle]] the
 * consumer supplies, and an **interaction state** the component derives
 * from framework-owned focus and its own transient local state. §2.3's
 * composition rule fixes their relationship:
 *
 * > The role owns hue, the state owns intensity and attributes.
 *
 * Each modulation therefore adds a rendition [[Attribute]] to the base
 * style's set — never touches `fg` or `bg`. The additions are idempotent
 * (set semantics), so nested applications collapse without duplicating
 * an already-present attribute. Existing attributes on the base are
 * preserved.
 *
 * Modulations do not compose commutatively where a terminal renders
 * conflicting attributes (`Bold` + `Dim`, for example, is
 * terminal-defined). Combinations that would conflict — such as
 * `focused` on a `disabled` widget — should be prevented at the widget
 * layer: a disabled component does not participate in focus.
 *
 * A consumer-overridable modulation (per-role, per-state) belongs in a
 * future `Theme` service on [[RenderContext]] and is deferred until a
 * real consumer earns the extension point.
 */
object InteractionState:

  /** Holds focus. Adds [[Attribute.Bold]] to the base. */
  def focused(base: CellStyle): CellStyle =
    base.copy(attributes = base.attributes + Attribute.Bold)

  /** Present but non-interactive. Adds [[Attribute.Dim]] to the base. */
  def disabled(base: CellStyle): CellStyle =
    base.copy(attributes = base.attributes + Attribute.Dim)

  /** Momentary press feedback. Adds [[Attribute.Reverse]] to the base. */
  def active(base: CellStyle): CellStyle =
    base.copy(attributes = base.attributes + Attribute.Reverse)

  /** Chosen within a set. Adds [[Attribute.Reverse]] to the base. */
  def selected(base: CellStyle): CellStyle =
    base.copy(attributes = base.attributes + Attribute.Reverse)
