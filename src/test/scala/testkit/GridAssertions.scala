package io.github.wickedsik.wsconsole
package testkit

import buffer.{Cell, CellStyle, ScreenBuffer}

import zio.test.*

import RenderHarness.glyphGrid

/**
 * Value-based visual assertions over a rendered [[buffer.ScreenBuffer]].
 *
 * '''KI-001.''' `CellStyle.toAnsi` emits its `Set[Attribute]` in
 * non-deterministic order (`docs/known-issues.md`), so NO assertion here
 * compares ANSI byte strings. The glyph grid ([[assertGrid]]) compares
 * characters only; style is asserted by structural `Cell`/`CellStyle`
 * equality ([[assertCell]] / [[assertStyle]]). Do NOT add a raw-ANSI
 * styled-cell snapshot until KI-001 is fixed — it would be flaky across
 * JVM runs for any multi-attribute style.
 */
object GridAssertions:

  /**
   * Assert the buffer's glyph grid matches `expected` — a multi-line block
   * where every blank is the sentinel '.'. Leading/trailing framing blank
   * lines (from a triple-quoted literal) are ignored. On mismatch, both
   * grids are shown with the first differing coordinate marked.
   *
   * Content whose glyph is literally the sentinel is rejected by
   * [[RenderHarness.glyphGrid]] (it is ambiguous with padding); assert such a
   * cell with [[assertCell]] / [[assertChar]] instead.
   */
  def assertGrid(buffer: ScreenBuffer, expected: String): TestResult =
    val actual = buffer.glyphGrid
    val wanted = normalize(expected)
    if actual == wanted then assertCompletes
    else assertTrue(mismatchReport(wanted, actual) == GridsMatch)

  /** Assert the exact cell (glyph AND style) at (x, y). Value-based. */
  def assertCell(buffer: ScreenBuffer, x: Int, y: Int, expected: Cell): TestResult =
    assertTrue(buffer.get(x, y).contains(expected))

  /** Assert the exact style at (x, y), ignoring the glyph. Value-based. */
  def assertStyle(buffer: ScreenBuffer, x: Int, y: Int, expected: CellStyle): TestResult =
    assertTrue(buffer.get(x, y).map(_.style).contains(expected))

  /** Assert the glyph at (x, y), ignoring the style. */
  def assertChar(buffer: ScreenBuffer, x: Int, y: Int, expected: Char): TestResult =
    assertTrue(buffer.get(x, y).map(_.char).contains(expected))

  // ===== internals =====

  private val GridsMatch = "✓ grids match"

  /** Split into rows and drop framing blank lines from a triple-quoted literal. */
  private def normalize(expected: String): Vector[String] =
    val lines = expected.split("\n", -1).toVector
    lines.dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse

  private def optChar(s: String, x: Int): Option[Char] =
    if x >= 0 && x < s.length then Some(s.charAt(x)) else None

  private def firstMismatch(expected: Vector[String], actual: Vector[String]): Option[(Int, Int)] =
    val rows = math.max(expected.length, actual.length)
    var found: Option[(Int, Int)] = None
    var y = 0
    while found.isEmpty && y < rows do
      val e = if y < expected.length then expected(y) else ""
      val a = if y < actual.length then actual(y) else ""
      val cols = math.max(e.length, a.length)
      var x = 0
      while found.isEmpty && x < cols do
        if optChar(e, x) != optChar(a, x) then found = Some((x, y))
        x += 1
      y += 1
    found

  private def mismatchReport(expected: Vector[String], actual: Vector[String]): String =
    val marker = firstMismatch(expected, actual) match
      case Some((x, y)) => s"first mismatch at (x=$x, y=$y)"
      case None         => "grids differ in row/column count"
    s"grid mismatch — $marker\n\n${block("expected", expected)}\n\n${block("actual", actual)}"

  private def block(label: String, rows: Vector[String]): String =
    s"$label (${rows.length} rows):\n" + rows.mkString("\n")
