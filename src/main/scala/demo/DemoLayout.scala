package io.github.wickedsik.wsconsole
package demo

import geometry.Rect

/**
 * Fixed layout constants for the demo's `PanelHost`-hosted content area.
 *
 * The demo's top-level tree is
 * `VBox(Constraint.Fill -> host.root, Constraint.Fixed(3) -> toolbar)` —
 * the content region is a fixed 80×21 rect (24 rows minus 3 for the
 * toolbar). Every in-lineup demo panel targets this rect so its `bounds`
 * match the space `host.root` grants it at render time.
 *
 * Ratified per `panel-opacity-and-panelhost-activation.md` Q2 (2026-07-31).
 */
object DemoLayout:
  /** The bounds every in-lineup demo panel targets. Sits above the 3-row toolbar. */
  val contentBounds: Rect = Rect(0, 0, 80, 21)
