package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, CellStyle}

/**
 * State modulation for interactive components.
 *
 * Visual meaning splits across two axes: a semantic role carried by a
 * consumer-supplied [[CellStyle]], and an interaction state the
 * component derives from focus and its local state. The role owns hue;
 * the state owns intensity and attributes.
 *
 * Each modulation adds a rendition [[Attribute]] — never touches `fg`
 * or `bg`. Additions are idempotent (set semantics); existing
 * attributes on the base are preserved.
 *
 * Conflicting attributes (`Bold` + `Dim`) are terminal-defined. Combos
 * that would conflict (`focused` on a `disabled` widget) should be
 * prevented at the widget layer — a disabled widget does not focus.
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
