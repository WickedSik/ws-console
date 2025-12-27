package io.github.wickedsik.wsconsole
package ansi

sealed trait AnsiColor:
  def code: Int
  def toAnsi: String = s"\u001B[${code}m"

private object AnsiColor {
  def validateRGB(r: Int, g: Int, b: Int): Unit = {
    require(r >= 0 && r <= 255, s"Red must be 0-255, got $r")
    require(g >= 0 && g <= 255, s"Green must be 0-255, got $g")
    require(b >= 0 && b <= 255, s"Blue must be 0-255, got $b")
  }
}

enum FgColor(val code: Int) extends AnsiColor:
  // Standard colors (30-37)
  case Black extends FgColor(30)
  case Red extends FgColor(31)
  case Green extends FgColor(32)
  case Yellow extends FgColor(33)
  case Blue extends FgColor(34)
  case Magenta extends FgColor(35)
  case Cyan extends FgColor(36)
  case White extends FgColor(37)
  case Default extends FgColor(39)
  // Bright colors (90-97)
  case BrightBlack extends FgColor(90)
  case BrightRed extends FgColor(91)
  case BrightGreen extends FgColor(92)
  case BrightYellow extends FgColor(93)
  case BrightBlue extends FgColor(94)
  case BrightMagenta extends FgColor(95)
  case BrightCyan extends FgColor(96)
  case BrightWhite extends FgColor(97)

enum BgColor(val code: Int) extends AnsiColor:
  // Standard colors (40-47)
  case Black extends BgColor(40)
  case Red extends BgColor(41)
  case Green extends BgColor(42)
  case Yellow extends BgColor(43)
  case Blue extends BgColor(44)
  case Magenta extends BgColor(45)
  case Cyan extends BgColor(46)
  case White extends BgColor(47)
  case Default extends BgColor(49)
  // Bright colors (100-107)
  case BrightBlack extends BgColor(100)
  case BrightRed extends BgColor(101)
  case BrightGreen extends BgColor(102)
  case BrightYellow extends BgColor(103)
  case BrightBlue extends BgColor(104)
  case BrightMagenta extends BgColor(105)
  case BrightCyan extends BgColor(106)
  case BrightWhite extends BgColor(107)

object Color {
  object Templates {
    // 256-color foreground
    // Usage: Color.Templates.Fg256(196) for bright red
    def Fg256(n: Int): String = {
      require(n >= 0 && n <= 255, s"Color index must be 0-255, got $n")
      s"\u001B[38;5;${n}m"
    }

    // 256-color background
    // Usage: Color.Templates.Bg256(196) for bright red background
    def Bg256(n: Int): String = {
      require(n >= 0 && n <= 255, s"Color index must be 0-255, got $n")
      s"\u001B[48;5;${n}m"
    }

    // True color (24-bit) foreground
    // Usage: Color.Templates.FgRgb(255, 128, 0) for orange
    def FgRgb(r: Int, g: Int, b: Int): String = {
      AnsiColor.validateRGB(r, g, b)

      s"\u001B[38;2;${r};${g};${b}m"
    }

    // True color (24-bit) background
    // Usage: Color.Templates.BgRgb(255, 128, 0) for orange background
    def BgRgb(r: Int, g: Int, b: Int): String = {
      AnsiColor.validateRGB(r, g, b)

      s"\u001B[48;2;${r};${g};${b}m"
    }
  }
}
