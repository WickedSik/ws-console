package io.github.wickedsik.wsconsole
package geometry

/**
 * A rectangle in terminal coordinate space (0-indexed, top-left origin).
 *
 * Used by Layer 2 (Canvas/Buffer) for drawing regions and clipping, and by
 * Layer 3 (Layout) for component placement.
 */
final case class Rect(x: Int, y: Int, width: Int, height: Int):

  /** True when this rectangle has no area. */
  def isEmpty: Boolean = width <= 0 || height <= 0

  /** Test whether the point (px, py) lies inside this rectangle. */
  def contains(px: Int, py: Int): Boolean =
    !isEmpty &&
      px >= x && px < x + width &&
      py >= y && py < y + height

  /** Test whether this rectangle overlaps `other` (touching edges do not count). */
  def intersects(other: Rect): Boolean =
    !isEmpty && !other.isEmpty &&
      x < other.x + other.width &&
      x + width > other.x &&
      y < other.y + other.height &&
      y + height > other.y

  /** Shrink the rectangle inward by `margin` on every side. Result may be empty. */
  def inner(margin: Int): Rect =
    Rect(
      x + margin,
      y + margin,
      math.max(0, width - margin * 2),
      math.max(0, height - margin * 2)
    )

  /**
   * Shrink the rectangle inward by per-edge [[Insets]]. Result may be
   * empty when insets exceed the rectangle's dimensions.
   */
  def inner(insets: Insets): Rect =
    Rect(
      x + insets.left,
      y + insets.top,
      math.max(0, width  - insets.left - insets.right),
      math.max(0, height - insets.top  - insets.bottom)
    )
