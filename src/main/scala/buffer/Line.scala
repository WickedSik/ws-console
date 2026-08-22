package io.github.wickedsik.wsconsole
package buffer

import geometry.Rect

/**
 * A positionless 1D primitive: an ordered sequence of styled cells.
 *
 * Used as the payload of [[RenderOp.ScrollRegionLine]] and as the unit of
 * work for `ScrollableCanvas.appendLine`. The vertical position is supplied
 * by the consumer at submit time; horizontal extent is `cells.length`.
 *
 * A line of `n` cells anchored at `(x, y)` occupies the rectangle
 * `Rect(x, y, n, 1)`. The companion provides both directions of conversion
 * to and from [[Rect]].
 */
final case class Line(cells: Seq[Cell]):

  /** Horizontal extent in cells. */
  def width: Int = cells.length

  /** Project this line into a [[Rect]] at the given anchor (height = 1). */
  def at(x: Int, y: Int): Rect = Rect(x, y, cells.length, 1)

object Line:

  /** A line of zero cells. */
  val Empty: Line = Line(Seq.empty)

  /**
   * Build a line from a string and a uniform style. Width is measured in
   * grapheme clusters, not `Char`s — so `"🧹 done".length` (6 UTF-16 units)
   * produces a 5-cell line ("🧹" is one grapheme). See buffer/Graphemes.scala.
   */
  def text(s: String, style: CellStyle = CellStyle.Empty): Line =
    val builder = Seq.newBuilder[Cell]
    Graphemes.foreach(s)(g => builder += Cell(g, style))
    Line(builder.result())

  /** Build a line from pre-styled cells. */
  def cells(cs: Seq[Cell]): Line = Line(cs)

  /** Build a line from heterogeneously-styled runs. Width is per-grapheme. */
  def runs(rs: (String, CellStyle)*): Line =
    val builder = Seq.newBuilder[Cell]
    rs.foreach { case (s, style) => Graphemes.foreach(s)(g => builder += Cell(g, style)) }
    Line(builder.result())

  /**
   * Build a line filling a height-1 [[Rect]] with `fill`. Width matches `rect.width`.
   * Throws [[IllegalArgumentException]] if `rect.height != 1`.
   */
  def fill(rect: Rect, fill: Cell = Cell.Empty): Line =
    require(rect.height == 1, s"Line.fill requires a height-1 Rect, got height ${rect.height}")
    Line(Seq.fill(rect.width)(fill))
