package io.github.wickedsik.wsconsole
package buffer

import ansi.{BgColor, Color, FgColor, Style as AnsiStyle}

/**
 * Text rendition attribute. Each attribute carries the ANSI escape that
 * enables it; disabling is performed by emitting a Reset before redrawing.
 */
enum Attribute(val ansiCode: String):
  case Bold          extends Attribute(AnsiStyle.Bold)
  case Dim           extends Attribute(AnsiStyle.Dim)
  case Italic        extends Attribute(AnsiStyle.Italic)
  case Underline     extends Attribute(AnsiStyle.Underline)
  case Blink         extends Attribute(AnsiStyle.Blink)
  case Reverse       extends Attribute(AnsiStyle.Reverse)
  case Strikethrough extends Attribute(AnsiStyle.Strikethrough)

private inline def requireByte(value: Int, name: String): Unit =
  require(value >= 0 && value <= 255, s"$name must be 0-255, got $value")

/**
 * Foreground paint for a Cell. Sealed so equality, pattern matching, and
 * hashing are well-defined for buffer diffing.
 */
sealed trait Foreground:
  def toAnsi: String

object Foreground:
  case object Inherit extends Foreground:
    val toAnsi: String = ""

  final case class Named(color: FgColor) extends Foreground:
    def toAnsi: String = color.toAnsi

  final case class Indexed(n: Int) extends Foreground:
    requireByte(n, "n")
    def toAnsi: String = Color.Templates.Fg256(n)

  final case class Rgb(r: Int, g: Int, b: Int) extends Foreground:
    requireByte(r, "r")
    requireByte(g, "g")
    requireByte(b, "b")
    def toAnsi: String = Color.Templates.FgRgb(r, g, b)

/**
 * Background paint for a Cell. Sealed for the same reasons as [[Foreground]].
 */
sealed trait Background:
  def toAnsi: String

object Background:
  case object Inherit extends Background:
    val toAnsi: String = ""

  final case class Named(color: BgColor) extends Background:
    def toAnsi: String = color.toAnsi

  final case class Indexed(n: Int) extends Background:
    requireByte(n, "n")
    def toAnsi: String = Color.Templates.Bg256(n)

  final case class Rgb(r: Int, g: Int, b: Int) extends Background:
    requireByte(r, "r")
    requireByte(g, "g")
    requireByte(b, "b")
    def toAnsi: String = Color.Templates.BgRgb(r, g, b)

/**
 * Structured visual style attached to a Cell. Diff-friendly value type that
 * converts to a leading ANSI sequence on demand. The caller is responsible
 * for emitting [[ansi.Style.Reset]] when transitioning between styles.
 */
final case class CellStyle(
  fg:         Foreground     = Foreground.Inherit,
  bg:         Background     = Background.Inherit,
  attributes: Set[Attribute] = Set.empty
):
  /** Render as the leading ANSI escape sequence for this style. */
  def toAnsi: String =
    val sb = StringBuilder()
    attributes.foreach(a => sb.append(a.ansiCode))
    sb.append(fg.toAnsi)
    sb.append(bg.toAnsi)
    sb.toString

object CellStyle:
  val Empty: CellStyle = CellStyle()
