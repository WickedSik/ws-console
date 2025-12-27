package io.github.wickedsik.wsconsole
package ansi

object Color {
  object Codes {
    object Fg {
      // Standard colors (30-37)
      val Black = "\u001B[30m"
      val Red = "\u001B[31m"
      val Green = "\u001B[32m"
      val Yellow = "\u001B[33m"
      val Blue = "\u001B[34m"
      val Magenta = "\u001B[35m"
      val Cyan = "\u001B[36m"
      val White = "\u001B[37m"
      val Default = "\u001B[39m"
      // Bright colors (90-97)
      val BrightBlack = "\u001B[90m"
      val BrightRed = "\u001B[91m"
      val BrightGreen = "\u001B[92m"
      val BrightYellow = "\u001B[93m"
      val BrightBlue = "\u001B[94m"
      val BrightMagenta = "\u001B[95m"
      val BrightCyan = "\u001B[96m"
      val BrightWhite = "\u001B[97m"
    }

    object Bg {
      // Standard colors (40-47)
      val Black = "\u001B[40m"
      val Red = "\u001B[41m"
      val Green = "\u001B[42m"
      val Yellow = "\u001B[43m"
      val Blue = "\u001B[44m"
      val Magenta = "\u001B[45m"
      val Cyan = "\u001B[46m"
      val White = "\u001B[47m"
      val Default = "\u001B[49m"
      // Bright colors (100-107)
      val BrightBlack = "\u001B[100m"
      val BrightRed = "\u001B[101m"
      val BrightGreen = "\u001B[102m"
      val BrightYellow = "\u001B[103m"
      val BrightBlue = "\u001B[104m"
      val BrightMagenta = "\u001B[105m"
      val BrightCyan = "\u001B[106m"
      val BrightWhite = "\u001B[107m"
    }
  }

  object Templates {
    // 256-color foreground
    // Usage: s"\u001B[38;5;${n}m" where n = 0-255
    def Fg256(n: Int): String = {
      require(0 < n && n < 256)

      s"\u001B[38;5;${n}m"
    }

    // 256-color background
    // Usage: s"\u001B[48;5;${n}m" where n = 0-255
    def Bg256(n: Int): String = {
      require(0 < n && n < 256)

      s"\u001B[48;5;${n}m"
    }

    // True color (24-bit) foreground
    // Usage: s"\u001B[38;2;${r};${g};${b}m" where r,g,b = 0-255
    def FgRgb(r: Int, g: Int, b: Int): String = {
      require(0 < r && r < 256)
      require(0 < g && g < 256)
      require(0 < b && b < 256)

      s"\u001B[38;2;${r};${g};${b}m"
    }

    // True color (24-bit) background
    // Usage: s"\u001B[48;2;${r};${g};${b}m" where r,g,b = 0-255
    def BgRgb(r: Int, g: Int, b: Int): String = {
      require(0 < r && r < 256)
      require(0 < g && g < 256)
      require(0 < b && b < 256)

      s"\u001B[48;2;${r};${g};${b}m"
    }
  }
}
