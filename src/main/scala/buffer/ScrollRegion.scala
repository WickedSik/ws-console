package io.github.wickedsik.wsconsole
package buffer

/**
 * A row range declaring a hardware scroll region (DECSTBM).
 *
 * Coordinates are 0-indexed, inclusive on both ends. `top` and `bottom`
 * refer to terminal rows; column extent is implicitly full-width
 * (DECSLRM column margins are out of scope — see decision 6 in
 * `.claude/tasks/scrollable-canvas-and-scroll-regions.md`).
 *
 * Validation against canvas dimensions happens at the call site (e.g.
 * `Canvas.scrollRegion`); this type only enforces structural invariants.
 */
final case class ScrollRegion(top: Int, bottom: Int):
  require(top >= 0, s"top must be >= 0, got $top")
  require(bottom >= top, s"bottom must be >= top, got top=$top bottom=$bottom")

  /** Number of rows in the region (inclusive of both ends). */
  def height: Int = bottom - top + 1
