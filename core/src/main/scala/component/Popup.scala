package io.github.wickedsik.wsconsole
package component

import geometry.Rect

/**
 * A box a component asks to have drawn next to it and over everything
 * around it: a dropdown list, an autocomplete menu, a tooltip. Returned
 * from [[Component.popups]] (ADR-007).
 *
 * `anchor` is the rect the popup attaches to, in canvas coordinates —
 * usually the owner's own area. `width` and `height` are the size the
 * popup wants. The render pipeline places it below the anchor, or above
 * when only that side has room, and may shrink it to fit the frame.
 *
 * `content` is laid out and drawn into the placed rect after the whole
 * tree, on a canvas clipped only to that rect, so neither an enclosing
 * `ScrollView` nor a later sibling can hide it. The rect is cleared to
 * blank cells first, so the popup is opaque.
 *
 * The popup is never a focus stop: focusable components inside
 * `content` stay out of the Tab cycle. The owner keeps focus and handles
 * the keys that drive the popup.
 */
final case class Popup(
  content: Component,
  anchor: Rect,
  width: Int,
  height: Int
)
