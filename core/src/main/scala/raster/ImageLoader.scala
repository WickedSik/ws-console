package io.github.wickedsik.wsconsole
package raster

import zio.{IO, ZIO}

import java.awt.image.BufferedImage
import java.io.{IOException, InputStream}
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Reads image files into [[RasterizedBitmap]]s through `javax.imageio`, which reads
 * PNG, JPEG, GIF and BMP out of the box.
 *
 * Kept apart from [[Braille]] so the encoder never touches I/O. A format
 * `ImageIO` cannot read fails with an `IOException`; it never yields a
 * `null` bitmap.
 */
object ImageLoader:

  /** Read the image at `path`. */
  def load(path: Path): IO[IOException, RasterizedBitmap] =
    ZIO.attemptBlockingIO(ImageIO.read(path.toFile)).flatMap(toBitmap(path.toString))

  /** Read the classpath resource `name` (e.g. `"logo.png"`) through `loader`. */
  def loadResource(name: String, loader: ClassLoader = getClass.getClassLoader): IO[IOException, RasterizedBitmap] =
    ZIO
      .acquireReleaseWith(open(name, loader))(in => ZIO.succeed(in.close()))(in =>
        ZIO.attemptBlockingIO(ImageIO.read(in))
      )
      .flatMap(toBitmap(name))

  private def open(name: String, loader: ClassLoader): IO[IOException, InputStream] =
    ZIO.attemptBlockingIO(Option(loader.getResourceAsStream(name))).someOrFail(
      new IOException(s"no such resource: $name")
    )

  // `ImageIO.read` answers an unreadable format with `null`, not an exception.
  private def toBitmap(source: String)(image: BufferedImage): IO[IOException, RasterizedBitmap] =
    image match
      case null => ZIO.fail(new IOException(s"unsupported image format: $source"))
      case img =>
        ZIO.attemptBlockingIO {
          val w = img.getWidth
          val h = img.getHeight
          RasterizedBitmap.fromArgb(w, h, img.getRGB(0, 0, w, h, null, 0, w))
        }
