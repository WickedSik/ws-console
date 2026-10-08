package io.github.wickedsik.wsconsole
package buffer

/**
 * A single unit of work emitted by the buffer's diff stream.
 *
 * Cell ops mutate individual screen positions; region ops manage the
 * active hardware scroll region (DECSTBM). Each op is self-contained:
 * it carries everything [[BufferFlusher]] needs to translate it into
 * ANSI, and everything [[Frame]] needs to mirror its effect onto
 * `previous`.
 */
enum RenderOp:

  /** Place `cell` at (x, y). Coordinates are 0-indexed with origin at top-left. */
  case Cell(x: Int, y: Int, cell: buffer.Cell)

  /** Declare a hardware scroll region. */
  case SetScrollRegion(region: ScrollRegion)

  /** Tear down the active hardware scroll region. */
  case ResetScrollRegion

  /**
   * Append a styled line at the bottom of the active region; the terminal
   * performs the scroll. The op carries its own region so the flusher can
   * position the cursor and the [[Frame]] can mirror `previous`'s state.
   */
  case ScrollRegionLine(region: ScrollRegion, line: Line)
