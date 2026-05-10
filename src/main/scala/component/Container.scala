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
  def items:     Seq[(Constraint, Component)]
  def direction: Direction

  override def childLayouts(area: Rect): Seq[(Component, Rect)] =
    if items.isEmpty then Seq.empty
    else
      // LayoutEngine.split handles zero-size areas by returning zero-rects;
      // every child therefore appears in the LayoutResult.
      val layout = Layout(direction, items.map(_._1))
      val rects  = LayoutEngine.split(layout, area)
      items.map(_._2).zip(rects)

  override def render(area: Rect, canvas: Canvas): Unit =
    val children = childLayouts(area)
    var i = 0
    while i < children.size do
      val (child, rect) = children(i)
      child.render(rect, canvas)
      i += 1

/** Horizontal container — children laid out left-to-right. */
final case class HBox(items: Seq[(Constraint, Component)]) extends Container:
  val direction: Direction = Direction.Horizontal

object HBox:
  /** Empty horizontal container. */
  val empty: HBox = new HBox(Seq.empty)

  /** Explicit per-child sizing. */
  @targetName("hboxFromPairs")
  def apply(items: (Constraint, Component)*): HBox =
    new HBox(items.toSeq)

  /** Bare children — each gets `Constraint.Fill`, equal share. */
  @targetName("hboxFromBareChildren")
  def apply(children: Component*)(using DummyImplicit): HBox =
    new HBox(children.map(Constraint.Fill -> _).toSeq)

/** Vertical container — children laid out top-to-bottom. */
final case class VBox(items: Seq[(Constraint, Component)]) extends Container:
  val direction: Direction = Direction.Vertical

object VBox:
  /** Empty vertical container. */
  val empty: VBox = new VBox(Seq.empty)

  /** Explicit per-child sizing. */
  @targetName("vboxFromPairs")
  def apply(items: (Constraint, Component)*): VBox =
    new VBox(items.toSeq)

  /** Bare children — each gets `Constraint.Fill`, equal share. */
  @targetName("vboxFromBareChildren")
  def apply(children: Component*)(using DummyImplicit): VBox =
    new VBox(children.map(Constraint.Fill -> _).toSeq)
