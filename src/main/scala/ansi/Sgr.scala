package io.github.wickedsik.wsconsole
package ansi

/**
 * A Select Graphic Rendition sequence held as its parameter list rather than
 * its rendered bytes.
 *
 * SGR is the only CSI family that accepts an unbounded parameter list, so it
 * is the only one where several changes collapse into one escape:
 * `ESC[1;3;38;2;255;128;0m` carries bold, italic and a truecolour foreground
 * in a single sequence. Cursor movement, erase, scroll regions and mode
 * setting each own a distinct final byte and one meaning, and cannot merge.
 *
 * Composition is list concatenation via [[++]]; rendering happens once, at
 * the wire edge. Two properties follow from carrying parameters instead of
 * bytes:
 *
 *   - '''Ordering belongs to the caller.''' Whatever order a caller composes
 *     in is the order emitted, so a canonical traversal yields canonical
 *     bytes. This is what let [[buffer.CellStyle]] close KI-001.
 *   - '''Merging saves two bytes per group.''' Each separate escape costs
 *     three bytes of framing (ESC, `[`, `m`); merging replaces that with one
 *     `;`.
 *
 * [[Empty]] renders to the empty string, never to `ESC[m`. An SGR with no
 * parameters means "reset all attributes", so an empty style must not be
 * allowed to silently reset the terminal.
 *
 * @param params SGR parameter codes, emitted left-to-right
 */
final case class Sgr(params: Vector[Int]):

  /** Concatenate two parameter lists so both render as one escape. */
  def ++(other: Sgr): Sgr = Sgr(params ++ other.params)

  def isEmpty: Boolean = params.isEmpty

  def nonEmpty: Boolean = params.nonEmpty

  /** Render as a single escape, or the empty string when there are no parameters. */
  def toAnsi: String =
    if params.isEmpty then "" else s"${Csi.ESC}[${params.mkString(";")}m"

object Sgr:

  /** No parameters. Renders to the empty string, not to `ESC[m`. */
  val Empty: Sgr = Sgr(Vector.empty)

  /** Reset all attributes and colours (SGR 0). */
  val Reset: Sgr = Sgr(Vector(0))

  // ===== Text attributes =====

  val Bold: Sgr = Sgr(Vector(1))
  val Dim: Sgr = Sgr(Vector(2))
  val Italic: Sgr = Sgr(Vector(3))
  val Underline: Sgr = Sgr(Vector(4))
  val Blink: Sgr = Sgr(Vector(5))
  val Reverse: Sgr = Sgr(Vector(7))
  val Hidden: Sgr = Sgr(Vector(8))
  val Strikethrough: Sgr = Sgr(Vector(9))

  // ===== Attribute disables =====

  /** Disables bold and dim together — the ANSI spec gives them one off-switch. */
  val NoBoldDim: Sgr = Sgr(Vector(22))
  val NoItalic: Sgr = Sgr(Vector(23))
  val NoUnderline: Sgr = Sgr(Vector(24))
  val NoBlink: Sgr = Sgr(Vector(25))
  val NoReverse: Sgr = Sgr(Vector(27))
  val NoHidden: Sgr = Sgr(Vector(28))
  val NoStrikethrough: Sgr = Sgr(Vector(29))

  // ===== Colours =====

  /** A single-parameter sequence, for the named colour codes of [[AnsiColor]]. */
  def code(n: Int): Sgr = Sgr(Vector(n))

  /** 256-colour palette foreground (0-255). */
  def fg256(n: Int): Sgr =
    requireByte(n, "Color index")
    Sgr(Vector(38, 5, n))

  /** 256-colour palette background (0-255). */
  def bg256(n: Int): Sgr =
    requireByte(n, "Color index")
    Sgr(Vector(48, 5, n))

  /** 24-bit truecolour foreground. */
  def fgRgb(r: Int, g: Int, b: Int): Sgr =
    requireRgb(r, g, b)
    Sgr(Vector(38, 2, r, g, b))

  /** 24-bit truecolour background. */
  def bgRgb(r: Int, g: Int, b: Int): Sgr =
    requireRgb(r, g, b)
    Sgr(Vector(48, 2, r, g, b))

  private def requireByte(value: Int, name: String): Unit =
    require(value >= 0 && value <= 255, s"$name must be 0-255, got $value")

  private def requireRgb(r: Int, g: Int, b: Int): Unit =
    requireByte(r, "Red")
    requireByte(g, "Green")
    requireByte(b, "Blue")
