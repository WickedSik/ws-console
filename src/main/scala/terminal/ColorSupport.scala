package io.github.wickedsik.wsconsole
package terminal

/**
 * Level of color support available in the terminal.
 *
 * Detection performed by TerminalFactory based on TERM, COLORTERM,
 * and other environment indicators.
 */
enum ColorSupport:
  /** No color support - monochrome only */
  case NoColor

  /** Standard 16-color ANSI palette (8 colors + bright variants) */
  case Basic16

  /** Extended 256-color palette (16 ANSI + 216 color cube + 24 grayscale) */
  case Extended256

  /** 24-bit true color (16.7 million colors via RGB) */
  case TrueColor
