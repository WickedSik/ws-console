package io.github.wickedsik.wsconsole
package component

import buffer.Canvas
import geometry.Rect

/**
 * A component that renders nothing but participates in layout.
 *
 * Useful for explicit blank cells in a container — top/bottom padding,
 * separators between elements, "leftover" cells you want to reserve.
 *
 * Equivalent to `Text("")` but more communicative at the call site.
 */
case object Spacer extends Component:
  def render(area: Rect, canvas: Canvas): Unit = ()
