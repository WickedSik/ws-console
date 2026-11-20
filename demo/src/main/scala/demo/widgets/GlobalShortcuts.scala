package io.github.wickedsik.wsconsole
package demo.widgets

import buffer.Frame
import component.{Component, Container, RenderContext}
import event.{Event, EventResult, KeyInput, Keymap}
import layout.{Constraint, Direction}

import zio.{UIO, ZIO}

import java.io.IOException

/**
 * Single-child container that turns keys bound in a [[event.Keymap]]
 * into `Perform` actions — the root-level home for keys that must work
 * regardless of where focus sits.
 *
 * Layout is a passthrough: the child fills the container's rect. Focus
 * travels through the child first — `GlobalShortcuts.handleEvent` only
 * sees an event once the focused component and everything below this
 * wrapper have answered `Ignored`. A focused text field therefore keeps
 * letters bound as shortcuts here.
 */
final class GlobalShortcuts private (
  child: Component,
  keymap: Keymap[ZIO[Frame, IOException, Unit]]
) extends Container:

  val items: Seq[(Constraint, Component)] = Seq(Constraint.Fill -> child)
  val direction: Direction = Direction.Vertical

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    KeyInput.decode(event, keymap) match
      case KeyInput.Bound(action)                                   => EventResult.Perform(action)
      case KeyInput.Text(_) | KeyInput.Unbound(_) | KeyInput.NotKey => EventResult.Ignored

object GlobalShortcuts:

  /**
   * Wrap `child` with a shortcut keymap. Each chord maps to the effect
   * it performs:
   *
   * {{{
   *   GlobalShortcuts.make(content, Keymap(
   *     Keymap.plain('q')                -> app.quit,
   *     Keymap.plain(SpecialKeyCode.Tab) -> app.focusManager.focusNext
   *   ))
   * }}}
   */
  def make(child: Component, keymap: Keymap[ZIO[Frame, IOException, Unit]]): UIO[GlobalShortcuts] =
    ZIO.succeed(GlobalShortcuts(child, keymap))
