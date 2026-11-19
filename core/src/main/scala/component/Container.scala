package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import geometry.Rect
import layout.{Constraint, Direction, Layout, LayoutEngine}

import scala.annotation.targetName

/**
 * Base for the two layout-bearing containers.
 *
 * The pair `(Constraint, Component)` is the unit of children: the
 * sizing intent travels with the child, eliminating dual-list drift
 * (constraint count vs. child count). Adding a child means adding one
 * pair; reordering preserves alignment automatically; mismatched counts
 * are structurally impossible to write.
 *
 * Two constructor shapes are exposed via the companion `apply`s:
 *   - **Explicit**: `HBox(Fixed(20) -> Panel(...), Fill -> Panel(...))`
 *     — every child carries its sizing intent
 *   - **Default**: `HBox(Panel(...), Panel(...))` — every child gets
 *     `Constraint.Fill`, equal share among them
 *
 * Mixed-form calls (some bare Components, some pairs) are rejected by
 * Scala's varargs homogeneity. This is a feature, not a limitation —
 * either every child declares its size, or none do.
 */
trait Container extends Component:
  def items: Seq[(Constraint, Component)]
  def direction: Direction

  override def childLayouts(area: Rect): Seq[(Component, Rect)] =
    if items.isEmpty then Seq.empty
    else
      // LayoutEngine.split handles zero-size areas by returning zero-rects;
      // every child therefore appears in the LayoutResult.
      val layout = Layout(direction, resolvedConstraints(area.width))
      val rects = LayoutEngine.split(layout, area)
      items.map(_._2).zip(rects)

  /**
   * The constraints handed to the engine. On the vertical axis each `Fit`
   * (bare or inside `Bounded`) becomes `Fixed(child.preferredHeight(width))`;
   * a child without a preference keeps `Fit`, which the engine solves as
   * `Fill`. The horizontal axis has no preferred width, so its constraints
   * pass through untouched.
   */
  private def resolvedConstraints(width: Int): Seq[Constraint] =
    direction match
      case Direction.Horizontal => items.map(_._1)
      case Direction.Vertical   => items.map((c, child) => Container.resolveFit(c, child, width))

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val children = childLayouts(area)
    var i = 0
    while i < children.size do
      val (child, rect) = children(i)
      child.render(rect, canvas, ctx)
      i += 1

object Container:

  /** Replace a vertical `Fit` with the child's preferred height, when it has one. */
  private[component] def resolveFit(c: Constraint, child: Component, width: Int): Constraint =
    c match
      case Constraint.Fit =>
        child.preferredHeight(width).fold(c)(Constraint.Fixed(_))
      case Constraint.Bounded(min, max, Constraint.Fit) =>
        child.preferredHeight(width).fold(c)(h => Constraint.Bounded(min, max, Constraint.Fixed(h)))
      case other => other

  /**
   * What one VBox child adds to the box's preferred height, or `None`
   * when it contributes no opinion. `Fixed` counts its size; `Fit` and
   * `Fill` count the child's own preference. A box laid out at its
   * preferred height therefore leaves a `Fill` residual equal to the
   * total its `Fill` children asked for — shared equally among them, as
   * `Fill` always is, so use `Fit` when each child needs its own height;
   * `Percentage` of an intrinsic total is circular and counts nothing.
   * `Bounded` clamps its inner contribution, falling back to its lower
   * bound when the inner has none.
   */
  private[component] def verticalContribution(c: Constraint, child: Component, width: Int): Option[Int] =
    c match
      case Constraint.Fixed(n)              => Some(n)
      case Constraint.Fit | Constraint.Fill => child.preferredHeight(width)
      case Constraint.Percentage(_)         => None
      case Constraint.Bounded(min, max, inner) =>
        verticalContribution(inner, child, width) match
          case Some(h) =>
            val atLeast = min.fold(h)(math.max(_, h))
            Some(max.fold(atLeast)(math.min(_, atLeast)))
          case None => min

/** Horizontal container — children laid out left-to-right. */
final case class HBox(items: Seq[(Constraint, Component)]) extends Container:
  val direction: Direction = Direction.Horizontal

  /**
   * The tallest child at the width it would be assigned. Widths come
   * from the same solve `childLayouts` runs, so a child measured narrow
   * here is laid out narrow there. `None` when no child has a preference.
   */
  override def preferredHeight(width: Int): Option[Int] =
    val widths = LayoutEngine.resolve(Layout(direction, items.map(_._1)), width)
    items.map(_._2).zip(widths).flatMap((child, w) => child.preferredHeight(w)).maxOption

object HBox:
  /** Empty horizontal container. */
  val empty: HBox = HBox(Seq.empty)

  /** Explicit per-child sizing. */
  @targetName("hboxFromPairs")
  def apply(items: (Constraint, Component)*): HBox =
    HBox(items.toSeq)

  /** Bare children — each gets `Constraint.Fill`, equal share. */
  @targetName("hboxFromBareChildren")
  def apply(children: Component*)(using DummyImplicit): HBox =
    HBox(children.map(Constraint.Fill -> _).toSeq)

/** Vertical container — children laid out top-to-bottom. */
final case class VBox(items: Seq[(Constraint, Component)]) extends Container:
  val direction: Direction = Direction.Vertical

  /**
   * The sum of every child's contribution (see
   * `Container.verticalContribution`). `None` when no child has an
   * opinion — a box of bare `Fill` spacers has no natural height.
   */
  override def preferredHeight(width: Int): Option[Int] =
    val contributions = items.map((c, child) => Container.verticalContribution(c, child, width))
    if contributions.forall(_.isEmpty) then None
    else Some(contributions.flatten.sum)

object VBox:
  /** Empty vertical container. */
  val empty: VBox = VBox(Seq.empty)

  /** Explicit per-child sizing. */
  @targetName("vboxFromPairs")
  def apply(items: (Constraint, Component)*): VBox =
    VBox(items.toSeq)

  /** Bare children — each gets `Constraint.Fill`, equal share. */
  @targetName("vboxFromBareChildren")
  def apply(children: Component*)(using DummyImplicit): VBox =
    VBox(children.map(Constraint.Fill -> _).toSeq)
