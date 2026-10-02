package io.github.wickedsik.wsconsole
package buffer

import scala.collection.mutable.ArrayBuffer

/**
 * One visual line produced by [[TextWrap.wrap]].
 *
 * `start` and `end` are char offsets into the source's [[StyledLine.text]]:
 * `source.text.substring(start, end)` is exactly what this line draws, tabs
 * aside (a tab draws as one space). Whitespace dropped at a soft break, and
 * the `\n` of a hard break, sit in the gap before the next line's `start`.
 * A caret moving through the source maps onto visual rows through these
 * offsets.
 *
 * `hardBreak` is true when an explicit line break in the source ends this
 * line, false for a soft wrap and for the last line.
 */
final case class WrappedLine(line: StyledLine, start: Int, end: Int, hardBreak: Boolean)

/**
 * Word wrap for styled text, measured in terminal columns.
 *
 * Pure text geometry: `(spans, width) => lines`, shared by `WrappedText`,
 * `TextArea` soft wrap, and anything else that lays text into a column.
 *
 * Rules:
 *
 *   - Lines break on whitespace. A word longer than the width breaks at the
 *     last grapheme cluster that fits, after first moving to a fresh line.
 *   - Width is counted with [[Widths]]. A wide grapheme never splits and
 *     never straddles the right edge; it moves to the next line. A grapheme
 *     wider than the whole width (CJK at width 1) is placed alone on its own
 *     line rather than dropped, so nothing is lost and wrapping always
 *     terminates; `Canvas.putText` then leaves it blank.
 *   - `\n`, `\r\n` and `\r` are hard breaks. A trailing break yields a
 *     trailing empty line; consecutive breaks yield empty lines. `\r\n` is
 *     one grapheme cluster under UAX #29, so it counts as one break.
 *   - Wrapping crosses span boundaries: a word may be made of several spans,
 *     and a span continues on the next line in its own style.
 *   - Leading whitespace survives on the first line of a paragraph and is
 *     dropped on every soft-wrapped continuation. Whitespace at a soft break
 *     is not drawn. A tab is breakable whitespace and draws as one space.
 *   - A width of zero or less, or an empty source, yields no lines.
 */
object TextWrap:

  /** The marker [[ellipsize]] ends a cut line with: U+2026, one column wide. */
  val Ellipsis: String = "…"

  /** A grapheme cluster with its style, source offset, and column width. */
  final private case class G(text: String, style: CellStyle, offset: Int, width: Int):
    def isNewline: Boolean = text == "\n" || text == "\r\n" || text == "\r"
    def isSpace: Boolean = !isNewline && text.codePoints.allMatch(Character.isWhitespace(_))
    def end: Int = offset + text.length

  def wrap(source: StyledLine, width: Int): Vector[WrappedLine] =
    if width <= 0 || source.isEmpty then Vector.empty
    else
      val out = ArrayBuffer.empty[WrappedLine]
      val paragraph = ArrayBuffer.empty[G]
      var paragraphStart = 0
      graphemes(source).foreach { g =>
        if g.isNewline then
          out ++= wrapParagraph(paragraph.toVector, paragraphStart, width, hardBreak = true)
          paragraph.clear()
          paragraphStart = g.end
        else paragraph += g
      }
      out ++= wrapParagraph(paragraph.toVector, paragraphStart, width, hardBreak = false)
      out.toVector

  /**
   * `line` cut to fit `width` with [[Ellipsis]] as its last column. The
   * marker takes the style of the last span left visible.
   */
  def ellipsize(line: StyledLine, width: Int): StyledLine =
    if width <= 0 then StyledLine.Empty
    else
      val kept = line.truncate(width - 1)
      val style = kept.spans.lastOption.orElse(line.spans.headOption).fold(CellStyle.Empty)(_.style)
      kept ++ StyledLine(Vector(Span(Ellipsis, style)))

  private def graphemes(source: StyledLine): Vector[G] =
    val out = Vector.newBuilder[G]
    var offset = 0
    source.spans.foreach { span =>
      Graphemes.foreach(span.text) { g =>
        out += G(g, span.style, offset, Widths.cellsFor(g))
        offset += g.length
      }
    }
    out.result()

  /** Wrap one paragraph: the graphemes between two hard breaks. */
  private def wrapParagraph(gs: Vector[G], start: Int, width: Int, hardBreak: Boolean): Vector[WrappedLine] =
    val lines = ArrayBuffer.empty[WrappedLine]
    val cur = ArrayBuffer.empty[G]
    var curWidth = 0
    var continuation = false

    def add(g: G): Unit =
      cur += g
      curWidth += g.width

    // A soft break that would leave only whitespace (indentation wider than
    // the line) emits nothing: the text continues on the next line.
    def softBreak(): Unit =
      val kept = cur.toVector.reverse.dropWhile(_.isSpace).reverse
      if kept.nonEmpty then lines += WrappedLine(toLine(kept), kept.head.offset, kept.last.end, hardBreak = false)
      cur.clear()
      curWidth = 0
      continuation = true

    runs(gs).foreach { run =>
      if run.head.isSpace then
        run.foreach { g =>
          if continuation && cur.isEmpty then ()
          else if curWidth + g.width <= width then add(g)
          else softBreak()
        }
      else if curWidth + run.map(_.width).sum <= width then run.foreach(add)
      else
        if cur.nonEmpty then softBreak()
        run.foreach { g =>
          if cur.nonEmpty && curWidth + g.width > width then softBreak()
          add(g)
        }
    }

    if cur.nonEmpty || lines.isEmpty then
      val lineStart = cur.headOption.fold(start)(_.offset)
      val lineEnd = cur.lastOption.fold(start)(_.end)
      lines += WrappedLine(toLine(cur.toVector), lineStart, lineEnd, hardBreak)
    else lines.update(lines.size - 1, lines.last.copy(hardBreak = hardBreak))
    lines.toVector

  /** Maximal runs of all-whitespace or all-non-whitespace graphemes. */
  private def runs(gs: Vector[G]): Vector[Vector[G]] =
    val out = Vector.newBuilder[Vector[G]]
    var rest = gs
    while rest.nonEmpty do
      val space = rest.head.isSpace
      val (run, tail) = rest.span(_.isSpace == space)
      out += run
      rest = tail
    out.result()

  /** Merge adjacent same-style graphemes into spans; a tab draws as a space. */
  private def toLine(gs: Seq[G]): StyledLine =
    val spans = gs.foldLeft(Vector.empty[Span]) { (acc, g) =>
      val text = if g.text == "\t" then " " else g.text
      acc.lastOption match
        case Some(last) if last.style == g.style => acc.init :+ last.copy(text = last.text + text)
        case _                                   => acc :+ Span(text, g.style)
    }
    StyledLine(spans)
