package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{FocusSnapshot, RenderContext}
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
 * the header, hint and button band, and the button cycling the dither mode.
 */
object BrailleImagePanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  // Header (3), hint (1), button band (3).
  private val ImageTop = 7

  private def isBraille(c: Char): Boolean = c >= '⠁' && c <= '⣿'

  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  /** Press Enter on the panel's focused button and run what it performs. */
  private def press(p: BrailleImagePanel, harness: FrameHarness): Task[Unit] =
    val ctx = RenderContext(FocusSnapshot(Some(p.ditherButton.id)), Instant.EPOCH)
    p.ditherButton.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), ctx) match
      case EventResult.Perform(effect) => effect.provideLayer(harness.frameLayer)
      case other                       => ZIO.fail(new AssertionError(s"expected Perform, got $other"))

  def spec: Spec[TestEnvironment & Scope, Any] = suite("BrailleImagePanel")(
    test("draws the header, names Dither.None, and puts the photo below the button band") {
      for p <- BrailleImagePanel.make
      yield
        val buf = renderToBuffer(W, H)(p.panel.root)
        val text = rows(buf)
        val imageRows = text.indices.filter(y => text(y).exists(isBraille))
        assertTrue(
          p.dither == Dither.None,
          text(1).contains("Braille Image"),
          text(5).contains("Cycle dither"),
          text(5).contains("Dither.None"),
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
                  press(p, harness).as(p.dither -> rows(renderToBuffer(W, H)(p.panel.root)))
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
    }
  )
