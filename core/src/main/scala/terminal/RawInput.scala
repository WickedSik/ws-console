package io.github.wickedsik.wsconsole
package terminal

import zio.Chunk

/**
 * Raw input received from the terminal.
 *
 * Layer 5's Event parser will consume RawInput.Bytes and interpret escape
 * sequences into KeyEvent, MouseEvent, etc. This enum avoids any
 * dependency on Layer 5 types while giving it a clean contract to build on.
 */
enum RawInput:
  /** Raw bytes read from terminal input */
  case Bytes(data: Chunk[Byte])

  /** Read timed out with no input available */
  case Timeout

  /** Input stream has been closed */
  case EndOfInput
