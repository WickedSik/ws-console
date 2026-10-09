package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.io.IOException

object RadioGroupSpec extends ZIOSpecDefault:

  private val noop: Int => ZIO[Frame, IOException, Unit] = _ => ZIO.unit

  private val baseStyle =
    CellStyle(fg = Foreground.Named(FgColor.White))

  private val options = Seq("Alpha", "Beta", "Gamma")

  private def focusCtx(w: RadioGroup): RenderContext =
    RenderContext(FocusSnapshot(Some(w.id)))

  private val unfocusedCtx: RenderContext = RenderContext.empty

  private def keyPress(w: RadioGroup, event: Event): EventResult =
    w.handleEvent(event, focusCtx(w))

  private val horizontal = RadioGroup.Layout.Horizontal()

  private def key(code: SpecialKeyCode): Event = SpecialKey(code, Set.empty)

  /** Row `y` of `buf` as one char per cell, blank for an empty cell. */
  private def row(buf: buffer.ScreenBuffer, y: Int): String =
    (0 until buf.width).map(x => buf.get(x, y).map(_.char).getOrElse(' ')).mkString

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_) => true
      case _                      => false

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RadioGroup")(
    // ===== Mark & label rendering =====

    suite("rendering")(
      test("draws one option per row with the selected mark on the chosen row") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('○'), // Alpha: unselected
            buf.get(0, 1).map(_.char).contains('●'), // Beta:  selected
            buf.get(0, 2).map(_.char).contains('○') // Gamma: unselected
          )
      },
      test("labels follow their mark after one space") {
        for
          w <- RadioGroup.make(options, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(2, 0).map(_.char).contains('A'), // Alpha
            buf.get(2, 1).map(_.char).contains('B'), // Beta
            buf.get(2, 2).map(_.char).contains('G') // Gamma
          )
      },
      test("consumer-supplied marks override the defaults") {
        for
          w <- RadioGroup.make(options, marks = ("(*)", "( )"), style = baseStyle)
        yield
          val buf = renderToBuffer(20, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('('),
            buf.get(1, 0).map(_.char).contains('*'),
            buf.get(2, 0).map(_.char).contains(')')
          )
      },
      test("labels wider than the row remainder are truncated to fit") {
        for
          w <- RadioGroup.make(Seq("HelloWorld", "Short"), style = baseStyle)
        yield
          val buf = renderToBuffer(6, 4)(w, ctx = unfocusedCtx)
          // Mark(1) + space(1) leaves 4 label cells on each row.
          assertTrue(
            buf.get(2, 0).map(_.char).contains('H'),
            buf.get(3, 0).map(_.char).contains('e'),
            buf.get(4, 0).map(_.char).contains('l'),
            buf.get(5, 0).map(_.char).contains('l')
          )
      },
      test("rows past the area height are not rendered") {
        for
          w <- RadioGroup.make(options, style = baseStyle)
        yield
          // Area height = 2; only rows 0 and 1 render.
          val buf = renderToBuffer(12, 4)(w, area = Rect(0, 0, 12, 2), ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('●'),
            buf.get(0, 1).map(_.char).contains('○'),
            buf.get(0, 2).contains(buffer.Cell.Empty)
          )
      },
      test("CJK labels truncate by cells, never splitting a wide grapheme") {
        for
          w <- RadioGroup.make(Seq("日本語"), style = baseStyle)
        yield
          // Area width 7: mark + space leave 5 cells, room for 日本 (4) but not 語.
          val buf = renderToBuffer(10, 1)(w, area = Rect(0, 0, 7, 1), ctx = unfocusedCtx)
          assertTrue(
            buf.get(2, 0).map(_.text).contains("日"),
            buf.get(4, 0).map(_.text).contains("本"),
            buf.get(6, 0).contains(buffer.Cell.Empty)
          )
      },
      test("empty options renders nothing") {
        for
          w <- RadioGroup.make(Seq.empty, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 0).contains(buffer.Cell.Empty))
      }
    ),

    // ===== State modulation =====

    suite("state modulation")(
      test("selected mark carries Bold (accent) on top of the base style") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(buf.get(0, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(true))
      },
      test("unselected rows carry Dim (muted) on top of the base style") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(2, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
          )
      },
      test("selected label gains Bold when the group is focused") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val focused = renderToBuffer(12, 4)(w, ctx = focusCtx(w))
          val unfocused = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            focused.get(2, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            !unfocused.get(2, 1).map(_.style.attributes.contains(Attribute.Bold)).contains(true)
          )
      },
      test("selected label preserves the role's fg (hue is role-owned)") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val focused = renderToBuffer(12, 4)(w, ctx = focusCtx(w))
          assertTrue(focused.get(2, 1).map(_.style.fg).contains(baseStyle.fg))
      },
      test("disabled adds Dim to every row") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle, enabled = false)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = focusCtx(w))
          assertTrue(
            buf.get(0, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(0, 1).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(0, 2).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
          )
      }
    ),

    // ===== Focus opt-in — one Tab stop =====

    suite("focus")(
      test("enabled non-empty group is focusable as one stop") {
        for
          w <- RadioGroup.make(options)
        yield assertTrue(w.focusable)
      },
      test("disabled group is excluded from the focus cycle") {
        for
          w <- RadioGroup.make(options, enabled = false)
        yield assertTrue(!w.focusable)
      },
      test("empty group is excluded from the focus cycle") {
        for
          w <- RadioGroup.make(Seq.empty)
        yield assertTrue(!w.focusable)
      }
    ),

    // ===== Event handling — arrow selection =====

    suite("selection movement")(
      test("Down advances the selection and returns Perform bound to onSelect") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Down, Set.empty))
          assertTrue(
            w.selected == 1,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Up retracts the selection") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Up, Set.empty))
          assertTrue(
            w.selected == 1,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Left / Right are off-axis for a vertical group and bubble") {
        for
          w <- RadioGroup.make(options, selected = 1)
        yield
          val left = keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          val right = keyPress(w, SpecialKey(SpecialKeyCode.Right, Set.empty))
          assertTrue(left == EventResult.Ignored, right == EventResult.Ignored, w.selected == 1)
      },
      test("Home jumps to the first option") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          assertTrue(
            w.selected == 0,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("End jumps to the last option") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.End, Set.empty))
          assertTrue(
            w.selected == 2,
            res match
              case EventResult.Perform(_) => true; case _ => false
          )
      },
      test("Ctrl+Up / Ctrl+Down are left to bubble to an enclosing view") {
        for
          w <- RadioGroup.make(options, selected = 1)
        yield
          val up = keyPress(w, SpecialKey(SpecialKeyCode.Up, Set(KeyModifier.Ctrl)))
          val down = keyPress(w, SpecialKey(SpecialKeyCode.Down, Set(KeyModifier.Ctrl)))
          assertTrue(up == EventResult.Ignored, down == EventResult.Ignored, w.selected == 1)
      },
      test("Ctrl+Home / Ctrl+End are left to bubble to an enclosing view") {
        for
          w <- RadioGroup.make(options, selected = 1)
        yield
          val home = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set(KeyModifier.Ctrl)))
          val end = keyPress(w, SpecialKey(SpecialKeyCode.End, Set(KeyModifier.Ctrl)))
          assertTrue(home == EventResult.Ignored, end == EventResult.Ignored, w.selected == 1)
      },
      test("Down at the last option returns Ignored (no wrap-around)") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Down, Set.empty))
          assertTrue(w.selected == 2, res == EventResult.Ignored)
      },
      test("Up at the first option returns Ignored (no wrap-around)") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Up, Set.empty))
          assertTrue(w.selected == 0, res == EventResult.Ignored)
      }
    ),

    // ===== Bubbling =====

    suite("bubbling")(
      test("Enter on a focused group returns Ignored") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Enter, Set.empty))
          assertTrue(res == EventResult.Ignored)
      },
      test("Space on a focused group returns Ignored (no per-option toggling)") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, CharKey(' ', Set.empty))
          assertTrue(res == EventResult.Ignored)
      },
      test("arrows on an unfocused group return Ignored") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = w.handleEvent(SpecialKey(SpecialKeyCode.Down, Set.empty), unfocusedCtx)
          assertTrue(w.selected == 0, res == EventResult.Ignored)
      },
      test("arrows on a disabled focused group return Ignored") {
        for
          w <- RadioGroup.make(options, enabled = false)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Down, Set.empty))
          assertTrue(w.selected == 0, res == EventResult.Ignored)
      }
    ),

    // ===== Horizontal layout =====

    suite("horizontal layout")(
      test("all options on one row, gap cells apart") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle, layout = horizontal)
        yield
          val buf = renderToBuffer(26, 2)(w, ctx = unfocusedCtx)
          assertTrue(
            row(buf, 0) == "○ Alpha  ● Beta  ○ Gamma  ",
            row(buf, 1).isBlank
          )
      },
      test("a custom gap, including zero") {
        for
          wide  <- RadioGroup.make(Seq("a", "b"), style = baseStyle, layout = RadioGroup.Layout.Horizontal(gap = 4))
          tight <- RadioGroup.make(Seq("a", "b"), style = baseStyle, layout = RadioGroup.Layout.Horizontal(gap = 0))
        yield assertTrue(
          row(renderToBuffer(10, 1)(wide, ctx = unfocusedCtx), 0) == "● a    ○ b",
          row(renderToBuffer(6, 1)(tight, ctx = unfocusedCtx), 0) == "● a○ b"
        )
      },
      test("the option reaching the right edge is truncated; later ones are not drawn") {
        for
          w <- RadioGroup.make(options, style = baseStyle, layout = horizontal)
        yield
          // Alpha ends at column 6; Beta's mark starts at 9 with three cells left.
          val buf = renderToBuffer(16, 1)(w, area = Rect(0, 0, 12, 1), ctx = unfocusedCtx)
          assertTrue(row(buf, 0) == "● Alpha  ○ B    ")
      },
      test("an option with no room for one label cell is not drawn") {
        for
          w <- RadioGroup.make(options, style = baseStyle, layout = horizontal)
        yield
          val buf = renderToBuffer(16, 1)(w, area = Rect(0, 0, 11, 1), ctx = unfocusedCtx)
          assertTrue(row(buf, 0) == "● Alpha         ")
      },
      test("CJK labels are laid out by cells") {
        for
          w <- RadioGroup.make(Seq("日本語", "中文"), style = baseStyle, layout = RadioGroup.Layout.Horizontal(gap = 1))
        yield
          val buf = renderToBuffer(16, 1)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(2, 0).map(_.text).contains("日"),
            buf.get(6, 0).map(_.text).contains("語"),
            buf.get(9, 0).map(_.char).contains('○'),
            buf.get(11, 0).map(_.text).contains("中"),
            buf.get(13, 0).map(_.text).contains("文")
          )
      },
      test("selected label gains Bold when focused; unselected options are Dim") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle, layout = horizontal)
        yield
          val buf = renderToBuffer(26, 1)(w, ctx = focusCtx(w))
          assertTrue(
            buf.get(11, 0).map(_.style.attributes.contains(Attribute.Bold)).contains(true),
            buf.get(2, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true),
            buf.get(19, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
          )
      },
      test("disabled adds Dim to every option") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle, enabled = false, layout = horizontal)
        yield
          val buf = renderToBuffer(26, 1)(w, ctx = focusCtx(w))
          assertTrue(
            Seq(0, 2, 9, 11, 17, 19).forall(x =>
              buf.get(x, 0).map(_.style.attributes.contains(Attribute.Dim)).contains(true)
            )
          )
      },
      test("preferred height is one row, zero when empty") {
        for
          w     <- RadioGroup.make(options, layout = horizontal)
          empty <- RadioGroup.make(Seq.empty, layout = horizontal)
        yield assertTrue(w.preferredHeight(20).contains(1), empty.preferredHeight(20).contains(0))
      },
      test("Right advances and Left retracts, each performing onSelect") {
        for
          w <- RadioGroup.make(options, layout = horizontal)
        yield
          val right = keyPress(w, key(SpecialKeyCode.Right))
          val afterRight = w.selected
          val left = keyPress(w, key(SpecialKeyCode.Left))
          assertTrue(isPerform(right), afterRight == 1, isPerform(left), w.selected == 0)
      },
      test("Up / Down are off-axis for a horizontal group and bubble") {
        for
          w <- RadioGroup.make(options, selected = 1, layout = horizontal)
        yield
          val up = keyPress(w, key(SpecialKeyCode.Up))
          val down = keyPress(w, key(SpecialKeyCode.Down))
          assertTrue(up == EventResult.Ignored, down == EventResult.Ignored, w.selected == 1)
      },
      test("no wrap-around at either end") {
        for
          first <- RadioGroup.make(options, layout = horizontal)
          last  <- RadioGroup.make(options, selected = 2, layout = horizontal)
        yield assertTrue(
          keyPress(first, key(SpecialKeyCode.Left)) == EventResult.Ignored,
          keyPress(last, key(SpecialKeyCode.Right)) == EventResult.Ignored
        )
      },
      test("Home / End jump to first / last") {
        for
          w <- RadioGroup.make(options, selected = 1, layout = horizontal)
        yield
          keyPress(w, key(SpecialKeyCode.End))
          val atEnd = w.selected
          keyPress(w, key(SpecialKeyCode.Home))
          assertTrue(atEnd == 2, w.selected == 0)
      },
      test("disabled refuses every key") {
        for
          w <- RadioGroup.make(options, enabled = false, layout = horizontal)
        yield assertTrue(keyPress(w, key(SpecialKeyCode.Right)) == EventResult.Ignored, w.selected == 0)
      },
      test("a negative gap is rejected") {
        for exit <- RadioGroup.make(options, layout = RadioGroup.Layout.Horizontal(gap = -1)).exit
        yield assertTrue(exit.isFailure)
      }
    ),

    // ===== Clamping =====

    suite("initial selection clamping")(
      test("negative initial selected is clamped to 0") {
        for
          w <- RadioGroup.make(options, selected = -3)
        yield assertTrue(w.selected == 0)
      },
      test("out-of-range initial selected is clamped to the last option") {
        for
          w <- RadioGroup.make(options, selected = 99)
        yield assertTrue(w.selected == 2)
      }
    )
  )
