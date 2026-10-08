package io.github.wickedsik.wsconsole
package buffer

/**
 * A text value with a caret: the pure editing model shared by
 * `TextInput` and `TextArea`.
 *
 * `caret` is a char offset into `value` and always sits on a grapheme
 * cluster boundary, so no edit or move ever splits a surrogate pair, an
 * emoji ZWJ sequence, or a letter from its combining accent. `\n` is an
 * ordinary cluster here: moving across it crosses a line, deleting it
 * joins two lines. Visual-line motion (wrap-aware `Up` / `Down` /
 * `Home` / `End`) belongs to the view, which places the caret with
 * [[moveTo]].
 *
 * Operations that can do nothing (`Backspace` at the start, `Left` at
 * the start, `Delete` at the end) return `None`, so a widget can answer
 * `Ignored` and let the key bubble.
 *
 * Word motion treats a cluster as part of a word when its first
 * codepoint is a letter, a digit, or `_`. On ASCII this is exactly the
 * rule `TextInput` has always used.
 */
final case class TextEdit(value: String, caret: Int):
  require(caret >= 0 && caret <= value.length, s"caret $caret outside [0, ${value.length}]")

  /** Insert `text` at the caret; the caret ends after it. */
  def insert(text: String): TextEdit =
    if text.isEmpty then this
    else
      val next = value.substring(0, caret) + text + value.substring(caret)
      TextEdit(next, Graphemes.ceiling(next, caret + text.length))

  /** Remove the cluster before the caret. */
  def backspace: Option[TextEdit] =
    if caret == 0 then None
    else
      val from = Graphemes.preceding(value, caret)
      Some(TextEdit(value.substring(0, from) + value.substring(caret), from))

  /** Remove the cluster after the caret. */
  def delete: Option[TextEdit] =
    if caret >= value.length then None
    else
      val to = Graphemes.following(value, caret)
      Some(TextEdit(value.substring(0, caret) + value.substring(to), caret))

  /** One cluster left. */
  def left: Option[TextEdit] = moveTo(Graphemes.preceding(value, caret))

  /** One cluster right. */
  def right: Option[TextEdit] = moveTo(Graphemes.following(value, caret))

  /** Back over any non-word clusters, then over the word before them. */
  def wordLeft: Option[TextEdit] =
    var i = caret
    while i > 0 && !isWordBefore(i) do i = Graphemes.preceding(value, i)
    while i > 0 && isWordBefore(i) do i = Graphemes.preceding(value, i)
    moveTo(i)

  /** Forward over any non-word clusters, then over the word after them. */
  def wordRight: Option[TextEdit] =
    var i = caret
    while i < value.length && !isWordAt(i) do i = Graphemes.following(value, i)
    while i < value.length && isWordAt(i) do i = Graphemes.following(value, i)
    moveTo(i)

  /**
   * Place the caret at `offset`, clamped to the value and moved back to a
   * cluster boundary when it falls inside one. `None` when it would not
   * move.
   */
  def moveTo(offset: Int): Option[TextEdit] =
    val next = Graphemes.floor(value, math.max(0, math.min(value.length, offset)))
    if next == caret then None else Some(copy(caret = next))

  private def isWordAt(i: Int): Boolean = TextEdit.isWordCodepoint(value.codePointAt(i))

  private def isWordBefore(i: Int): Boolean =
    isWordAt(Graphemes.preceding(value, i))

object TextEdit:

  /** `value` with the caret at its end, where a seeded field starts. */
  def atEnd(value: String): TextEdit = TextEdit(value, value.length)

  private def isWordCodepoint(cp: Int): Boolean =
    Character.isLetterOrDigit(cp) || cp == '_'
