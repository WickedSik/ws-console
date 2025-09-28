package io.github.wickedsik.wsconsole
package config

case class ConsoleConfig(
  patterns: PatternConfig = PatternConfig.default,
  colors: ColorScheme = ColorScheme.default,
  behavior: BehaviorConfig = BehaviorConfig.default
)

object ConsoleConfig {
  val default: ConsoleConfig = ConsoleConfig()
}

// TODO: Future enhancement - support for themes that can override both patterns and colors together
// TODO: Consider supporting custom pattern definitions via configuration files
case class PatternConfig(
  primary: (String, String) = ("*", "*"),        // *text* → Primary emphasis
  secondary: (String, String) = ("_", "_"),      // _text_ → Secondary emphasis
  strong: (String, String) = ("**", "**"),       // **text** → Strong emphasis
  code: (String, String) = ("`", "`"),           // `text` → Code/literal text
  quoted: (String, String) = ("\"", "\""),       // "text" → Quoted text
  tagged: (String, String) = ("<", ">"),         // <text> → Tagged sections
  marked: (String, String) = ("==", "==")        // ==text== → Marked/highlighted
)

object PatternConfig {
  val default: PatternConfig = PatternConfig()
}

// TODO: Future enhancement - support for theme presets (e.g., "solarized", "monokai", "dracula")
// TODO: Consider RGB/hex color support for terminals that support true color
case class ColorScheme(
  primary: String = "magenta_bold",    // Maps to primary pattern
  secondary: String = "italic",        // Maps to secondary pattern
  strong: String = "bold",             // Maps to strong pattern
  code: String = "cyan",               // Maps to code pattern
  quoted: String = "blue",             // Maps to quoted pattern
  tagged: String = "yellow",           // Maps to tagged pattern
  marked: String = "reverse",          // Maps to marked pattern
  // Semantic colors for messages
  error: String = "red",
  success: String = "green",
  warning: String = "yellow",
  info: String = "blue"
)

object ColorScheme {
  val default: ColorScheme = ColorScheme()
}

case class BehaviorConfig(
  defaultWidth: Int = 80,
  enableColors: Boolean = true,
  enableWrapping: Boolean = true,
  enablePatterns: Boolean = true,
  forceAnsi: Boolean = false,
  forceJLine: Boolean = false,
  silentFallback: Boolean = true
)

object BehaviorConfig {
  val default: BehaviorConfig = BehaviorConfig()
}