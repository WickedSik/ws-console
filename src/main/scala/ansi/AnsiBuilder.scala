package io.github.wickedsik.wsconsole
package ansi

/**
 * Immutable builder for constructing ANSI escape sequences.
 *
 * This builder provides a fluent API for composing terminal control sequences
 * including cursor movement, colors, styles, and screen manipulation.
 *
 * All methods return a new AnsiBuilder instance, allowing for safe composition
 * and reuse of partial sequences.
 *
 * @param parts Internal vector of ANSI sequence parts
 */
final case class AnsiBuilder private (
  private val parts: Vector[String] = Vector.empty
):
  // ===== Internal Methods =====

  /** Append a sequence and return new instance */
  private def append(s: String): AnsiBuilder = AnsiBuilder(parts :+ s)

  // ===== Validation Helpers =====

  private def requirePositive(value: Int, name: String): Unit =
    require(value > 0, s"$name must be positive, got: $value")

  private def requireNonNegative(value: Int, name: String): Unit =
    require(value >= 0, s"$name must be non-negative, got: $value")

  private def requireRange(value: Int, min: Int, max: Int, name: String): Unit =
    require(value >= min && value <= max, s"$name must be between $min and $max, got: $value")

  // ===== Build & Composition =====

  /** Build final ANSI string from accumulated parts */
  def build: String = parts.mkString

  /** Compose two builders together */
  def ++(other: AnsiBuilder): AnsiBuilder = AnsiBuilder(parts ++ other.parts)

  // ===== Cursor Control =====

  /** Move cursor to home position (1,1) */
  def home: AnsiBuilder = append(Cursor.Codes.Home)

  /** Move cursor to specific position (1-indexed) */
  def moveTo(row: Int, col: Int): AnsiBuilder =
    requirePositive(row, "row")
    requirePositive(col, "col")
    append(Cursor.Templates.Position(row, col))

  /** Move cursor up n lines */
  def moveUp(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.Up(n))

  /** Move cursor down n lines */
  def moveDown(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.Down(n))

  /** Move cursor right n columns */
  def moveRight(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.Forward(n))

  /** Move cursor left n columns */
  def moveLeft(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.Backward(n))

  /** Move cursor to specific column (1-indexed) */
  def moveToColumn(col: Int): AnsiBuilder =
    requirePositive(col, "col")
    append(Cursor.Templates.Column(col))

  /** Move cursor to beginning of next line, n lines down */
  def moveToNextLine(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.NextLine(n))

  /** Move cursor to beginning of previous line, n lines up */
  def moveToPrevLine(n: Int = 1): AnsiBuilder =
    requirePositive(n, "n")
    append(Cursor.Templates.PrevLine(n))

  /** Save current cursor position (DEC format - more compatible) */
  def saveCursor: AnsiBuilder = append(Cursor.Codes.SaveDec)

  /** Restore previously saved cursor position (DEC format) */
  def restoreCursor: AnsiBuilder = append(Cursor.Codes.RestoreDec)

  // ===== Cursor Visibility & Shape =====

  /** Hide the cursor */
  def hideCursor: AnsiBuilder = append(CursorVisibility.Hide)

  /** Show the cursor */
  def showCursor: AnsiBuilder = append(CursorVisibility.Show)

  /** Set cursor shape */
  def cursorShape(shape: CursorShape): AnsiBuilder = append(shape.toAnsi)

  // ===== Screen Control =====

  /** Clear entire screen */
  def clearScreen: AnsiBuilder = append(Screen.ClearAll)

  /** Clear from cursor to end of screen */
  def clearToEnd: AnsiBuilder = append(Screen.ClearFromCursor)

  /** Clear from start of screen to cursor */
  def clearToStart: AnsiBuilder = append(Screen.ClearToCursor)

  /** Clear screen and scrollback buffer */
  def clearScrollback: AnsiBuilder = append(Screen.ClearAllWithScrollback)

  /** Clear entire current line */
  def clearLine: AnsiBuilder = append(Screen.ClearLine)

  /** Clear from cursor to end of line */
  def clearLineToEnd: AnsiBuilder = append(Screen.ClearLineFromCursor)

  /** Clear from start of line to cursor */
  def clearLineToStart: AnsiBuilder = append(Screen.ClearLineToCursor)

  // ===== Colors =====

  /** Set foreground color using enum */
  def fg(color: FgColor): AnsiBuilder = append(color.toAnsi)

  /** Set background color using enum */
  def bg(color: BgColor): AnsiBuilder = append(color.toAnsi)

  /** Set foreground color using 256-color palette (0-255) */
  def fg256(index: Int): AnsiBuilder =
    requireRange(index, 0, 255, "index")
    append(Color.Templates.Fg256(index))

  /** Set background color using 256-color palette (0-255) */
  def bg256(index: Int): AnsiBuilder =
    requireRange(index, 0, 255, "index")
    append(Color.Templates.Bg256(index))

  /** Set foreground color using RGB (0-255 per component) */
  def fgRgb(r: Int, g: Int, b: Int): AnsiBuilder =
    requireRange(r, 0, 255, "r")
    requireRange(g, 0, 255, "g")
    requireRange(b, 0, 255, "b")
    append(Color.Templates.FgRgb(r, g, b))

  /** Set background color using RGB (0-255 per component) */
  def bgRgb(r: Int, g: Int, b: Int): AnsiBuilder =
    requireRange(r, 0, 255, "r")
    requireRange(g, 0, 255, "g")
    requireRange(b, 0, 255, "b")
    append(Color.Templates.BgRgb(r, g, b))

  /** Reset foreground to default */
  def defaultFg: AnsiBuilder = append(FgColor.Default.toAnsi)

  /** Reset background to default */
  def defaultBg: AnsiBuilder = append(BgColor.Default.toAnsi)

  // ===== Text Styling =====

  /** Enable bold text */
  def bold: AnsiBuilder = append(Style.Bold)

  /** Enable dim/faint text */
  def dim: AnsiBuilder = append(Style.Dim)

  /** Enable italic text */
  def italic: AnsiBuilder = append(Style.Italic)

  /** Enable underlined text */
  def underline: AnsiBuilder = append(Style.Underline)

  /** Enable blinking text */
  def blink: AnsiBuilder = append(Style.Blink)

  /** Enable reverse video (swap fg/bg) */
  def reverse: AnsiBuilder = append(Style.Reverse)

  /** Enable hidden/invisible text */
  def hidden: AnsiBuilder = append(Style.Hidden)

  /** Enable strikethrough text */
  def strikethrough: AnsiBuilder = append(Style.Strikethrough)

  /** Reset all styles and colors */
  def reset: AnsiBuilder = append(Style.Reset)

  /** Disable bold and dim (ANSI spec disables both together) */
  def noBold: AnsiBuilder = append(StyleDisable.BoldDim)

  /** Disable bold and dim (ANSI spec disables both together) */
  def noDim: AnsiBuilder = append(StyleDisable.BoldDim)

  /** Disable italic */
  def noItalic: AnsiBuilder = append(StyleDisable.Italic)

  /** Disable underline */
  def noUnderline: AnsiBuilder = append(StyleDisable.Underline)

  /** Disable blink */
  def noBlink: AnsiBuilder = append(StyleDisable.Blink)

  /** Disable reverse video */
  def noReverse: AnsiBuilder = append(StyleDisable.Reverse)

  /** Disable hidden text */
  def noHidden: AnsiBuilder = append(StyleDisable.Hidden)

  /** Disable strikethrough */
  def noStrikethrough: AnsiBuilder = append(StyleDisable.Strikethrough)

  // ===== Scroll Regions =====

  /** Set scrolling region to specific lines (1-indexed) */
  def setScrollRegion(top: Int, bottom: Int): AnsiBuilder =
    requirePositive(top, "top")
    requirePositive(bottom, "bottom")
    require(bottom >= top, s"bottom ($bottom) must be >= top ($top)")
    append(Scroll.Templates.SetRegion(top, bottom))

  /** Reset scrolling region to full screen */
  def resetScrollRegion: AnsiBuilder = append(Scroll.Codes.ResetRegion)

  /** Scroll up one line */
  def scrollUp: AnsiBuilder = append(Scroll.Codes.Up)

  /** Scroll down one line */
  def scrollDown: AnsiBuilder = append(Scroll.Codes.Down)

  // ===== Alternate Buffer =====

  /** Switch to alternate screen buffer */
  def enterAltBuffer: AnsiBuilder = append(AlternateBuffer.Enter)

  /** Return to normal screen buffer */
  def exitAltBuffer: AnsiBuilder = append(AlternateBuffer.Exit)

  // ===== Mouse Support =====

  /** Enable basic mouse tracking (clicks only) */
  def enableMouse: AnsiBuilder = append(Mouse.EnableNormal)

  /** Enable mouse button events (clicks + drags) */
  def enableMouseButton: AnsiBuilder = append(Mouse.EnableButton)

  /** Enable all mouse events (motion tracking) */
  def enableMouseAny: AnsiBuilder = append(Mouse.EnableAny)

  /** Enable SGR extended mouse mode */
  def enableMouseSgr: AnsiBuilder = append(Mouse.EnableSgr)

  /** Disable all mouse tracking modes */
  def disableMouse: AnsiBuilder =
    append(Mouse.DisableNormal)
      .append(Mouse.DisableButton)
      .append(Mouse.DisableAny)
      .append(Mouse.DisableSgr)

  // ===== Terminal Modes =====

  /** Enable automatic line wrapping */
  def lineWrapOn: AnsiBuilder = append(Mode.LineWrapOn)

  /** Disable automatic line wrapping */
  def lineWrapOff: AnsiBuilder = append(Mode.LineWrapOff)

  /** Enable bracketed paste mode */
  def bracketedPasteOn: AnsiBuilder = append(Mode.BracketedPasteOn)

  /** Disable bracketed paste mode */
  def bracketedPasteOff: AnsiBuilder = append(Mode.BracketedPasteOff)

  // ===== Text Content =====

  /** Append plain text */
  def text(s: String): AnsiBuilder = append(s)

  /** Append text with newline */
  def line(s: String): AnsiBuilder = append(s + "\n")

  /** Append newline */
  def newline: AnsiBuilder = append("\n")

  /** Append raw ANSI sequence (no validation) */
  def raw(sequence: String): AnsiBuilder = append(sequence)

  // ===== Terminal Queries =====

  /** Query cursor position (response: ESC[row;colR) */
  def queryCursorPosition: AnsiBuilder = append(Query.CursorPosition)

  /** Query device attributes */
  def queryDeviceAttributes: AnsiBuilder = append(Query.DeviceAttributes)

  // ===== Full Reset =====

  /** Perform full terminal reset (RIS) */
  def fullReset: AnsiBuilder = append(Reset.Full)

object AnsiBuilder:
  /** Create empty builder */
  def apply(): AnsiBuilder = new AnsiBuilder(Vector.empty)

  /** Create builder with initial text */
  def text(s: String): AnsiBuilder = AnsiBuilder().text(s)
