package io.github.wickedsik.wsconsole
package buffer

/**
 * A minimal positional change to the screen: place `cell` at coordinates (x, y).
 *
 * Coordinates are 0-indexed with origin at the top-left of the buffer.
 * Translation to 1-indexed terminal coordinates happens in [[BufferFlusher]].
 */
final case class CellUpdate(x: Int, y: Int, cell: Cell)
