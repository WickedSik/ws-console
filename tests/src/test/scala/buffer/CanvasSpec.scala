package io.github.wickedsik.wsconsole
package buffer

import ansi.FgColor
import geometry.{Rect, Sides}
import zio.Scope
import zio.test.*

object CanvasSpec extends ZIOSpecDefault:

  private val redStyle = CellStyle(fg = Foreground.Named(FgColor.Red))
  private val yellowStyle = CellStyle(fg = Foreground.Named(FgColor.Yellow))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Canvas")(
    suite("putChar / putText")(
      test("putChar writes a single styled cell") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        canvas.putChar(3, 2, 'X', redStyle)
        assertTrue(buf.get(3, 2).contains(Cell('X', redStyle)))
      },
      test("putText writes characters left-to-right") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        canvas.putText(2, 1, "abc", redStyle)
        assertTrue(
          buf.get(2, 1).contains(Cell('a', redStyle)),
          buf.get(3, 1).contains(Cell('b', redStyle)),
          buf.get(4, 1).contains(Cell('c', redStyle))
        )
      },
      test("putText that runs off the right edge is clipped") {
        val buf = ScreenBuffer.of(5, 1)
        val canvas = Canvas(buf)
        canvas.putText(3, 0, "abcd", redStyle)
        assertTrue(
          buf.get(3, 0).contains(Cell('a', redStyle)),
          buf.get(4, 0).contains(Cell('b', redStyle))
          // 'c' and 'd' are silently discarded
        )
      }
    ),
    suite("drawBox")(
      test("draws corners and edges with the given style") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        canvas.drawBox(Rect(0, 0, 4, 3), BoxStyle.Single, style = yellowStyle)
        assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
          buf.get(3, 0).map(_.char).contains(BoxStyle.Single.topRight),
          buf.get(0, 2).map(_.char).contains(BoxStyle.Single.bottomLeft),
          buf.get(3, 2).map(_.char).contains(BoxStyle.Single.bottomRight),
          buf.get(1, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          buf.get(2, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          buf.get(0, 1).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(3, 1).map(_.char).contains(BoxStyle.Single.midRight),
          buf.get(1, 2).map(_.char).contains(BoxStyle.Single.bottomCenter),
          buf.get(1, 0).map(_.style).contains(yellowStyle)
        )
      },
      test("title is placed on the top edge starting at x+2") {
        val buf = ScreenBuffer.of(20, 5)
        val canvas = Canvas(buf)
        canvas.drawBox(Rect(0, 0, 12, 3), BoxStyle.Single, title = Some("hi"), style = yellowStyle)
        assertTrue(
          buf.get(2, 0).map(_.char).contains('h'),
          buf.get(3, 0).map(_.char).contains('i')
        )
      },
      test("a wide title is truncated by cells and leaves the corner whole") {
        val buf = ScreenBuffer.of(10, 3)
        // maxTitleLen = 8 - 4 = 4 cells: " 設定 " is 6, so " 設" (3 cells) is drawn.
        Canvas(buf).drawBox(Rect(0, 0, 8, 3), BoxStyle.Single, title = Some(" 設定 "))
        assertTrue(
          buf.get(3, 0).map(_.text).contains("設"),
          !(0 until 10).exists(x => buf.get(x, 0).exists(_.text == "定")),
          buf.get(7, 0).map(_.char).contains('┐')
        )
      },
      test("title is truncated when it exceeds the box width") {
        val buf = ScreenBuffer.of(20, 5)
        val canvas = Canvas(buf)
        canvas.drawBox(Rect(0, 0, 6, 3), BoxStyle.Single, title = Some("toolong"), style = yellowStyle)
        // maxTitleLen = width - 4 = 2; "to" fits, the rest is dropped
        assertTrue(
          buf.get(2, 0).map(_.char).contains('t'),
          buf.get(3, 0).map(_.char).contains('o'),
          buf.get(4, 0).map(_.char).contains(BoxStyle.Single.topCenter)
        )
      },
      test("undersized rect for full frame is a no-op") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        canvas.drawBox(Rect(0, 0, 1, 1), BoxStyle.Single, style = yellowStyle)
        assertTrue(buf.get(0, 0).contains(Cell.Empty))
      },
      test("Sides.none writes nothing, even with a title and non-empty rect") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        canvas.drawBox(
          Rect(0, 0, 8, 4),
          BoxStyle.Single,
          sides = Sides.none,
          title = Some("nope"),
          style = yellowStyle
        )
        val allEmpty =
          (0 until 8).forall(x =>
            (0 until 4).forall(y => buf.get(x, y).contains(Cell.Empty))
          )
        assertTrue(allEmpty)
      },

      // ===== Per-side visibility =====

      test("sides.top only fills the top row with topCenter — no corners, nothing below") {
        val buf = ScreenBuffer.of(6, 3)
        val canvas = Canvas(buf)
        canvas.drawBox(
          Rect(0, 0, 6, 3),
          BoxStyle.Single,
          sides = Sides(top = true, right = false, bottom = false, left = false),
          style = yellowStyle
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          buf.get(5, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          buf.get(0, 1).contains(Cell.Empty),
          buf.get(0, 2).contains(Cell.Empty)
        )
      },
      test("sides.top + sides.left draws two edges meeting at the top-left corner") {
        val buf = ScreenBuffer.of(4, 4)
        val canvas = Canvas(buf)
        canvas.drawBox(
          Rect(0, 0, 4, 4),
          BoxStyle.Single,
          sides = Sides(top = true, right = false, bottom = false, left = true),
          style = yellowStyle
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
          // Top row past the corner is topCenter — no top-right because right is off.
          buf.get(3, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          // Left column below the corner is midLeft.
          buf.get(0, 1).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(0, 3).map(_.char).contains(BoxStyle.Single.midLeft),
          // Nothing on the right column or bottom row.
          buf.get(3, 1).contains(Cell.Empty),
          buf.get(1, 3).contains(Cell.Empty)
        )
      },
      test("three-sided (no bottom) draws top corners and extends verticals to the bottom row") {
        val buf = ScreenBuffer.of(5, 4)
        val canvas = Canvas(buf)
        canvas.drawBox(
          Rect(0, 0, 5, 4),
          BoxStyle.Single,
          sides = Sides(top = true, right = true, bottom = false, left = true),
          style = yellowStyle
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topLeft),
          buf.get(4, 0).map(_.char).contains(BoxStyle.Single.topRight),
          buf.get(0, 1).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(4, 1).map(_.char).contains(BoxStyle.Single.midRight),
          // Bottom row: no bottom edge, so verticals extend through it.
          buf.get(0, 3).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(4, 3).map(_.char).contains(BoxStyle.Single.midRight),
          // The middle of the bottom row was never written — no bottomCenter.
          buf.get(2, 3).contains(Cell.Empty)
        )
      },
      test("title is suppressed when sides.top is false") {
        val buf = ScreenBuffer.of(10, 4)
        val canvas = Canvas(buf)
        canvas.drawBox(
          Rect(0, 0, 10, 4),
          BoxStyle.Single,
          sides = Sides(top = false, right = true, bottom = true, left = true),
          title = Some("hi"),
          style = yellowStyle
        )
        // Neither 'h' nor 'i' lands on row 0.
        assertTrue(
          !buf.get(2, 0).map(_.char).contains('h'),
          !buf.get(3, 0).map(_.char).contains('i')
        )
      },
      test("undersized guard is proportional to the visible sides") {
        val buf = ScreenBuffer.of(6, 3)
        val canvas = Canvas(buf)
        // Top-only needs height >= 1; a 6x1 rect must draw the top row.
        canvas.drawBox(
          Rect(0, 0, 6, 1),
          BoxStyle.Single,
          sides = Sides(top = true, right = false, bottom = false, left = false),
          style = yellowStyle
        )
        assertTrue(
          buf.get(0, 0).map(_.char).contains(BoxStyle.Single.topCenter),
          buf.get(5, 0).map(_.char).contains(BoxStyle.Single.topCenter)
        )
      }
    ),
    suite("fillRect")(
      test("fills the requested area with the given cell") {
        val buf = ScreenBuffer.of(6, 4)
        val canvas = Canvas(buf)
        val cell = Cell('#', redStyle)
        canvas.fillRect(Rect(1, 1, 3, 2), cell)
        assertTrue(
          buf.get(1, 1).contains(cell),
          buf.get(3, 2).contains(cell),
          buf.get(0, 0).contains(Cell.Empty),
          buf.get(4, 1).contains(Cell.Empty)
        )
      }
    ),
    suite("subCanvas")(
      test("writes through a sub-canvas land at the offset position") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val sub = canvas.subCanvas(Rect(2, 1, 4, 3))
        sub.putChar(0, 0, 'X', redStyle)
        sub.putChar(3, 2, 'Y', redStyle)
        assertTrue(
          buf.get(2, 1).contains(Cell('X', redStyle)),
          buf.get(5, 3).contains(Cell('Y', redStyle))
        )
      },
      test("writes outside the sub-canvas bounds are silently discarded") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val sub = canvas.subCanvas(Rect(2, 1, 3, 2))
        sub.putChar(5, 0, 'X', redStyle)
        sub.putChar(0, 5, 'X', redStyle)
        sub.putChar(-1, 0, 'X', redStyle)
        // Buffer is unchanged outside the sub-canvas
        assertTrue(
          buf.get(7, 1).contains(Cell.Empty),
          buf.get(2, 6).orElse(Some(Cell.Empty)).contains(Cell.Empty),
          buf.get(1, 1).contains(Cell.Empty)
        )
      },
      test("sub-canvas dimensions equal the requested rect when fully inside") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val sub = canvas.subCanvas(Rect(1, 1, 4, 3))
        assertTrue(sub.width == 4, sub.height == 3)
      },
      test("sub-canvas reports its logical extent; writes past the parent are discarded") {
        val buf = ScreenBuffer.of(5, 3)
        val canvas = Canvas(buf)
        val sub = canvas.subCanvas(Rect(3, 1, 10, 10))
        sub.putText(0, 0, "abcd", redStyle)
        assertTrue(
          sub.width == 10,
          sub.height == 10,
          buf.get(3, 1).contains(Cell('a', redStyle)),
          buf.get(4, 1).contains(Cell('b', redStyle))
        )
      },
      test("a negative origin is translated, not clamped") {
        val buf = ScreenBuffer.of(6, 4)
        val canvas = Canvas(buf)
        // Origin two rows above the buffer: canvas row 2 is buffer row 0.
        val sub = canvas.subCanvas(Rect(1, -2, 4, 5))
        sub.putChar(0, 0, 'A', redStyle)
        sub.putChar(0, 2, 'B', redStyle)
        sub.putChar(3, 4, 'C', redStyle)
        assertTrue(
          buf.get(1, 0).contains(Cell('B', redStyle)),
          // Under the old clamp, 'A' landed on (1, 0) and every row shifted down by two.
          buf.get(4, 2).contains(Cell('C', redStyle))
        )
      },
      test("a nested sub-canvas inherits the parent's clip") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val outer = canvas.subCanvas(Rect(2, 1, 3, 2))
        val inner = outer.subCanvas(Rect(1, 0, 10, 10))
        inner.putText(0, 0, "xyz", redStyle)
        inner.putChar(0, 3, 'Q', redStyle)
        assertTrue(
          buf.get(3, 1).contains(Cell('x', redStyle)),
          buf.get(4, 1).contains(Cell('y', redStyle)),
          buf.get(5, 1).contains(Cell.Empty),
          buf.get(3, 4).contains(Cell.Empty)
        )
      }
    ),
    suite("clip")(
      test("keeps the origin and discards writes outside the clip rect") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val clipped = canvas.clip(Rect(2, 1, 3, 2))
        clipped.putText(0, 1, "abcdefg", redStyle)
        clipped.putChar(3, 3, 'Z', redStyle)
        assertTrue(
          clipped.width == 10,
          clipped.height == 5,
          buf.get(1, 1).contains(Cell.Empty),
          buf.get(2, 1).contains(Cell('c', redStyle)),
          buf.get(4, 1).contains(Cell('e', redStyle)),
          buf.get(5, 1).contains(Cell.Empty),
          buf.get(3, 3).contains(Cell.Empty)
        )
      },
      test("a box straddling the clip's top edge is cut, not shifted") {
        val buf = ScreenBuffer.of(8, 6)
        val canvas = Canvas(buf)
        val viewport = canvas.clip(Rect(0, 2, 8, 3))
        // Box occupies rows 0..3; only rows 2..3 survive — its sides and bottom.
        viewport.drawBox(Rect(1, 0, 4, 4), BoxStyle.Single)
        assertTrue(
          buf.get(1, 0).contains(Cell.Empty),
          buf.get(1, 1).contains(Cell.Empty),
          buf.get(1, 2).map(_.char).contains(BoxStyle.Single.midLeft),
          buf.get(1, 3).map(_.char).contains(BoxStyle.Single.bottomLeft),
          buf.get(4, 3).map(_.char).contains(BoxStyle.Single.bottomRight)
        )
      },
      test("clips intersect — a clip never widens its parent's") {
        val buf = ScreenBuffer.of(10, 5)
        val canvas = Canvas(buf)
        val narrowed = canvas.clip(Rect(2, 0, 3, 5)).clip(Rect(0, 0, 10, 5))
        narrowed.putText(0, 0, "abcdefg", redStyle)
        assertTrue(
          buf.get(1, 0).contains(Cell.Empty),
          buf.get(2, 0).contains(Cell('c', redStyle)),
          buf.get(5, 0).contains(Cell.Empty)
        )
      },
      test("fillRect respects the clip") {
        val buf = ScreenBuffer.of(6, 4)
        val canvas = Canvas(buf)
        val cell = Cell('#', redStyle)
        canvas.clip(Rect(1, 1, 2, 2)).fillRect(Rect(0, 0, 6, 4), cell)
        assertTrue(
          buf.get(1, 1).contains(cell),
          buf.get(2, 2).contains(cell),
          buf.get(0, 0).contains(Cell.Empty),
          buf.get(3, 1).contains(Cell.Empty)
        )
      },
      test("a wide grapheme straddling the clip's right edge is dropped whole") {
        val buf = ScreenBuffer.of(6, 1)
        val canvas = Canvas(buf)
        // CJK glyphs occupy cells 0-1, 2-3; the second straddles the clip at x=3.
        canvas.clip(Rect(0, 0, 3, 1)).putText(0, 0, "中文", redStyle)
        assertTrue(
          buf.get(0, 0).map(_.text).contains("中"),
          buf.get(1, 0).map(_.text).contains(""),
          buf.get(2, 0).contains(Cell.Empty),
          buf.get(3, 0).contains(Cell.Empty)
        )
      },
      test("Cyrillic clips on cell boundaries") {
        val buf = ScreenBuffer.of(6, 1)
        val canvas = Canvas(buf)
        canvas.clip(Rect(1, 0, 3, 1)).putText(0, 0, "Привет", redStyle)
        assertTrue(
          buf.get(0, 0).contains(Cell.Empty),
          buf.get(1, 0).map(_.text).contains("р"),
          buf.get(3, 0).map(_.text).contains("в"),
          buf.get(4, 0).contains(Cell.Empty)
        )
      }
    )
  )
