package io.github.wickedsik.wsconsole
package raster

import zio.{Scope, ZIO}
import zio.test.*

import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO

object ImageLoaderSpec extends ZIOSpecDefault:

  /** A temp file deleted when the test's scope closes. */
  private def tempFile(suffix: String): ZIO[Scope, IOException, Path] =
    ZIO.acquireRelease(ZIO.attemptBlockingIO(Files.createTempFile("ws-console-", suffix)))(p =>
      ZIO.attemptBlockingIO(Files.deleteIfExists(p)).ignore
    )

  def spec: Spec[TestEnvironment & Scope, Any] = suite("ImageLoader")(
    test("loads a PNG with its size, luminance and alpha") {
      for
        path <- tempFile(".png")
        _ <- ZIO.attemptBlockingIO {
               val img = BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB)
               img.setRGB(0, 0, 0xFFFFFFFF)
               img.setRGB(1, 0, 0xFF000000)
               img.setRGB(2, 1, 0x00FFFFFF)
               ImageIO.write(img, "png", path.toFile)
             }
        b <- ImageLoader.load(path)
      yield assertTrue(
        b.width == 3,
        b.height == 2,
        b.luminance(0, 0) == 1.0f,
        b.alpha(0, 0) == 1.0f,
        b.luminance(1, 0) == 0.0f,
        b.alpha(2, 1) == 0.0f
      )
    },
    test("a missing file fails with an IOException") {
      for exit <- ImageLoader.load(Path.of("/no/such/image.png")).exit
      yield assertTrue(exit.isFailure)
    },
    test("a file ImageIO cannot read fails with an IOException, never a null bitmap") {
      for
        path  <- tempFile(".png")
        _     <- ZIO.attemptBlockingIO(Files.writeString(path, "not an image"))
        error <- ImageLoader.load(path).flip
      yield assertTrue(error.getMessage.startsWith("unsupported image format"))
    },
    test("a missing resource fails with an IOException") {
      for error <- ImageLoader.loadResource("no/such/resource.png").flip
      yield assertTrue(error.getMessage == "no such resource: no/such/resource.png")
    }
  )
