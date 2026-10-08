package io.github.wickedsik.wsconsole
package layout

/**
 * Axis along which a [[Layout]] partitions a region.
 *
 *   - `Horizontal`: constraints are arranged left-to-right; each resolved
 *     sub-rect spans the full vertical extent of the parent area.
 *   - `Vertical`: constraints are arranged top-to-bottom; each resolved
 *     sub-rect spans the full horizontal extent of the parent area.
 *
 * Diagonal layouts and right-to-left / bottom-to-top reversal are out of
 * scope for Layer 3.
 */
enum Direction:
  case Horizontal, Vertical
