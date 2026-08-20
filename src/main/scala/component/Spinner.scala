package io.github.wickedsik.wsconsole
package component

import buffer.{Canvas, CellStyle}
import geometry.Rect

/**
 * Indeterminate activity indicator — "work is happening, duration
 * unknown."
 *
 * Display-only — no focus, no local state. The current frame is a pure
 * function of the wall-clock timestamp exposed on [[RenderContext]]
 * (sampled once per frame by the render loop) and the [[SpinnerStyle]]'s
 * `frameInterval`:
 *
 *   `idx = (timestamp.toEpochMilli / frameInterval.toMillis) % frames.length`
 *
 * Because the index derives from real time, the animation runs at a
 * constant rate independent of render cadence — a faster render loop
 * does not spin it faster.
 *
 * '''Consumer responsibility.''' The render loop only produces frames
 * when something requests a redraw. An on-screen Spinner that wants to
 * animate needs the loop to keep turning — typically a small periodic
 * `requestRedraw` from the panel that hosts it. This widget is the
 * frame selector, not the animation driver.
 *
 * Occupies a single cell in the top-left of `area`. Multi-character
 * frames render left-to-right and truncate to `area.width`.
 */
final case class Spinner(
  frames: SpinnerStyle = SpinnerStyle.Braille,
  style:  CellStyle    = CellStyle.Empty
) extends Component:

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty then return

    val cycle    = frames.frames
    val interval = frames.frameInterval.toMillis
    val elapsed  = ctx.timestamp.toEpochMilli
    val ticks    = if interval <= 0 then 0L else elapsed / interval
    val idx      = math.floorMod(ticks, cycle.length.toLong).toInt
    val frame    = cycle(idx)

    val truncated =
      if frame.length > area.width then frame.take(area.width)
      else frame
    canvas.putText(area.x, area.y, truncated, style)
