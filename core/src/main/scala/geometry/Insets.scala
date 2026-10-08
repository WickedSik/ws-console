package io.github.wickedsik.wsconsole
package geometry

/**
 * Per-edge spacing measured in terminal cells.
 *
 * The geometry primitive behind component padding. Component-side use
 * (`Rect.inner(insets)`) is the intended composition: framed components
 * lay out as `area.inner(border).inner(padding)`, where `border` yields
 * a uniform frame inset and `padding` yields per-edge content spacing.
 *
 * Values are not validated. Callers that pass negative or oversized
 * values get the same permissive behaviour as [[Rect.inner]] — the
 * derived rect clamps to zero rather than throwing.
 */
final case class Insets(top: Int, right: Int, bottom: Int, left: Int)

object Insets:
  val zero: Insets = Insets(0, 0, 0, 0)

  /** Uniform inset on every edge. */
  def all(n: Int): Insets = Insets(n, n, n, n)

  /** Horizontal inset on left/right, vertical inset on top/bottom. */
  def symmetric(horizontal: Int, vertical: Int): Insets =
    Insets(vertical, horizontal, vertical, horizontal)
