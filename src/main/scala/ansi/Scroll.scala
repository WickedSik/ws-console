package io.github.wickedsik.wsconsole
package ansi

object Scroll:
  object Codes:
    val Up: String = s"${Csi.ESC}[S" // SU: scroll content up by 1 within active region
    val Down: String = s"${Csi.ESC}[T" // SD: scroll content down by 1 within active region
    val ResetRegion: String = s"${Csi.ESC}[r" // Reset scroll region to full screen

  object Templates:
    // Usage: s"${Csi.ESC}[${top};${bottom}r" where top, bottom = 1-indexed line numbers
    def SetRegion(top: Int, bottom: Int): String = {
      require(top > 0)
      require(bottom > 0)

      s"${Csi.ESC}[$top;${bottom}r"
    }
