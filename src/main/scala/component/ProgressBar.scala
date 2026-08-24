package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, Cell, CellStyle}
import geometry.Rect

/**
 * Horizontal determinate progress bar spanning a single row.
 *
 * Display-only — no focus, no local state. `progress` is host-supplied
 * and clamped to `[0.0, 1.0]`. The [[bar]] variant picks the rendering
 * strategy; `Fill` (the default) uses block-element eighths for
 * sub-cell precision at the fill boundary, `Shade` softens the leading
 * edge with a four-step gradient, `Segmented` draws discrete pips.
 *
 * The fill carries the consumer's `style` (typically an `accent`,
 * `info`, or `success` role). The track renders in the same style
 * plus `Dim` — a muted version of whatever hue the fill carries. When
 * a `Theme` service ships, the track derivation moves behind
 * `ctx.theme.trackFor(role)`.
 *
 * The bar occupies the top row of `area`. A caller wanting vertical
 * centring wraps it in a `VBox` with spacers. A percentage caption is
 * not built in — compose a `Text` beside the bar in an `HBox`.
 */
final case class ProgressBar(
  progress: Double,
  bar: ProgressBarStyle = ProgressBarStyle.Fill,
  style: CellStyle = CellStyle.Empty
) extends Component:

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty then return

    val width = area.width
    val clamped = math.max(0.0, math.min(1.0, progress))
    val trackStyle = style.copy(attributes = style.attributes + Attribute.Dim)

    bar match
      case ProgressBarStyle.Fill      => renderFill(canvas, area.x, area.y, width, clamped, trackStyle)
      case ProgressBarStyle.Shade     => renderShade(canvas, area.x, area.y, width, clamped, trackStyle)
      case ProgressBarStyle.Segmented => renderSegmented(canvas, area.x, area.y, width, clamped, trackStyle)

  private def renderFill(
    canvas: Canvas,
    x0: Int,
    y: Int,
    width: Int,
    progress: Double,
    trackStyle: CellStyle
  ): Unit =
    val fillStyle = style
    val totalEighths = math.round(progress * width * 8).toInt
    val fullCells = totalEighths / 8
    val partialEighths = totalEighths % 8
    val eighths = ProgressBarStyle.Fill.eighths

    var col = 0
    while col < width do
      val cell =
        if col < fullCells then Cell(ProgressBarStyle.Fill.full, fillStyle)
        else if col == fullCells && partialEighths > 0 then Cell(eighths(partialEighths - 1), fillStyle)
        else Cell(ProgressBarStyle.Fill.empty, trackStyle)
      canvas.putChar(x0 + col, y, cell.char, cell.style)
      col += 1

  private def renderShade(
    canvas: Canvas,
    x0: Int,
    y: Int,
    width: Int,
    progress: Double,
    trackStyle: CellStyle
  ): Unit =
    val fillStyle = style
    val shades = ProgressBarStyle.Shade.shades
    val totalSteps = math.round(progress * width * shades.length).toInt
    val fullCells = totalSteps / shades.length
    val partialSteps = totalSteps % shades.length

    var col = 0
    while col < width do
      val cell =
        if col < fullCells then Cell(ProgressBarStyle.Shade.full, fillStyle)
        else if col == fullCells && partialSteps > 0 then Cell(shades(partialSteps - 1), fillStyle)
        else Cell(ProgressBarStyle.Shade.empty, trackStyle)
      canvas.putChar(x0 + col, y, cell.char, cell.style)
      col += 1

  private def renderSegmented(
    canvas: Canvas,
    x0: Int,
    y: Int,
    width: Int,
    progress: Double,
    trackStyle: CellStyle
  ): Unit =
    val filled = math.round(progress * width).toInt
    var col = 0
    while col < width do
      val cell =
        if col < filled then Cell(ProgressBarStyle.Segmented.filled, style)
        else Cell(ProgressBarStyle.Segmented.empty, trackStyle)
      canvas.putChar(x0 + col, y, cell.char, cell.style)
      col += 1
