package io.github.wickedsik.wsconsole
package ansi

object Mouse:
  val EnableNormal: String = s"${Csi.ESC}[?1000h" // Click events only
  val EnableButton: String = s"${Csi.ESC}[?1002h" // Click + drag events
  val EnableAny: String = s"${Csi.ESC}[?1003h" // All mouse events
  val EnableSgr: String = s"${Csi.ESC}[?1006h" // SGR extended mode
  val DisableNormal: String = s"${Csi.ESC}[?1000l"
  val DisableButton: String = s"${Csi.ESC}[?1002l"
  val DisableAny: String = s"${Csi.ESC}[?1003l"
  val DisableSgr: String = s"${Csi.ESC}[?1006l"
