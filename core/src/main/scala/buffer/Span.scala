package io.github.wickedsik.wsconsole
package buffer

import glyph.Widths

/** A run of text drawn in one style. */
final case class Span(text: String, style: CellStyle = CellStyle.Empty)

/**
 * A sequence of [[Span]]s laid left to right: one logical line of styled
 * text. Measuring and truncating go by terminal columns ([[Widths]]), never
 * by `String.length`, so a CJK or emoji span takes the columns it draws.
 *
 * A `StyledLine` may hold `\n`; [[TextWrap.wrap]] treats it as a hard
 * break. Single-line consumers (`Text`) draw it as-is.
 */
final case class StyledLine(spans: Vector[Span]):

  /** The text of every span, concatenated. */
  def text: String = spans.map(_.text).mkString

  /** Width in terminal columns. */
  def width: Int = spans.map(s => Widths.stringWidth(s.text)).sum

  def isEmpty: Boolean = spans.forall(_.text.isEmpty)

  def ++(other: StyledLine): StyledLine = StyledLine(spans ++ other.spans)

  /** Every span's style laid over `base` (see [[CellStyle.over]]). */
  def over(base: CellStyle): StyledLine =
    StyledLine(spans.map(s => s.copy(style = s.style.over(base))))

  /**
   * The longest prefix, in whole grapheme clusters, that fits in `columns`.
   * Stops at the first span that had to be cut: a wide grapheme left out at
   * the limit must not let a later, narrower span fill its column out of order.
   */
  def truncate(columns: Int): StyledLine =
    val out = Vector.newBuilder[Span]
    var remaining = columns
    var cut = false
    for span <- spans if !cut do
      val kept = Widths.truncate(span.text, remaining)
      if kept.nonEmpty then out += span.copy(text = kept)
      remaining -= Widths.stringWidth(kept)
      cut = kept.length < span.text.length
    StyledLine(out.result())

  /**
   * Draw the spans left to right starting at `(x, y)`, each in its own
   * style, advancing by each span's column width.
   */
  def draw(canvas: Canvas, x: Int, y: Int): Unit =
    var col = x
    spans.foreach { span =>
      canvas.putText(col, y, span.text, span.style)
      col += Widths.stringWidth(span.text)
    }

object StyledLine:
  val Empty: StyledLine = StyledLine(Vector.empty)

/**
 * Builds a [[StyledLine]] from plain strings and styled spans; a plain
 * string becomes an unstyled span, which inherits the component's base
 * style:
 *
 * {{{
 * Spans("Name", Span("*", required))
 * }}}
 */
object Spans:
  def apply(parts: (String | Span)*): StyledLine =
    StyledLine(parts.toVector.map {
      case s: String => Span(s)
      case s: Span   => s
    })
