package io.github.wickedsik.wsconsole
package capabilities

import io.github.wickedsik.wsconsole.ColorDepth

case class TerminalCapabilities(
  hasColors: Boolean,
  colorDepth: ColorDepth,
  width: Int,
  height: Int,
  canResize: Boolean,
  isInteractive: Boolean,
  terminalType: TerminalType,
  environment: EnvironmentType
)

enum TerminalType {
  case JLine3
  case Ansi
  case Dumb
  case Unknown
}

enum EnvironmentType {
  case Standard
  case IDE
  case CI_CD
  case Docker
  case SSH
  case Unknown
}

object TerminalCapabilities {
  val unknown: TerminalCapabilities = TerminalCapabilities(
    hasColors = false,
    colorDepth = ColorDepth.NoColor,
    width = 80,
    height = 24,
    canResize = false,
    isInteractive = false,
    terminalType = TerminalType.Unknown,
    environment = EnvironmentType.Unknown
  )
}