package io.github.wickedsik.wsconsole
package terminal

import zio.Chunk

/**
 * Raw input received from the terminal.
 *
 * Layer 5's Event parser will consume RawInput.Bytes and interpret escape
 * sequences into KeyEvent, MouseEvent, etc. This sealed trait avoids any
 * dependency on Layer 5 types while giving it a clean contract to build on.
 */
sealed trait RawInput

object RawInput:
  /** Raw bytes read from terminal input */
  case class Bytes(data: Chunk[Byte]) extends RawInput

  /** Read timed out with no input available */
  case object Timeout extends RawInput

  /** Input stream has been closed */
  case object EndOfInput extends RawInput
