package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, Frame, ScreenBuffer}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{Event, EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import testkit.FrameHarness
import testkit.RenderHarness.*

import zio.*
import zio.test.*

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

object SliderSpec extends ZIOSpecDefault:

  private val baseStyle = CellStyle(fg = Foreground.Named(FgColor.BrightCyan))

  private def focusCtx(w: Slider): RenderContext = RenderContext(FocusSnapshot(Some(w.id)))

  private def press(w: Slider, event: Event): EventResult = w.handleEvent(event, focusCtx(w))

  private def key(code: SpecialKeyCode, mods: KeyModifier*): Event = SpecialKey(code, mods.toSet)

  /** A unit slider: [0, 1] in steps of 0.1. */
  private def unit(value: Double, default: Option[Double] = None, enabled: Boolean = true): UIO[Slider] =
    Slider.make(0, 1, 0.1, value, default = default, style = baseStyle, enabled = enabled)

  /** A Top K style integer slider: [0, 100] in steps of 1. */
  private def integral(value: Double, pageSteps: Int = 10): UIO[Slider] =
    Slider.make(0, 100, 1, value, pageSteps = pageSteps)

  private def isPerform(res: EventResult): Boolean =
    res match
      case EventResult.Perform(_) => true
      case _                      => false

  /** Run a `Perform` effect against a real `Frame`. */
  private def perform(res: EventResult): IO[IOException, Unit] =
    res match
      case EventResult.Perform(effect) => FrameHarness.make(1, 1).flatMap(h => effect.provideLayer(h.frameLayer))
      case _                           => ZIO.unit

  private def track(buf: ScreenBuffer): String = buf.glyphGrid.head

  private def caption(buf: ScreenBuffer): String = buf.glyphGrid(1)

  private def thumbColumn(buf: ScreenBuffer): Int = track(buf).indexOf('●')

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Slider")(
    suite("value")(
      test("an initial value outside the range is clamped") {
        for
          below <- unit(-5)
          above <- unit(7)
          nan   <- unit(Double.NaN)
        yield assertTrue(below.value == 0.0, above.value == 1.0, nan.value == 0.0)
      },
      test("an initial value snaps to the nearest step") {
        for
          down <- unit(0.84)
          up   <- unit(0.86)
        yield assertTrue(down.value == 0.8, up.value == 0.9)
      },
      test("repeated steps do not drift") {
        for
          w <- unit(0)
        yield
          (1 to 3).foreach(_ => press(w, key(SpecialKeyCode.Right)))
          val three = w.value
          (1 to 5).foreach(_ => press(w, key(SpecialKeyCode.Right)))
          // 0.1 + 0.1 + 0.1 in Double arithmetic is 0.30000000000000004.
          assertTrue(three == 0.3, w.value == 0.8)
      },
      test("a range that is not a whole number of steps still reaches max") {
        for
          w <- Slider.make(0, 1, 0.3, 0)
        yield
          press(w, key(SpecialKeyCode.End))
          val atEnd = w.value
          press(w, key(SpecialKeyCode.Left))
          assertTrue(atEnd == 1.0, w.value == 0.9)
      },
      test("snapping near max picks max when it is the nearer value") {
        for
          w <- Slider.make(0, 1, 0.3, 0)
        yield
          w.set(0.97)
          val nearMax = w.value
          w.set(0.93)
          assertTrue(nearMax == 1.0, w.value == 0.9)
      },
      test("set clamps and snaps without producing an event") {
        for
          w <- unit(0.5)
        yield
          w.set(0.71)
          val snapped = w.value
          w.set(42)
          assertTrue(snapped == 0.7, w.value == 1.0)
      },
      test("an invalid range, step or page stride is rejected") {
        for
          empty    <- Slider.make(1, 1, 0.1, 1).exit
          reversed <- Slider.make(1, 0, 0.1, 0).exit
          noStep   <- Slider.make(0, 1, 0, 0).exit
          noPage   <- Slider.make(0, 1, 0.1, 0, pageSteps = 0).exit
        yield assertTrue(empty.isFailure, reversed.isFailure, noStep.isFailure, noPage.isFailure)
      }
    ),
    suite("keys")(
      test("Right and Left move one step and perform onChange with the new value") {
        for
          seen <- ZIO.succeed(new AtomicReference[Double](-1))
          w    <- Slider.make(0, 1, 0.1, 0.5, onChange = v => ZIO.succeed(seen.set(v)))
          right = press(w, key(SpecialKeyCode.Right))
          _    <- perform(right)
          afterRight = seen.get()
          left = press(w, key(SpecialKeyCode.Left))
          _    <- perform(left)
        yield assertTrue(isPerform(right), afterRight == 0.6, isPerform(left), seen.get() == 0.5, w.value == 0.5)
      },
      test("PgUp and PgDn move ten steps by default") {
        for
          w <- integral(30)
        yield
          press(w, key(SpecialKeyCode.PgUp))
          val up = w.value
          press(w, key(SpecialKeyCode.PgDn))
          press(w, key(SpecialKeyCode.PgDn))
          assertTrue(up == 40.0, w.value == 20.0)
      },
      test("pageSteps sets the PgUp / PgDn stride") {
        for
          w <- integral(30, pageSteps = 5)
        yield
          press(w, key(SpecialKeyCode.PgUp))
          assertTrue(w.value == 35.0)
      },
      test("a page move stops at the bound, and the next one is Ignored") {
        for
          w <- integral(95)
        yield
          val first = press(w, key(SpecialKeyCode.PgUp))
          val second = press(w, key(SpecialKeyCode.PgUp))
          assertTrue(isPerform(first), w.value == 100.0, second == EventResult.Ignored)
      },
      test("Home and End jump to the bounds") {
        for
          w <- integral(30)
        yield
          press(w, key(SpecialKeyCode.End))
          val atEnd = w.value
          press(w, key(SpecialKeyCode.Home))
          assertTrue(atEnd == 100.0, w.value == 0.0)
      },
      test("a step past either bound is Ignored") {
        for
          low  <- unit(0)
          high <- unit(1)
        yield assertTrue(
          press(low, key(SpecialKeyCode.Left)) == EventResult.Ignored,
          press(low, key(SpecialKeyCode.Home)) == EventResult.Ignored,
          press(high, key(SpecialKeyCode.Right)) == EventResult.Ignored,
          press(high, key(SpecialKeyCode.End)) == EventResult.Ignored
        )
      },
      test("Up / Down, Ctrl-chords and other keys bubble") {
        for
          w <- unit(0.5)
        yield assertTrue(
          press(w, key(SpecialKeyCode.Up)) == EventResult.Ignored,
          press(w, key(SpecialKeyCode.Down)) == EventResult.Ignored,
          press(w, key(SpecialKeyCode.Right, KeyModifier.Ctrl)) == EventResult.Ignored,
          press(w, key(SpecialKeyCode.End, KeyModifier.Ctrl)) == EventResult.Ignored,
          press(w, key(SpecialKeyCode.Tab)) == EventResult.Ignored,
          press(w, CharKey('x', Set.empty)) == EventResult.Ignored,
          w.value == 0.5
        )
      },
      test("an unfocused slider ignores keys") {
        for
          w <- unit(0.5)
        yield
          val res = w.handleEvent(key(SpecialKeyCode.Right), RenderContext.empty)
          assertTrue(res == EventResult.Ignored, w.value == 0.5)
      },
      test("a disabled slider refuses keys and leaves the focus cycle") {
        for
          w <- unit(0.5, enabled = false)
        yield assertTrue(
          press(w, key(SpecialKeyCode.Right)) == EventResult.Ignored,
          w.value == 0.5,
          !w.focusable
        )
      }
    ),
    suite("rendering")(
      test("the thumb sits at min, mid and max on an odd width") {
        for
          low  <- unit(0)
          mid  <- unit(0.5)
          high <- unit(1)
        yield assertTrue(
          thumbColumn(renderToBuffer(11, 1)(low)) == 0,
          thumbColumn(renderToBuffer(11, 1)(mid)) == 5,
          thumbColumn(renderToBuffer(11, 1)(high)) == 10
        )
      },
      test("the thumb sits at min, mid and max on an even width") {
        for
          low  <- unit(0)
          mid  <- unit(0.5)
          high <- unit(1)
        yield assertTrue(
          thumbColumn(renderToBuffer(10, 1)(low)) == 0,
          // 0.5 * 9 = 4.5 rounds half up.
          thumbColumn(renderToBuffer(10, 1)(mid)) == 5,
          thumbColumn(renderToBuffer(10, 1)(high)) == 9
        )
      },
      test("the rest of the row is track; there is no fill") {
        for
          w <- unit(0.5)
        yield assertTrue(track(renderToBuffer(11, 1)(w)) == "─────●─────")
      },
      test("width 1 shows only the thumb, whatever the value") {
        for
          low  <- unit(0)
          high <- unit(1)
        yield assertTrue(
          track(renderToBuffer(1, 1)(low)) == "●",
          track(renderToBuffer(1, 1)(high)) == "●"
        )
      },
      test("the default label centres on the default's column") {
        for
          w <- unit(0.2, default = Some(0.5))
        yield
          // Column round(0.5 * 20) = 10; "Default" is 7 wide, so it starts at 7.
          assertTrue(caption(renderToBuffer(21, 2)(w)) == ".......Default.......")
      },
      test("the default label clamps at the left edge") {
        for
          w <- unit(0.5, default = Some(0))
        yield assertTrue(caption(renderToBuffer(21, 2)(w)) == "Default..............")
      },
      test("the default label clamps at the right edge") {
        for
          w <- unit(0.5, default = Some(1))
        yield assertTrue(caption(renderToBuffer(21, 2)(w)) == "..............Default")
      },
      test("a default label wider than the slider truncates by cells") {
        for
          narrow <- unit(0.5, default = Some(0.5))
          cjk    <- Slider.make(0, 1, 0.1, 0.5, default = Some(0.5), defaultLabel = "既定値です")
        yield assertTrue(
          caption(renderToBuffer(5, 2)(narrow)) == "Defau",
          // Five cells hold two wide glyphs; the third would straddle the edge.
          renderToBuffer(5, 2)(cjk).get(0, 1).map(_.text).contains("既"),
          renderToBuffer(5, 2)(cjk).get(2, 1).map(_.text).contains("定"),
          renderToBuffer(5, 2)(cjk).get(4, 1).map(_.char).contains(' ')
        )
      },
      test("a one-row area draws the track and no label") {
        for
          w <- unit(0.5, default = Some(0.5))
        yield
          val buf = renderToBuffer(11, 2)(w, area = Rect(0, 0, 11, 1))
          assertTrue(track(buf) == "─────●─────", caption(buf) == "...........")
      },
      test("preferred height is 1, or 2 with a default") {
        for
          plain   <- unit(0.5)
          marked  <- unit(0.5, default = Some(0.8))
        yield assertTrue(plain.preferredHeight(20).contains(1), marked.preferredHeight(20).contains(2))
      }
    ),
    suite("states")(
      test("at rest: track Dim, thumb in the base style") {
        for
          w <- unit(0.5)
        yield
          val buf = renderToBuffer(11, 1)(w)
          assertTrue(
            buf.styleAt(0, 0).exists(_.attributes.contains(Attribute.Dim)),
            buf.styleAt(5, 0).contains(baseStyle)
          )
      },
      test("focused: the thumb switches to the focus colour and gains Bold; the track is unchanged") {
        for
          w <- unit(0.5)
        yield
          val rest = renderToBuffer(11, 1)(w)
          val buf = renderToBuffer(11, 1)(w, ctx = focusCtx(w))
          assertTrue(
            buf.styleAt(5, 0).map(_.fg).contains(Slider.FocusedThumbColor),
            buf.styleAt(5, 0).exists(_.attributes.contains(Attribute.Bold)),
            buf.styleAt(0, 0) == rest.styleAt(0, 0)
          )
      },
      test("the default label is Dim") {
        for
          w <- unit(0.5, default = Some(0.5))
        yield assertTrue(renderToBuffer(11, 2)(w).styleAt(4, 1).exists(_.attributes.contains(Attribute.Dim)))
      },
      test("disabled: every cell is Dim, the thumb is not Bold") {
        for
          w <- unit(0.5, default = Some(0.5), enabled = false)
        yield
          val buf = renderToBuffer(11, 2)(w, ctx = focusCtx(w))
          assertTrue(
            (0 until 11).forall(x => buf.styleAt(x, 0).exists(_.attributes.contains(Attribute.Dim))),
            !buf.styleAt(5, 0).exists(_.attributes.contains(Attribute.Bold)),
            buf.styleAt(5, 0).map(_.fg).contains(baseStyle.fg)
          )
      }
    )
  )
