package io.github.wickedsik.wsconsole
package buffer

import ansi.{BgColor, FgColor, Sgr}

/**
 * Text rendition attribute. Each attribute carries the SGR parameters that
 * enable it; disabling is performed by emitting a Reset before redrawing.
 *
 * '''Declaration order is emission order.''' [[CellStyle.sgr]] traverses
 * `Attribute.values`, so the order the cases appear in below is the order
 * their parameters reach the wire. Reordering them changes emitted bytes
 * (not rendering — terminals process SGR parameters left-to-right).
 */
enum Attribute(val sgr: Sgr):
  case Bold extends Attribute(Sgr.Bold)
  case Dim extends Attribute(Sgr.Dim)
  case Italic extends Attribute(Sgr.Italic)
  case Underline extends Attribute(Sgr.Underline)
  case Blink extends Attribute(Sgr.Blink)
  case Reverse extends Attribute(Sgr.Reverse)
  case Strikethrough extends Attribute(Sgr.Strikethrough)

  /** This attribute as a standalone escape. Prefer [[sgr]] when composing. */
  def ansiCode: String = sgr.toAnsi

private inline def requireByte(value: Int, name: String): Unit =
  require(value >= 0 && value <= 255, s"$name must be 0-255, got $value")

/**
 * Foreground paint for a Cell. Sealed so equality, pattern matching, and
 * hashing are well-defined for buffer diffing.
 */
sealed trait Foreground:
  /** This paint as SGR parameters, for merging into a combined sequence. */
  def sgr: Sgr

  /** This paint as a standalone escape. Prefer [[sgr]] when composing. */
  def toAnsi: String = sgr.toAnsi

object Foreground:
  case object Inherit extends Foreground:
    val sgr: Sgr = Sgr.Empty

  final case class Named(color: FgColor) extends Foreground:
    def sgr: Sgr = color.sgr

  final case class Indexed(n: Int) extends Foreground:
    requireByte(n, "n")
    def sgr: Sgr = Sgr.fg256(n)

  final case class Rgb(r: Int, g: Int, b: Int) extends Foreground:
    requireByte(r, "r")
    requireByte(g, "g")
    requireByte(b, "b")
    def sgr: Sgr = Sgr.fgRgb(r, g, b)

/**
 * Background paint for a Cell. Sealed for the same reasons as [[Foreground]].
 */
sealed trait Background:
  /** This paint as SGR parameters, for merging into a combined sequence. */
  def sgr: Sgr

  /** This paint as a standalone escape. Prefer [[sgr]] when composing. */
  def toAnsi: String = sgr.toAnsi

object Background:
  case object Inherit extends Background:
    val sgr: Sgr = Sgr.Empty

  final case class Named(color: BgColor) extends Background:
    def sgr: Sgr = color.sgr

  final case class Indexed(n: Int) extends Background:
    requireByte(n, "n")
    def sgr: Sgr = Sgr.bg256(n)

  final case class Rgb(r: Int, g: Int, b: Int) extends Background:
    requireByte(r, "r")
    requireByte(g, "g")
    requireByte(b, "b")
    def sgr: Sgr = Sgr.bgRgb(r, g, b)

/**
 * Structured visual style attached to a Cell. Diff-friendly value type that
 * converts to a single leading ANSI sequence on demand. The caller is
 * responsible for resetting when transitioning between styles — prefix
 * [[ansi.Sgr.Reset]] onto [[sgr]] to fold that reset into the same escape.
 */
final case class CellStyle(
  fg: Foreground = Foreground.Inherit,
  bg: Background = Background.Inherit,
  attributes: Set[Attribute] = Set.empty
):
  /**
   * This style's SGR parameters in canonical order: attributes in
   * `Attribute` declaration order, then foreground, then background.
   *
   * The traversal is over `Attribute.values` rather than over `attributes`
   * itself precisely because `Set` iteration order is unspecified. Equal
   * styles therefore produce identical bytes on every run, which is what
   * makes wire-level assertions and style caching safe (KI-001).
   */
  def sgr: Sgr =
    val withAttributes = Attribute.values.foldLeft(Sgr.Empty): (acc, attribute) =>
      if attributes.contains(attribute) then acc ++ attribute.sgr else acc
    withAttributes ++ fg.sgr ++ bg.sgr

  /** Render as the single leading ANSI escape for this style. */
  def toAnsi: String = sgr.toAnsi

object CellStyle:
  val Empty: CellStyle = CellStyle()
