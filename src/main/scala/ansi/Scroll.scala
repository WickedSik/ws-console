package io.github.wickedsik.wsconsole
package ansi

object Scroll:
  object Codes:
    val Up          = "\u001B[S" // SU: scroll content up by 1 within active region
    val Down        = "\u001B[T" // SD: scroll content down by 1 within active region
    val ResetRegion = "\u001B[r" // Reset scroll region to full screen

  object Templates:
    // Usage: s"\u001B[${top};${bottom}r" where top, bottom = 1-indexed line numbers
    def SetRegion(top: Int, bottom: Int): String = {
      require(top > 0)
      require(bottom > 0)

      s"\u001B[$top;${bottom}r"
    }
