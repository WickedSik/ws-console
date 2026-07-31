package io.github.wickedsik.wsconsole
package render

import component.ComponentId

/**
 * Strategy for reconciling the previously-focused component id against
 * a newly-installed `FocusOrder`. Invoked by `FocusManager.setOrder`
 * when the order changes between frames.
 *
 * Library primitive — the choice belongs to the consumer, not the
 * library. Different consumer categories have legitimately different
 * needs:
 *   - Forms: focus drops when an input is removed (`DropOnRemoval`)
 *   - Dashboards: focus rolls forward to keep something selected
 *     (`MoveToFirstOnRemoval`)
 *   - Modal stacks: focus restores to caller on dismissal (`custom`)
 *
 * Pure: implementations must not perform I/O. Reconciliation is called
 * inside `FocusManager`'s state-update path on the render loop's fiber.
 *
 * '''Idempotence is required.''' For any `f` and `order`:
 * {{{
 *   reconcile(reconcile(f, order), order) == reconcile(f, order)
 * }}}
 * `FocusManager.setOrder` schedules a redraw whenever reconciliation
 * changes the focused id, and that redraw calls `setOrder` again with
 * the same order. A non-idempotent policy therefore drives an unbounded
 * render loop. Both bundled policies satisfy this trivially: each maps
 * an id already present in `order` to itself.
 */
trait FocusPolicy:
  def reconcile(
    previouslyFocused: Option[ComponentId],
    newOrder:          FocusOrder
  ): Option[ComponentId]

object FocusPolicy:

  /**
   * Drop focus when the focused widget is no longer in the cycle.
   * Honest semantics: "the focused thing went away, so there is no
   * focus." Consumer must explicitly re-engage via Tab or programmatic
   * `focus(id)`. Equivalent to the pre-v1 `FocusManager.updateFocusables`
   * behaviour.
   */
  val DropOnRemoval: FocusPolicy = new FocusPolicy:
    def reconcile(previouslyFocused: Option[ComponentId], newOrder: FocusOrder): Option[ComponentId] =
      previouslyFocused.filter(newOrder.contains)

  /**
   * Move focus to the first entry of the new order when the focused
   * widget is no longer in the cycle. Friendlier default for the common
   * always-something-focused TUI shape (toolbars, navigation bars,
   * dashboards with a persistent focus root). When the new order is
   * empty, focus drops to `None`.
   */
  val MoveToFirstOnRemoval: FocusPolicy = new FocusPolicy:
    def reconcile(previouslyFocused: Option[ComponentId], newOrder: FocusOrder): Option[ComponentId] =
      previouslyFocused
        .filter(newOrder.contains)
        .orElse(newOrder.entries.headOption.map(_.id))

  /**
   * Custom reconciliation — supply a function for cases the named
   * policies do not cover: restore to a last-known id, move to a
   * nearest sibling, preserve a focus stack across modal transitions,
   * accessibility-driven focus shifts, etc.
   */
  def custom(f: (Option[ComponentId], FocusOrder) => Option[ComponentId]): FocusPolicy =
    new FocusPolicy:
      def reconcile(previouslyFocused: Option[ComponentId], newOrder: FocusOrder): Option[ComponentId] =
        f(previouslyFocused, newOrder)
