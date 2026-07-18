package io.github.wickedsik.wsconsole
package testkit

import ansi.{BgColor, FgColor}
import buffer.{Attribute, Background, Cell, CellStyle, Foreground, ScreenBuffer}

/**
 * Decodes the ANSI byte stream emitted by `buffer.BufferFlusher` back into a
 * cell grid. Generalises `FrameRefreshSpec.positionsAddressed`: it recovers not
 * just the addressed positions but the glyph and a STRUCTURALLY-parsed style
 * per cell, so wire-level tests can assert "the diff emitted glyph G (styled S)
 * at (x, y)" and round a flushed frame back through [[GridAssertions.assertGrid]].
 *
 * '''KI-001.''' Style is rebuilt from SGR parameter codes into a `CellStyle`
 * value and compared structurally — never by string-comparing `toAnsi`. This is
 * the ONE decoder the known-issue's "byte-level snapshot" trigger permits,
 * because it does not depend on the order attributes were emitted in.
 *
 * '''Partial-map contract.''' `BufferFlusher` emits only the cells the diff
 * produced. Over a steady-state frame the result is a partial map (unchanged
 * cells are absent); a complete grid requires the frame to have been
 * invalidated first (`Frame.invalidate`), so every position is re-emitted.
 *
 * Scope: models the plain `RenderOp.Cell` grammar (`moveTo + reset + style +
 * glyph`). `ScrollRegionLine` ops (several glyphs after a single `moveTo`) are
 * not reconstructed.
 */
object AnsiGrid:

  private val Esc: Char = '\u001B'

  /** Decode the emitted cell ops into a position → cell map. */
  def decode(bytes: String): Map[(Int, Int), Cell] =
    val result = scala.collection.mutable.Map.empty[(Int, Int), Cell]
    var pending: Option[(Int, Int)] = None
    var style  = new StyleAcc
    var i      = 0
    val n      = bytes.length
    while i < n do
      val c = bytes.charAt(i)
      if c == Esc && i + 1 < n && bytes.charAt(i + 1) == '[' then
        var j = i + 2
        while j < n && !bytes.charAt(j).isLetter do j += 1
        val params    = bytes.substring(i + 2, j)
        val finalByte = if j < n then bytes.charAt(j) else ' '
        finalByte match
          case 'H' =>
            params.split(";") match
              case Array(rowS, colS) =>
                pending = Some((colS.toInt - 1, rowS.toInt - 1))
                style   = new StyleAcc
              case _ => ()
          case 'm' =>
            val ps = if params.isEmpty then List(0) else params.split(";").toList.map(_.toInt)
            applyParams(ps, style)
          case _ => () // other CSI (e.g. `[2J` clear-screen) — not a cell
        i = j + 1
      else
        pending match
          case Some(pos) =>
            result(pos) = Cell(c, style.toCellStyle)
            pending = None
          case None => ()
        i += 1
    result.toMap

  /**
   * Decode into a fresh `width × height` buffer; positions the diff did not
   * emit stay [[buffer.Cell.Empty]]. Lets a flushed frame round-trip through
   * [[GridAssertions.assertGrid]].
   */
  def decodeToBuffer(bytes: String, width: Int, height: Int): ScreenBuffer =
    val buffer = ScreenBuffer.of(width, height)
    decode(bytes).foreach { case ((x, y), cell) => buffer.set(x, y, cell) }
    buffer

  // ===== structural SGR parsing =====

  private final class StyleAcc:
    var fg: Foreground = Foreground.Inherit
    var bg: Background = Background.Inherit
    private val attrs  = scala.collection.mutable.Set.empty[Attribute]

    def reset(): Unit =
      fg = Foreground.Inherit
      bg = Background.Inherit
      attrs.clear()

    def add(a: Attribute): Unit = attrs += a

    def toCellStyle: CellStyle = CellStyle(fg, bg, attrs.toSet)

  private def applyParams(ps: List[Int], style: StyleAcc): Unit =
    ps match
      case Nil => ()
      case 0 :: rest =>
        style.reset(); applyParams(rest, style)
      case 38 :: 5 :: n :: rest =>
        style.fg = Foreground.Indexed(n); applyParams(rest, style)
      case 38 :: 2 :: r :: g :: b :: rest =>
        style.fg = Foreground.Rgb(r, g, b); applyParams(rest, style)
      case 48 :: 5 :: n :: rest =>
        style.bg = Background.Indexed(n); applyParams(rest, style)
      case 48 :: 2 :: r :: g :: b :: rest =>
        style.bg = Background.Rgb(r, g, b); applyParams(rest, style)
      case code :: rest =>
        attributeOf(code).foreach(style.add)
        fgNamedOf(code).foreach(fg => style.fg = fg)
        bgNamedOf(code).foreach(bg => style.bg = bg)
        applyParams(rest, style)

  private def attributeOf(code: Int): Option[Attribute] = code match
    case 1 => Some(Attribute.Bold)
    case 2 => Some(Attribute.Dim)
    case 3 => Some(Attribute.Italic)
    case 4 => Some(Attribute.Underline)
    case 5 => Some(Attribute.Blink)
    case 7 => Some(Attribute.Reverse)
    case 9 => Some(Attribute.Strikethrough)
    case _ => None

  private def fgNamedOf(code: Int): Option[Foreground] =
    FgColor.values.find(_.code == code).map(Foreground.Named(_))

  private def bgNamedOf(code: Int): Option[Background] =
    BgColor.values.find(_.code == code).map(Background.Named(_))
