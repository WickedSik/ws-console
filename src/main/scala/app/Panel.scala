package io.github.wickedsik.wsconsole
package app

import buffer.{Cell, Frame}
import component.Component
import geometry.Rect
import terminal.Terminal

import zio.ZIO

import java.io.IOException

/**
 * Layer 7 Panel — a sibling of [[Component]], not a subtype.
 *
 * A panel owns a root component, a [[Rect]] it occupies, and three
 * lifecycle hooks driven by its host. Region-management concerns
 * (clear bounds on teardown, install/restore scroll regions) stay
 * outside the component model — components render, panels live and die.
 *
 * Lifecycle:
 *   - `onMount`   — one-shot setup on `push`.
 *   - `onUnload`  — teardown on `pop` or `replace`. Default clears
 *                   `bounds` with [[Cell.Empty]].
 *   - `onRemount` — when a `pop` reveals a covered panel. Default no-op;
 *                   `onMount` does not re-run.
 *
 * Panels are passive infrastructure — a [[PanelHost]] drives transitions
 * and a Layer 6 `RenderLoop` produces frames.
 */
trait Panel:
  def bounds: Rect
  def root: Component
  def onMount: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit
  def onUnload: ZIO[Terminal & Frame, IOException, Unit] = Panel.clearBounds(bounds)
  def onRemount: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit

object Panel:

  /**
   * Fill `bounds` with [[Cell.Empty]] on the Frame's current canvas.
   * Not flushed — the host issues a redraw after teardown.
   */
  def clearBounds(bounds: Rect): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWith[Frame](_.canvas.fillRect(bounds, Cell.Empty))

  /** Panel with default lifecycle (mount no-op, unload clears bounds, remount no-op). */
  def of(rootComponent: Component, panelBounds: Rect): Panel =
    new Panel:
      def bounds: Rect = panelBounds
      def root: Component = rootComponent
