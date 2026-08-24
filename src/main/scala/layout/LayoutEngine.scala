package io.github.wickedsik.wsconsole
package layout

import geometry.Rect

import scala.collection.mutable

/**
 * Pure constraint resolver and rectangle splitter.
 *
 * `resolve` produces sizes summing to ≤ `available`, in the order of
 * `layout.constraints`. `split` partitions a [[Rect]] along
 * `layout.direction` and returns sub-rectangles in the same coordinate
 * space as the input area (not relative).
 *
 * Algorithm — three passes plus a truncation step:
 *
 *   1. Deterministic contributions: assign sizes for `Fixed`, `Percentage`,
 *      `Bounded(Fixed)`, `Bounded(Percentage)`. For `Bounded(Fill)` assign
 *      the lower bound (or 0 if absent). Pure `Fill` and `Bounded(Fill)`
 *      cells are flagged as "Fill-wanting" with a remaining capacity.
 *   2. Iterative residual distribution: while residual > 0 and any Fill-
 *      wanting cell has remaining capacity, distribute equally; cells
 *      capped by `Bounded.max` drop out and any unused share is re-spread.
 *   3. Floor remainder fallback: if no Fill-wanting cells exist and the
 *      residual is ≤ the count of percentage cells (the upper bound for
 *      floor-rounding loss), the residual is given to the first `Percentage`
 *      (or `Bounded(Percentage)` not yet at its max). A residual larger than
 *      that count indicates user under-specification (e.g. `Percentage(40)`
 *      alone of 100); the un-allocated space is left alone.
 *
 * After the three passes, a left-to-right truncation enforces the budget:
 * total ≤ `available`. Over-subscription causes later cells to receive 0.
 *
 * `Percentage` rounding uses `floor`. Two-Fill split of an odd residual
 * gives the extra cell to the earliest Fill, in constraint order.
 */
object LayoutEngine:

  /**
   * Resolve a layout against an available extent (cells along the layout's
   * direction). Returns sizes summing to ≤ `available`, in constraint order.
   *
   * Edge cases:
   *   - empty constraints → empty result
   *   - `available <= 0`  → all sizes 0
   */
  def resolve(layout: Layout, available: Int): Seq[Int] =
    val cs = layout.constraints
    if cs.isEmpty then Seq.empty
    else if available <= 0 then Seq.fill(cs.size)(0)
    else resolveNonEmpty(cs, available)

  /**
   * Partition `area` along `layout.direction` into sub-rectangles. Returns
   * one rect per constraint in the same order. Empty cells (size 0)
   * produce zero-area rects rather than being elided — callers may filter.
   *
   * Sub-rect origins are in the same coordinate space as `area` (not
   * relative). The cross-axis extent of `area` is preserved on every
   * sub-rect.
   */
  def split(layout: Layout, area: Rect): Seq[Rect] =
    val cs = layout.constraints
    if cs.isEmpty then Seq.empty
    else
      layout.direction match
        case Direction.Horizontal =>
          val sizes = resolve(layout, area.width)
          val out = mutable.ArrayBuffer.empty[Rect]
          var x = area.x
          sizes.foreach { w =>
            out += Rect(x, area.y, w, area.height)
            x += w
          }
          out.toSeq
        case Direction.Vertical =>
          val sizes = resolve(layout, area.height)
          val out = mutable.ArrayBuffer.empty[Rect]
          var y = area.y
          sizes.foreach { h =>
            out += Rect(area.x, y, area.width, h)
            y += h
          }
          out.toSeq

  // ===== Internals =====

  private def resolveNonEmpty(cs: Seq[Constraint], available: Int): Seq[Int] =
    val n = cs.size
    val sizes = Array.fill(n)(0)
    val capacity = Array.fill(n)(0) // remaining "absorb more" capacity for Fill-wanting cells
    val isFill = Array.fill(n)(false)

    // Pass 1: deterministic contributions
    var i = 0
    while i < n do
      cs(i) match
        case Constraint.Fixed(size) =>
          sizes(i) = size

        case Constraint.Percentage(p) =>
          sizes(i) = ((available.toLong * p) / 100L).toInt

        case Constraint.Fill =>
          isFill(i) = true
          capacity(i) = Int.MaxValue

        case Constraint.Bounded(min, max, inner) =>
          inner match
            case Constraint.Fixed(size) =>
              sizes(i) = clamp(size, min, max)

            case Constraint.Percentage(p) =>
              val v = ((available.toLong * p) / 100L).toInt
              sizes(i) = clamp(v, min, max)

            case Constraint.Fill =>
              val floor = min.getOrElse(0)
              sizes(i) = floor
              isFill(i) = true
              capacity(i) = max.fold(Int.MaxValue)(m => math.max(0, m - floor))

            case _: Constraint.Bounded =>
              // construction-time validation forbids this; defensive no-op.
              ()
      i += 1

    val anyFill = (0 until n).exists(idx => isFill(idx) && capacity(idx) > 0)

    if anyFill then
      // Pass 2: iteratively distribute residual to Fill-wanting cells
      var residual = math.max(0, available - sumOf(sizes))
      var active = (0 until n).filter(idx => isFill(idx) && capacity(idx) > 0).toVector

      while residual > 0 && active.nonEmpty do
        val k = active.size
        val share = residual / k
        val leftover = residual - share * k

        if share == 0 && leftover == 0 then
          active = Vector.empty
        else
          var distributed = 0
          val nextActive = mutable.ArrayBuffer.empty[Int]
          var ord = 0
          active.foreach { idx =>
            val want = share + (if ord < leftover then 1 else 0)
            val take = math.min(want, capacity(idx))
            sizes(idx) += take
            capacity(idx) -= take
            distributed += take
            if capacity(idx) > 0 then nextActive += idx
            ord += 1
          }
          if distributed == 0 then active = Vector.empty
          else
            active = nextActive.toVector
            residual -= distributed
    else
      // Pass 3: no Fill-wanting cells — distribute floor-rounding remainder only.
      // Floor loss across N percentage cells is strictly < N (each cell loses <1
      // cell to flooring). A residual ≤ N is rounding dust; a larger residual
      // means the user under-specified (e.g. `Percentage(40)` alone of 100), and
      // the un-allocated space is left alone — not magically given to the first
      // Percentage, which would inflate it far beyond the declared share.
      val residual = available - sumOf(sizes)
      val percentCount = cs.count {
        case Constraint.Percentage(_)                           => true
        case Constraint.Bounded(_, _, Constraint.Percentage(_)) => true
        case _                                                  => false
      }
      if residual > 0 && residual <= percentCount then
        val firstPercIdx = (0 until n).find { idx =>
          cs(idx) match
            case Constraint.Percentage(_) => true
            case Constraint.Bounded(_, max, Constraint.Percentage(_)) =>
              max.fold(true)(m => sizes(idx) < m)
            case _ => false
        }
        firstPercIdx.foreach { idx =>
          cs(idx) match
            case Constraint.Bounded(_, max, _) =>
              val target = sizes(idx) + residual
              sizes(idx) = max.fold(target)(m => math.min(m, target))
            case _ =>
              sizes(idx) += residual
        }

    // Truncation: enforce total ≤ available, left-to-right
    var remaining = available
    var j = 0
    while j < n do
      val want = sizes(j)
      if want > remaining then
        sizes(j) = remaining
        remaining = 0
      else
        remaining -= want
      j += 1

    sizes.toIndexedSeq

  private def sumOf(arr: Array[Int]): Int =
    var s = 0
    var i = 0
    while i < arr.length do
      s += arr(i)
      i += 1
    s

  private def clamp(value: Int, min: Option[Int], max: Option[Int]): Int =
    val withMin = min.fold(value)(m => math.max(m, value))
    max.fold(withMin)(m => math.min(m, withMin))

/** Layer-3 extension — splits a rectangle into sub-rects per a [[Layout]]. */
extension (rect: Rect)
  def split(layout: Layout): Seq[Rect] =
    LayoutEngine.split(layout, rect)
