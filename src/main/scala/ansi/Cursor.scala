package io.github.wickedsik.wsconsole
package ansi

object Cursor:
  object Codes:
    val Home: String = s"${Csi.ESC}[H" // Move to (1,1)
    val SaveDec: String = s"${Csi.ESC}7" // Save position (DEC - more compatible)
    val RestoreDec: String = s"${Csi.ESC}8" // Restore position (DEC)
    val SaveSco: String = s"${Csi.ESC}[s" // Save position (SCO)
    val RestoreSco: String = s"${Csi.ESC}[u" // Restore position (SCO)

  object Templates:
    // Usage: s"${Csi.ESC}[${n}A" where n = number of lines
    def Up(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}A"

    // Usage: s"${Csi.ESC}[${n}B" where n = number of lines
    def Down(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}B"

    // Usage: s"${Csi.ESC}[${n}C" where n = number of columns
    def Forward(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}C"

    // Usage: s"${Csi.ESC}[${n}D" where n = number of columns
    def Backward(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}D"

    // Usage: s"${Csi.ESC}[${n}E" where n = lines down (moves to column 1)
    def NextLine(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}E"

    // Usage: s"${Csi.ESC}[${n}F" where n = lines up (moves to column 1)
    def PrevLine(n: Int): String =
      require(n > 0)
      s"${Csi.ESC}[${n}F"

    // Usage: s"${Csi.ESC}[${col}G" where col = column number (1-indexed)
    def Column(col: Int): String =
      require(col > 0)
      s"${Csi.ESC}[${col}G"

    // Usage: s"${Csi.ESC}[${row};${col}H" where row, col are 1-indexed
    def Position(row: Int, col: Int): String =
      require(row > 0)
      require(col > 0)
      s"${Csi.ESC}[$row;${col}H"

    // Usage: s"${Csi.ESC}[${row};${col}f" - alternate form of Position
    def PositionAlt(row: Int, col: Int): String =
      require(row > 0)
      require(col > 0)
      s"${Csi.ESC}[$row;${col}f"
