package io.github.wickedsik.wsconsole
package ansi

import zio.Scope
import zio.test.*

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

/**
 * Enforces the [[Csi]] single-source rule over the whole source tree.
 *
 * A raw 0x00 or 0x1B byte in a source file is invisible in every editor
 * that does not render control pictures, so it survives review and diffs
 * unnoticed — and a NUL reaching the wire corrupts the terminal. Review
 * discipline cannot catch what it cannot see, so the build does.
 *
 * Enforced:
 *   - no raw NUL or ESC byte in any `.scala` file
 *   - no NUL or ESC escape text outside `Csi.scala` — `Csi` holds the only
 *     definition of either byte
 *
 * The escape-text rule matters beyond tidiness: tooling that decodes
 * `\uXXXX` while writing a file turns the escape into a raw byte, so every
 * site holding one is a latent source of the corruption the first rule
 * bans. Call sites compose from `Csi.ESC` instead — `s"${Csi.ESC}[H"`
 * keeps the visible `[` that made the inline form readable.
 */
object ControlByteHygieneSpec extends ZIOSpecDefault:

  private val SourceRoot = Paths.get("src")
  private val CsiSource  = "Csi.scala"

  // Assembled at runtime so this file does not contain the sequences it bans.
  private val NulEscape: String = "" + '\\' + "u0000"
  private val EscEscape: String = "" + '\\' + "u001B"

  private def scalaSources: List[Path] =
    val walk = Files.walk(SourceRoot)
    try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toList
    finally walk.close()

  /** Files containing `byte`, reported as "path (count)" so failures name the offender. */
  private def rawByteOffenders(byte: Byte): String =
    scalaSources
      .map(p => (p.toString, Files.readAllBytes(p).count(_ == byte)))
      .collect { case (path, n) if n > 0 => s"$path ($n)" }
      .mkString(", ")

  /** Files outside `Csi.scala` holding `needle`, case-insensitively. */
  private def escapeOffenders(needle: String): String =
    scalaSources
      .filterNot(_.getFileName.toString == CsiSource)
      .map { p =>
        val text = Files.readString(p).toLowerCase
        (p.toString, text.sliding(needle.length).count(_ == needle.toLowerCase))
      }
      .collect { case (path, n) if n > 0 => s"$path ($n)" }
      .mkString(", ")

  def spec: Spec[TestEnvironment & Scope, Any] = suite("control-byte hygiene")(

    // Without this, a wrong working directory would scan zero files and
    // every rule below would pass vacuously, forever.
    test("the scan reaches the source tree") {
      assertTrue(scalaSources.size > 50)
    },

    test("no source file contains a raw NUL byte") {
      assertTrue(rawByteOffenders(0x00.toByte).isEmpty)
    },

    test("no source file contains a raw ESC byte") {
      assertTrue(rawByteOffenders(0x1b.toByte).isEmpty)
    },

    test("Csi.NUL is the only definition of the NUL byte") {
      assertTrue(escapeOffenders(NulEscape).isEmpty)
    },

    test("Csi.ESC is the only definition of the ESC byte") {
      assertTrue(escapeOffenders(EscEscape).isEmpty)
    }
  )
