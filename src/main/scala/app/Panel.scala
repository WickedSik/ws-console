package io.github.wickedsik.wsconsole
package app

import buffer.{Cell, Frame}
import component.Component
import geometry.Rect
import terminal.Terminal

import zio.ZIO

import java.io.IOException

/**
 * Layer 7 Panel — a sibling of [[Component]] (Q3 ratified, *not* a subtype).
 *
 * A panel *owns* a root component, a [[Rect]] it occupies on screen, and
 * three lifecycle hooks driven by the host that displays it. The split
 * keeps a panel's region-management concerns (clear bounds on teardown,
 * install / restore scroll regions, re-bind transient handlers) outside
 * the component model — components render, panels live and die.
 *
 * Lifecycle:
 *   - `onMount`   — one-shot setup when the panel is `push`ed onto a host.
 *   - `onUnload`  — teardown when the panel is `pop`ped or `replace`d.
 *                   Default implementation writes [[Cell.Empty]] across
 *                   `bounds`, absorbing the manual `.onInterrupt(clearBox)`
 *                   boilerplate of the existing animated panels.
 *   - `onRemount` — when a `pop` reveals this panel after it had been
 *                   covered by another. Distinct phase from `onMount` —
 *                   one-shot setup does not re-run. Default is no-op.
 *
 * Panels are *passive infrastructure* — a [[PanelHost]] drives the
 * transitions and a single Layer 6 `RenderLoop` produces frames.
 */
trait Panel:
  def bounds:    Rect
  def root:      Component
  def onMount:   ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit
  def onUnload:  ZIO[Terminal & Frame, IOException, Unit] = Panel.clearBounds(bounds)
  def onRemount: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit

object Panel:

  /**
   * Default `onUnload` body: fill `bounds` with [[Cell.Empty]] on the
   * Frame's current canvas. The write is not flushed — the host issues a
   * redraw after teardown and the loop's next render walks the now-
   * shorter panel stack.
   */
  def clearBounds(bounds: Rect): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWith[Frame](_.canvas.fillRect(bounds, Cell.Empty))

  /**
   * Convenience constructor for a panel whose lifecycle is entirely
   * default (mount no-op, unload clears bounds, remount no-op).
   */
  def of(rootComponent: Component, panelBounds: Rect): Panel =
    new Panel:
      def bounds: Rect      = panelBounds
      def root:   Component = rootComponent
