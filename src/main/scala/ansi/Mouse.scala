package io.github.wickedsik.wsconsole
package ansi

object Mouse {
  val EnableNormal = "\u001B[?1000h" // Click events only
  val EnableButton = "\u001B[?1002h" // Click + drag events
  val EnableAny = "\u001B[?1003h" // All mouse events
  val EnableSgr = "\u001B[?1006h" // SGR extended mode
  val DisableNormal = "\u001B[?1000l"
  val DisableButton = "\u001B[?1002l"
  val DisableAny = "\u001B[?1003l"
  val DisableSgr = "\u001B[?1006l"
}
