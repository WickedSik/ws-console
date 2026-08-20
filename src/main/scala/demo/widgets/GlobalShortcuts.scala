package io.github.wickedsik.wsconsole
package demo.widgets

import buffer.Frame
import component.{Component, Container, RenderContext}
import event.{Event, EventResult}
import layout.{Constraint, Direction}

import zio.{UIO, ZIO}

import java.io.IOException

/**
 * Single-child container that turns matched events into `Perform`
 * actions — the root-level home for keys that must work regardless of
 * where focus sits.
 *
 * Layout is a passthrough: the child fills the container's rect. Focus
 * travels through the child first — `GlobalShortcuts.handleEvent` only
 * sees an event once the focused component and everything below this
 * wrapper have answered `Ignored`. A focused text field therefore keeps
 * letters bound as shortcuts here.
 */
final class GlobalShortcuts private (
  child:    Component,
  bindings: PartialFunction[Event, ZIO[Frame, IOException, Unit]]
) extends Container:

  val items:     Seq[(Constraint, Component)] = Seq(Constraint.Fill -> child)
  val direction: Direction                    = Direction.Vertical

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if bindings.isDefinedAt(event) then EventResult.Perform(bindings(event))
    else EventResult.Ignored

object GlobalShortcuts:

  /**
   * Wrap `child` with a shortcut map. The map is a `PartialFunction`
   * so pattern-match arms compose naturally at the call site:
   *
   * {{{
   *   GlobalShortcuts.make(content) {
   *     case CharKey('q', mods) if mods.isEmpty => app.quit
   *     case SpecialKey(SpecialKeyCode.Tab, _)  => app.focusManager.focusNext()
   *   }
   * }}}
   */
  def make(child: Component)(
    bindings: PartialFunction[Event, ZIO[Frame, IOException, Unit]]
  ): UIO[GlobalShortcuts] =
    ZIO.succeed(new GlobalShortcuts(child, bindings))
