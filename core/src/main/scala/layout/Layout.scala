package io.github.wickedsik.wsconsole
package layout

/**
 * A layout: a [[Direction]] paired with an ordered sequence of
 * [[Constraint]]s. Resolved by [[LayoutEngine.resolve]] and partitioned
 * into sub-rectangles by [[LayoutEngine.split]] (or `rect.split(layout)`).
 *
 * An empty constraint sequence is valid and resolves to `Seq.empty` —
 * useful as a sentinel for "no children".
 */
final case class Layout(direction: Direction, constraints: Seq[Constraint])

object Layout:

  /** Smart constructor for a horizontal layout. Pass a `Seq` via `cs*`. */
  def horizontal(constraints: Constraint*): Layout =
    Layout(Direction.Horizontal, constraints.toSeq)

  /** Smart constructor for a vertical layout. Pass a `Seq` via `cs*`. */
  def vertical(constraints: Constraint*): Layout =
    Layout(Direction.Vertical, constraints.toSeq)
