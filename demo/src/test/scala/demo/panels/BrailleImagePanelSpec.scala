package io.github.wickedsik.wsconsole
package demo.panels

import buffer.{Foreground, ScreenBuffer}
import component.{Button, Coloring, FocusSnapshot, RenderContext}
import event.EventResult
import event.KeyEvent.SpecialKey
import event.SpecialKeyCode
import raster.Dither
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

import java.time.Instant

/**
 * [[BrailleImagePanel]] at the demo's content size: the photo drawn below
 * the header, hint, button band and description, one button cycling the
 * dither mode and the other toggling the colouring.
 */
object BrailleImagePanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  // Header (3), hint (1), button band (3), description (1).
  private val ImageTop = 8

  private def isBraille(c: Char): Boolean = c >= '⠁' && c <= '⣿'

  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  /** Press Enter on `button`, focused, and run what it performs. */
  private def press(button: Button, harness: FrameHarness): Task[Unit] =
    val ctx = RenderContext(FocusSnapshot(Some(button.id)), Instant.EPOCH)
    button.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), ctx) match
      case EventResult.Perform(effect) => effect.provideLayer(harness.frameLayer)
      case other                       => ZIO.fail(AssertionError(s"expected Perform, got $other"))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BrailleImagePanel")(
    test("draws the header, names Dither.None in Coloring.Uniform, and puts the photo below the description") {
      for p <- BrailleImagePanel.make
      yield
        val buf = renderToBuffer(W, H)(p.panel.root)
        val text = rows(buf)
        val imageRows = text.indices.filter(y => text(y).exists(isBraille))
        assertTrue(
          p.dither == Dither.None,
          p.coloring == Coloring.Uniform,
          text(1).contains("Braille Image"),
          text(5).contains("Cycle dither"),
          text(5).contains("Toggle colour"),
          text(5).contains("Dither.None · Coloring.Uniform"),
          text(7).contains("threshold cut"),
          imageRows.nonEmpty,
          imageRows.min >= ImageTop
        )
    },
    test("Enter on the button cycles every mode in order and wraps; the image follows") {
      for
        p       <- BrailleImagePanel.make
        harness <- FrameHarness.make(W, H)
        before = rows(renderToBuffer(W, H)(p.panel.root))
        seen <- ZIO.foreach(Dither.values.toVector) { _ =>
                  press(p.ditherButton, harness).as(p.dither -> rows(renderToBuffer(W, H)(p.panel.root)))
                }
      yield
        val modes = seen.map(_._1)
        val floyd = seen(1)._2
        assertTrue(
          modes == Vector(Dither.Ordered, Dither.FloydSteinberg, Dither.Atkinson, Dither.None),
          seen.forall((d, text) => text(5).contains(s"Dither.$d")),
          floyd.drop(ImageTop) != before.drop(ImageTop),
          seen.last._2 == before
        )
    },
    test("Toggle colour switches to Coloring.Source and back; lit cells take truecolor") {
      def truecolor(buf: ScreenBuffer): Int =
        (for y <- ImageTop until H; x <- 0 until W yield buf.get(x, y)).flatten.map(_.style.fg).collect {
          case rgb: Foreground.Rgb => rgb
        }.size
      for
        p       <- BrailleImagePanel.make
        harness <- FrameHarness.make(W, H)
        uniform = renderToBuffer(W, H)(p.panel.root)
        _ <- press(p.coloringButton, harness)
        source = renderToBuffer(W, H)(p.panel.root)
        sourceColoring = p.coloring
        _ <- press(p.coloringButton, harness)
      yield assertTrue(
        truecolor(uniform) == 0,
        sourceColoring == Coloring.Source,
        rows(source)(5).contains("Coloring.Source"),
        truecolor(source) > 0,
        p.coloring == Coloring.Uniform,
        p.dither == Dither.None
      )
    }
  )
