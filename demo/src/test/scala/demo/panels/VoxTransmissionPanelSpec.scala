package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.BrailleImage
import raster.{Braille, ImageLoader, Polarity}
import testkit.RenderHarness.renderToBuffer

import zio.Scope
import zio.test.*

/**
 * [[VoxTransmissionPanel]] at the demo's content size: header and text
 * rows, and the trimmed Aquila contained and centred in the rows left over.
 */
object VoxTransmissionPanelSpec extends ZIOSpecDefault:

  private val W = 80
  private val H = 21

  private def isBraille(c: Char): Boolean = c >= '⠁' && c <= '⣿'

  /** Columns holding a Braille glyph on row `y`. */
  private def brailleColumns(buf: ScreenBuffer, y: Int): Seq[Int] =
    (0 until buf.width).filter(x => buf.get(x, y).exists(c => isBraille(c.char)))

  // Header (3), origin line and rule above the image; rule and hint below.
  private val ImageTop = 5
  private val ImageRows = H - ImageTop - 2

  def spec: Spec[TestEnvironment & Scope, Any] = suite("VoxTransmissionPanel")(
    test("draws the header and the trimmed Aquila, contained and centred below the text") {
      for
        panel  <- VoxTransmissionPanel.make
        aquila <- ImageLoader.loadResource(VoxTransmissionPanel.Resource)
      yield
        val trimmed = aquila.trim(Polarity.BrightInk, BrailleImage.DefaultThreshold)
        val (cols, rows) = Braille.fit(trimmed, W, ImageRows)
        val left = (W - cols) / 2
        val top = ImageTop + (ImageRows - rows) / 2
        val buf = renderToBuffer(W, H)(panel.root)
        val text = (0 until H).map(y => (0 until W).flatMap(x => buf.get(x, y).map(_.text)).mkString)
        val imageRows = (0 until H).filter(y => brailleColumns(buf, y).nonEmpty)
        val columns = imageRows.flatMap(brailleColumns(buf, _))
        assertTrue(
          text(1).contains("+++ Vox Transmission +++"),
          trimmed.width < aquila.width || trimmed.height < aquila.height,
          imageRows.nonEmpty,
          imageRows.min >= top,
          imageRows.max < top + rows,
          columns.min >= left,
          columns.max < left + cols
        )
    }
  )
