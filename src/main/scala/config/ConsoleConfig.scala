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

case class PatternConfig(
  action: (String, String) = ("*", "*"),           // *action* → formatted
  speech: (String, String) = ("\"", "\""),         // "speech" → formatted
  intro: (String, String) = ("<intro>", "</intro>"), // <intro>text</intro> → formatted
  emphasis: (String, String) = ("_", "_"),         // _emphasis_ → formatted
  code: (String, String) = ("`", "`")              // `code` → formatted
)

object PatternConfig {
  val default: PatternConfig = PatternConfig()
}

case class ColorScheme(
  action: String = "magenta_bold",
  speech: String = "blue",
  intro: String = "yellow",
  emphasis: String = "italic",
  code: String = "cyan",
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