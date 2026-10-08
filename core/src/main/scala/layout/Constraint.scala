package io.github.wickedsik.wsconsole
package layout

/**
 * Declarative sizing requirement along a single axis.
 *
 * Five cases:
 *
 *   - [[Constraint.Fixed]]      — exact size in cells
 *   - [[Constraint.Percentage]] — fraction of available space, in `[0, 100]`
 *   - [[Constraint.Fill]]       — singleton; takes whatever remains; multiple
 *     `Fill` cells share the residual equally
 *   - [[Constraint.Bounded]]    — wraps another constraint with optional
 *     lower/upper bounds. Used to express e.g. "30% but at least 20 cells"
 *     via `Constraint.atLeast(20, Constraint.Percentage(30))`. Nesting
 *     `Bounded` inside `Bounded` is rejected at construction.
 *
 * Validation occurs at construction; invalid inputs throw
 * [[IllegalArgumentException]].
 */
sealed trait Constraint

object Constraint:

  final case class Fixed(size: Int) extends Constraint:
    require(size >= 0, s"Fixed.size must be >= 0, got $size")

  final case class Percentage(percent: Int) extends Constraint:
    require(percent >= 0 && percent <= 100, s"Percentage.percent must be in [0, 100], got $percent")

  case object Fill extends Constraint

  final case class Bounded(min: Option[Int], max: Option[Int], inner: Constraint) extends Constraint:
    require(
      min.isDefined || max.isDefined,
      "Bounded must have at least one of min/max defined"
    )
    min.foreach(m => require(m >= 0, s"Bounded.min must be >= 0, got $m"))
    max.foreach(m => require(m >= 0, s"Bounded.max must be >= 0, got $m"))
    (min, max) match
      case (Some(a), Some(b)) =>
        require(a <= b, s"Bounded.min must be <= max, got min=$a max=$b")
      case _ => ()
    inner match
      case _: Bounded =>
        throw new IllegalArgumentException("Bounded cannot wrap Bounded")
      case _ => ()

  /** Wrap `inner` with a lower bound. */
  def atLeast(min: Int, inner: Constraint): Constraint =
    Bounded(Some(min), None, inner)

  /** Wrap `inner` with an upper bound. */
  def atMost(max: Int, inner: Constraint): Constraint =
    Bounded(None, Some(max), inner)

  /** Wrap `inner` with both lower and upper bounds. */
  def bounded(min: Int, max: Int, inner: Constraint): Constraint =
    Bounded(Some(min), Some(max), inner)
