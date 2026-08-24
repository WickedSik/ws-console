package io.github.wickedsik.wsconsole
package testkit

import buffer.{Canvas, CellStyle, ScreenBuffer}
import component.{Component, RenderContext}
import geometry.Rect

/**
 * Pure render harness — collapses `ScreenBuffer.of` + `Canvas` +
 * `render` into a single call, plus glyph-grid / style projections for
 * readable assertions.
 *
 * '''Empty-cell policy.''' The glyph grid renders every space as the
 * sentinel '.' so trailing padding is visible in a string literal. To
 * assert an exact space or style, use [[GridAssertions.assertCell]] /
 * [[GridAssertions.assertStyle]]. A rendered glyph literally equal to
 * the sentinel would be ambiguous with padding, so [[glyphGrid]] rejects
 * it rather than silently conflating the two.
 */
object RenderHarness:

  /** The visible stand-in for a blank (space) cell in the glyph grid. */
  val EmptySentinel: Char = '.'

  /**
   * Render `component` into a fresh `width × height` buffer and return it.
   * `area` defaults to the whole buffer; `ctx` to the empty focus context.
   *
   * Buffer dimensions must be positive (`ScreenBuffer.of` requires it), but
   * the render `area` may be empty — an empty area is a rendering no-op, as
   * the component contract requires, so the two are kept independent.
   */
  def renderToBuffer(width: Int, height: Int)(
    component: Component,
    area: Rect = Rect(0, 0, width, height),
    ctx: RenderContext = RenderContext.empty
  ): ScreenBuffer =
    val buffer = ScreenBuffer.of(width, height)
    component.render(area, Canvas(buffer), ctx)
    buffer

  extension (buffer: ScreenBuffer)

    /**
     * Each row as a string; every space (empty cell) becomes [[EmptySentinel]].
     *
     * Fails fast (`IllegalArgumentException`) on a cell whose glyph is literally
     * the sentinel: in a char-only grid it is indistinguishable from padding, so
     * conflating the two silently would be a false-negative. Assert such content
     * with [[GridAssertions.assertCell]] / [[GridAssertions.assertChar]].
     */
    def glyphGrid: Vector[String] =
      (0 until buffer.height).map { y =>
        (0 until buffer.width).map { x =>
          buffer.get(x, y).map(_.char) match
            case Some(' ') => EmptySentinel
            case Some(EmptySentinel) =>
              throw new IllegalArgumentException(
                s"glyphGrid: cell ($x, $y) holds a literal '$EmptySentinel', indistinguishable " +
                  "from the empty-cell sentinel — assert it with assertCell/assertChar instead."
              )
            case Some(c) => c
            case None    => EmptySentinel
        }.mkString
      }.toVector

    /** The glyph grid joined into one multi-line block — the readable snapshot. */
    def renderGrid: String = buffer.glyphGrid.mkString("\n")

    /** The style at (x, y), or `None` out of bounds. */
    def styleAt(x: Int, y: Int): Option[CellStyle] = buffer.get(x, y).map(_.style)

    /** The char at (x, y), or `None` out of bounds. */
    def charAt(x: Int, y: Int): Option[Char] = buffer.get(x, y).map(_.char)
