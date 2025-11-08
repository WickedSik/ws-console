package io.github.wickedsik.wsconsole
package capabilities

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
  case Rich      // Advanced terminal with full feature support (future: JLine3-based)
  case Ansi      // Standard ANSI escape code support
  case Dumb      // Minimal terminal with no special features
  case Unknown   // Unable to determine terminal type
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