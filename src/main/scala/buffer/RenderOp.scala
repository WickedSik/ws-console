package io.github.wickedsik.wsconsole
package buffer

/**
 * A single unit of work emitted by the buffer's diff stream.
 *
 * Replaces `CellUpdate` once the buffer carries scroll-region state. Cell
 * ops mutate individual screen positions; region ops manage the active
 * hardware scroll region (DECSTBM). Each op is self-contained: it carries
 * everything the [[BufferFlusher]] needs to translate it into ANSI bytes,
 * and everything the [[Frame]] needs to mirror its effect onto `previous`.
 *
 * See `.claude/tasks/scrollable-canvas-and-scroll-regions.md` for design
 * rationale and the ratified decisions.
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
