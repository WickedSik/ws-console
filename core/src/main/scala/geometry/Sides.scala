package io.github.wickedsik.wsconsole
package geometry

/**
 * Per-edge visibility flags for a rectangular frame.
 *
 * The geometry primitive behind selective border rendering. An edge with
 * `true` costs one cell along that edge; an edge with `false` consumes
 * none. Corner glyphs on a rendered border only appear where their two
 * adjacent edges are both visible — a suppressed corner cell is filled
 * by whichever edge glyph still runs through it, or by the background
 * when both adjacent edges are absent.
 *
 * Component composition mirrors [[Insets]]: `area.inner(sides.toInsets)`
 * yields the content region a partial frame leaves behind.
 */
final case class Sides(top: Boolean, right: Boolean, bottom: Boolean, left: Boolean):
  def isEmpty: Boolean = !(top || right || bottom || left)
  def nonEmpty: Boolean = !isEmpty

  /**
   * Per-edge cell cost as [[Insets]]. `true` on an edge costs one cell,
   * `false` costs none — the same shape framed components use to derive
   * their child region via `area.inner(sides.toInsets)`.
   */
  def toInsets: Insets = Insets(
    if top then 1 else 0,
    if right then 1 else 0,
    if bottom then 1 else 0,
    if left then 1 else 0
  )

object Sides:
  val all: Sides = Sides(top = true, right = true, bottom = true, left = true)
  val none: Sides = Sides(top = false, right = false, bottom = false, left = false)

  /**
   * Horizontal enables the left and right edges; vertical enables the
   * top and bottom edges. Mirrors [[Insets.symmetric]] — same axis names,
   * same field ordering under the hood.
   */
  def symmetric(horizontal: Boolean, vertical: Boolean): Sides =
    Sides(top = vertical, right = horizontal, bottom = vertical, left = horizontal)
