package io.github.wickedsik.wsconsole
package ansi

object Cursor:
  object Codes:
    val Home = "\u001B[H" // Move to (1,1)
    val SaveDec = "\u001B7" // Save position (DEC - more compatible)
    val RestoreDec = "\u001B8" // Restore position (DEC)
    val SaveSco = "\u001B[s" // Save position (SCO)
    val RestoreSco = "\u001B[u" // Restore position (SCO)

  object Templates:
    // Usage: s"\u001B[${n}A" where n = number of lines
    def Up(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}A"
    }

    // Usage: s"\u001B[${n}B" where n = number of lines
    def Down(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}B"
    }

    // Usage: s"\u001B[${n}C" where n = number of columns
    def Forward(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}C"
    }

    // Usage: s"\u001B[${n}D" where n = number of columns
    def Backward(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}D"
    }

    // Usage: s"\u001B[${n}E" where n = lines down (moves to column 1)
    def NextLine(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}E"
    }

    // Usage: s"\u001B[${n}F" where n = lines up (moves to column 1)
    def PrevLine(n: Int): String = {
      require(n > 0)

      s"\u001B[${n}F"
    }

    // Usage: s"\u001B[${col}G" where col = column number (1-indexed)
    def Column(col: Int): String = {
      require(col > 0)

      s"\u001B[${col}G"
    }

    // Usage: s"\u001B[${row};${col}H" where row, col are 1-indexed
    def Position(row: Int, col: Int): String = {
      require(row > 0)
      require(col > 0)

      s"\u001B[${row};${col}H"
    }

    // Usage: s"\u001B[${row};${col}f" - alternate form of Position
    def PositionAlt(row: Int, col: Int): String = {
      require(row > 0)
      require(col > 0)

      s"\u001B[${row};${col}f"
    }
