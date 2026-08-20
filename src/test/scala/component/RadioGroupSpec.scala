package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, Frame}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, SpecialKeyCode}
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

  def spec: Spec[TestEnvironment & Scope, Any] = suite("RadioGroup")(

    // ===== Mark & label rendering =====

    suite("rendering")(

      test("draws one option per row with the selected mark on the chosen row") {
        for
          w <- RadioGroup.make(options, selected = 1, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(0, 0).map(_.char).contains('○'),  // Alpha: unselected
            buf.get(0, 1).map(_.char).contains('●'),  // Beta:  selected
            buf.get(0, 2).map(_.char).contains('○')   // Gamma: unselected
          )
      },

      test("labels follow their mark after one space") {
        for
          w <- RadioGroup.make(options, style = baseStyle)
        yield
          val buf = renderToBuffer(12, 4)(w, ctx = unfocusedCtx)
          assertTrue(
            buf.get(2, 0).map(_.char).contains('A'),  // Alpha
            buf.get(2, 1).map(_.char).contains('B'),  // Beta
            buf.get(2, 2).map(_.char).contains('G')   // Gamma
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
          val focused   = renderToBuffer(12, 4)(w, ctx = focusCtx(w))
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
            res match { case EventResult.Perform(_) => true; case _ => false }
          )
      },

      test("Up retracts the selection") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Up, Set.empty))
          assertTrue(
            w.selected == 1,
            res match { case EventResult.Perform(_) => true; case _ => false }
          )
      },

      test("Right advances (accepted as a lenient alias for Down)") {
        for
          w <- RadioGroup.make(options)
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Right, Set.empty))
          assertTrue(w.selected == 1)
      },

      test("Left retracts (accepted as a lenient alias for Up)") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          keyPress(w, SpecialKey(SpecialKeyCode.Left, Set.empty))
          assertTrue(w.selected == 1)
      },

      test("Home jumps to the first option") {
        for
          w <- RadioGroup.make(options, selected = 2)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.Home, Set.empty))
          assertTrue(
            w.selected == 0,
            res match { case EventResult.Perform(_) => true; case _ => false }
          )
      },

      test("End jumps to the last option") {
        for
          w <- RadioGroup.make(options)
        yield
          val res = keyPress(w, SpecialKey(SpecialKeyCode.End, Set.empty))
          assertTrue(
            w.selected == 2,
            res match { case EventResult.Perform(_) => true; case _ => false }
          )
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
