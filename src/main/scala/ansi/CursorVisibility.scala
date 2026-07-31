package io.github.wickedsik.wsconsole
package ansi

object CursorVisibility:
  val Hide: String = s"${Csi.ESC}[?25l"
  val Show: String = s"${Csi.ESC}[?25h"
