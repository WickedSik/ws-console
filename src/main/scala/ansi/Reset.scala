package io.github.wickedsik.wsconsole
package ansi

object Reset:
  val Full: String = s"${Csi.ESC}c" // RIS - Reset to Initial State
