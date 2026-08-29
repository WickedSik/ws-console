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
 * A panel owns a root component, a rule for what rect it renders into
 * given its host's allocated area, and three lifecycle hooks driven by
 * its host. Region-management concerns stay outside the component model —
 * components render, panels live and die.
 *
 * `bounds(hostArea)` defaults to the host area — the panel fills whatever
 * the host grants it. Overlays override this to return a fixed sub-rect.
 *
 * Lifecycle:
 *   - `onMount`   — one-shot setup on `push`.
 *   - `onUnload`  — teardown on `pop` or `replace`. Default no-op; the
 *                   host's post-transition redraw and Layer 2's
 *                   per-frame `clearOutsideRegion` at swap already wipe
 *                   the departed panel's cells.
 *   - `onRemount` — when a `pop` reveals a covered panel. Default no-op;
 *                   `onMount` does not re-run.
 *
 * Panels are passive infrastructure — a [[PanelHost]] drives transitions
 * and a Layer 6 `RenderLoop` produces frames.
 */
trait Panel:
  /**
   * Compute the rect this panel renders into, given the host's allocated
   * area. Default: fills the whole area.
   */
  def bounds(hostArea: Rect): Rect = hostArea
  def root: Component
  def onMount: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit
  def onUnload: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit
  def onRemount: ZIO[Terminal & Frame, IOException, Unit] = ZIO.unit

object Panel:

  /**
   * Fill `bounds` with [[Cell.Empty]] on the Frame's current canvas.
   * Not flushed — the host issues a redraw after teardown.
   */
  def clearBounds(bounds: Rect): ZIO[Frame, IOException, Unit] =
    ZIO.serviceWith[Frame](_.canvas.fillRect(bounds, Cell.Empty))

  /** Panel that fills whatever area its host grants it. */
  def of(rootComponent: Component): Panel =
    new Panel:
      def root: Component = rootComponent

  /**
   * Overlay panel with a fixed absolute rect, ignoring the host area.
   * Use for modal-style panels that must sit at a specific screen
   * position regardless of host size.
   */
  def overlay(rootComponent: Component, fixedBounds: Rect): Panel =
    new Panel:
      override def bounds(hostArea: Rect): Rect = fixedBounds
      def root: Component = rootComponent
