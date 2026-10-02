package io.github.wickedsik.wsconsole
package component

import buffer.{Attribute, Canvas, Cell, CellStyle}
import event.KeyEvent.SpecialKey
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect

import zio.{UIO, ZIO}

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

/**
 * A vertical viewport onto a child taller than the area it is given.
 *
 * The child is laid out at `max(preferredHeight, viewportHeight)` rows,
 * one column narrower than the ScrollView, and shifted up by the scroll
 * offset. Rows outside the viewport are clipped, not shifted: a bordered
 * child straddling an edge shows its visible rows with the border cut.
 * Short content still fills the viewport, as it would without a
 * ScrollView. Descendant rects in the layout are the translated ones,
 * offscreen or not, so offscreen focusables stay in the Tab cycle.
 *
 * The right-hand column always holds a scrollbar: a track plus a thumb
 * sized to `viewport / content`, spanning the whole track when the
 * content fits. Reserving the column unconditionally keeps the content
 * width constant, so content never reflows as it crosses the viewport
 * height.
 *
 * Keys arrive when the ScrollView or one of its descendants holds focus
 * and the descendant left the key `Ignored`. Every scroll key requires
 * `Ctrl` and nothing else; the bare keys belong to the focused control
 * (caret, selection, list movement) and are never taken by the view:
 *
 *   - `Ctrl+PgUp` / `Ctrl+PgDn` — by the viewport height minus one
 *   - `Ctrl+Up` / `Ctrl+Down` — by one row
 *   - `Ctrl+Home` / `Ctrl+End` — to either end
 *
 * Some hosts claim these chords before the terminal sees them (macOS
 * binds `Ctrl+Up` / `Ctrl+Down` to Mission Control by default; several
 * terminals switch tabs on `Ctrl+PgUp` / `Ctrl+PgDn`).
 *
 * A key that cannot move the view (already at that end, or the content
 * fits) is `Ignored`, so an enclosing ScrollView receives it — the
 * innermost view that can scroll consumes the key.
 *
 * Focus: a ScrollView is not a focus stop by default — its focusable
 * descendants are. Pass `focusable = true` for read-only content such as
 * a log pane, which has nothing else to hold focus. When the ScrollView
 * itself is focused the scrollbar thumb renders bold.
 *
 * The ScrollView has no preferred height of its own: it is the component
 * that is smaller than its content. Inside a vertical container give it
 * a `Fixed`, `Percentage`, or `Fill` constraint.
 *
 * Widget-local state: the scroll offset, and the geometry of the last
 * layout (needed by `handleEvent`, which receives no area). The offset
 * is clamped to `[0, content - viewport]` on every layout, which also
 * absorbs terminal resizes and shrinking content.
 */
final class ScrollView private (
  val child: Component,
  override val focusable: Boolean,
  val style: CellStyle,
  val trackStyle: CellStyle
) extends Component
    with Scrollable:

  private val offsetRef = new AtomicInteger(0)
  private val geometryRef = new AtomicReference[ScrollView.Geometry](ScrollView.Geometry.empty)

  /** Rows of content scrolled above the viewport. */
  def offset: Int = offsetRef.get()

  /**
   * Scroll so `row` is the first visible content row, clamped to the last
   * layout's range. Takes effect on the next frame; request a redraw.
   *
   * '''Fiber affinity.''' Every layout writes the clamped offset back, so
   * a call from a fiber other than the render loop's can be overwritten
   * by a layout already in flight. Call from `onEvent` or a `Perform`
   * effect, which run on the loop fiber.
   */
  def scrollTo(row: Int): Unit =
    offsetRef.set(geometryRef.get().clamp(row))

  // ===== Layout =====

  /** Height the child is laid out at for an area of this size. */
  private def contentHeight(area: Rect): Int =
    val preferred = child.preferredHeight(ScrollView.contentWidth(area)).getOrElse(area.height)
    math.max(preferred, area.height)

  override def childLayouts(area: Rect): Seq[(Component, Rect)] =
    if area.isEmpty || ScrollView.contentWidth(area) == 0 then
      geometryRef.set(ScrollView.Geometry.empty)
      Seq((child, Rect(area.x, area.y, 0, 0)))
    else
      val geometry = ScrollView.Geometry(area.height, contentHeight(area))
      geometryRef.set(geometry)
      val off = geometry.clamp(offsetRef.get())
      offsetRef.set(off)
      Seq((child, Rect(area.x, area.y - off, ScrollView.contentWidth(area), geometry.content)))

  // ===== Rendering =====

  override def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    if area.isEmpty then return
    val viewport = Rect(area.x, area.y, ScrollView.contentWidth(area), area.height)
    val clipped = canvas.clip(viewport)
    childLayouts(area).foreach((c, r) => c.render(r, clipped, ctx))
    drawScrollbar(area, canvas, ctx)

  private def drawScrollbar(area: Rect, canvas: Canvas, ctx: RenderContext): Unit =
    val x = area.x + area.width - 1
    val view = area.height
    val geometry = geometryRef.get()
    val (thumbStart, thumbSize) = ScrollView.thumb(view, geometry.content, offsetRef.get())
    val thumbStyle = if ctx.focus.isFocused(this.id) then InteractionState.focused(style) else style
    var row = 0
    while row < view do
      val inThumb = row >= thumbStart && row < thumbStart + thumbSize
      val cell =
        if inThumb then Cell(ScrollView.ThumbGlyph, thumbStyle)
        else Cell(ScrollView.TrackGlyph, trackStyle)
      canvas.putChar(x, area.y + row, cell.char, cell.style)
      row += 1

  // ===== Scrolling =====

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    val geometry = geometryRef.get()
    event match
      case SpecialKey(code, m) if m == ScrollView.Ctrl =>
        code match
          case SpecialKeyCode.PgUp => scrollBy(-geometry.page)
          case SpecialKeyCode.PgDn => scrollBy(geometry.page)
          case SpecialKeyCode.Up   => scrollBy(-1)
          case SpecialKeyCode.Down => scrollBy(1)
          case SpecialKeyCode.Home => scrollToRow(0)
          case SpecialKeyCode.End  => scrollToRow(geometry.maxOffset)
          case _                   => EventResult.Ignored
      case _ => EventResult.Ignored

  private def scrollBy(delta: Int): EventResult = scrollToRow(offsetRef.get() + delta)

  private def scrollToRow(row: Int): EventResult =
    val current = offsetRef.get()
    val next = geometryRef.get().clamp(row)
    if next == current then EventResult.Ignored
    else
      offsetRef.set(next)
      EventResult.RequestRedraw

  override def reveal(target: Rect, viewport: Rect): Int =
    val current = offsetRef.get()
    val top = target.y - viewport.y
    val bottom = top + target.height
    val delta =
      if target.height >= viewport.height then top
      else if top < 0 then top
      else if bottom > viewport.height then bottom - viewport.height
      else 0
    val next = geometryRef.get().clamp(current + delta)
    offsetRef.set(next)
    next - current

object ScrollView:

  /** Scrollbar track glyph — the light vertical from the box vocabulary. */
  val TrackGlyph: Char = '│'

  /** Scrollbar thumb glyph — the full block from the progress-bar vocabulary. */
  val ThumbGlyph: Char = '█'

  /** Default track style: dimmed, so the thumb reads as the foreground. */
  val DefaultTrackStyle: CellStyle = CellStyle(attributes = Set(Attribute.Dim))

  /**
   * Wrap `child` in a viewport. `style` paints the scrollbar thumb,
   * `trackStyle` the rest of its column. `focusable = true` makes the
   * ScrollView a focus stop of its own, for content with no focusable
   * descendants.
   */
  def make(
    child: Component,
    focusable: Boolean = false,
    style: CellStyle = CellStyle.Empty,
    trackStyle: CellStyle = DefaultTrackStyle
  ): UIO[ScrollView] =
    ZIO.succeed(new ScrollView(child, focusable, style, trackStyle))

  /** Columns left for content once the scrollbar column is reserved. */
  private def contentWidth(area: Rect): Int = math.max(0, area.width - 1)

  /** The modifier set every scroll key requires: `Ctrl` alone. */
  private val Ctrl: Set[KeyModifier] = Set(KeyModifier.Ctrl)

  /**
   * Thumb `(start, size)` in rows for a track `view` rows tall. The size
   * is proportional to `view / content`, at least one row; the start
   * maps offset `0` to the top and the maximum offset to the bottom
   * exactly, so the thumb touches each end only when the view does.
   */
  private[component] def thumb(view: Int, content: Int, offset: Int): (Int, Int) =
    if view <= 0 then (0, 0)
    else if content <= view then (0, view)
    else
      val size = math.max(1, math.min(view, math.round(view.toDouble * view / content).toInt))
      val maxOffset = content - view
      val travel = view - size
      val proportional = math.round(offset.toDouble * travel / maxOffset).toInt
      val start =
        if offset <= 0 then 0
        else if offset >= maxOffset then travel
        // Strictly between the ends whenever the view is, when the track leaves room for it.
        else if travel >= 2 then math.max(1, math.min(travel - 1, proportional))
        else proportional
      (start, size)

  /** Viewport and content height from the last layout. */
  final private case class Geometry(viewport: Int, content: Int):
    def maxOffset: Int = math.max(0, content - viewport)
    def clamp(row: Int): Int = math.max(0, math.min(maxOffset, row))

    /** Page distance: the viewport less one row of overlap, at least one row. */
    def page: Int = math.max(1, viewport - 1)

  private object Geometry:
    val empty: Geometry = Geometry(0, 0)
