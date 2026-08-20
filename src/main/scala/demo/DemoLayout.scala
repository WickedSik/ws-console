package io.github.wickedsik.wsconsole
package demo

import geometry.Rect

/**
 * Fixed layout constants for the demo's `PanelHost`-hosted content area.
 *
 * The top-level tree is `VBox(Fill -> host.root, Fixed(3) -> toolbar)`
 * — content is 80×21 (24 rows minus 3 for the toolbar). Every panel
 * targets this rect so its `bounds` match what `host.root` grants it.
 */
object DemoLayout:
  /** The bounds every in-lineup demo panel targets. */
  val contentBounds: Rect = Rect(0, 0, 80, 21)
