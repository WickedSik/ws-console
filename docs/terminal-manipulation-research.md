# Terminal Manipulation Research

**Document Type:** Technical Research
**Date:** 2025-10-26
**Purpose:** Foundation research for Scala terminal manipulation library
**Status:** Complete

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [ANSI Escape Sequences Reference](#ansi-escape-sequences-reference)
3. [Core Terminal Manipulation Techniques](#core-terminal-manipulation-techniques)
4. [In-Place Update Strategies](#in-place-update-strategies)
5. [Scrolling Region Manipulation](#scrolling-region-manipulation)
6. [Box Drawing and Layout Systems](#box-drawing-and-layout-systems)
7. [Terminal Capability Detection](#terminal-capability-detection)
8. [Existing Library Analysis](#existing-library-analysis)
9. [Implementation Patterns](#implementation-patterns)
10. [Code Examples](#code-examples)
11. [Best Practices](#best-practices)
12. [Cross-Platform Considerations](#cross-platform-considerations)
13. [Performance Considerations](#performance-considerations)
14. [References](#references)

---

## Executive Summary

This document provides comprehensive research on terminal manipulation techniques for building a modern Scala terminal
UI library. The research covers:

- **ANSI escape sequences** for cursor control, text styling, and screen management
- **In-place update techniques** used by tools like progress bars and spinners
- **Scrolling region manipulation** for fixed status bars (APT-style)
- **Box drawing** and layout systems for TUI applications
- **Terminal capability detection** for graceful degradation
- **Existing libraries** in Scala, Java, and other ecosystems
- **Implementation patterns** from successful libraries like Ora, blessed, and tui-rs

The goal is to provide a solid foundation for implementing a type-safe, idiomatic Scala library for terminal
manipulation with minimal dependencies and maximum portability.

---

## ANSI Escape Sequences Reference

### Fundamental Structure

ANSI escape sequences begin with the ESC character (hexadecimal `0x1B`, octal `\033`, caret notation `^[`), followed by
command characters.

```
ESC [ <parameters> <command>
```

The `ESC [` combination is called **CSI** (Control Sequence Introducer).

### Common Representations

| Representation | Example    | Notes                       |
|----------------|------------|-----------------------------|
| Octal          | `\033[H`   | Common in C, shell scripts  |
| Hexadecimal    | `\x1B[H`   | Common in many languages    |
| Unicode        | `\u001B[H` | Java, Scala string literals |
| Caret          | `^[[H`     | Visual representation       |

### Cursor Movement Commands

| Function             | Sequence           | Parameters           | Description                  |
|----------------------|--------------------|----------------------|------------------------------|
| Cursor Up            | `ESC[<n>A`         | n = lines            | Move up n lines              |
| Cursor Down          | `ESC[<n>B`         | n = lines            | Move down n lines            |
| Cursor Forward       | `ESC[<n>C`         | n = columns          | Move right n columns         |
| Cursor Backward      | `ESC[<n>D`         | n = columns          | Move left n columns          |
| Cursor Position      | `ESC[<row>;<col>H` | row, col (1-indexed) | Absolute positioning         |
| Cursor Position Alt  | `ESC[<row>;<col>f` | row, col (1-indexed) | Same as H                    |
| Home Position        | `ESC[H`            | -                    | Move to (1,1)                |
| Column Position      | `ESC[<n>G`         | n = column           | Move to column n             |
| Save Cursor (DEC)    | `ESC 7`            | -                    | Save position and attributes |
| Restore Cursor (DEC) | `ESC 8`            | -                    | Restore saved position       |
| Save Cursor (SCO)    | `ESC[s`            | -                    | Alternative save             |
| Restore Cursor (SCO) | `ESC[u`            | -                    | Alternative restore          |

**Important Notes:**

- Coordinates are 1-indexed (top-left is 1,1)
- Moving beyond screen boundaries behavior is terminal-dependent
- DEC sequences (ESC 7/8) are more widely supported than SCO (ESC[s/u)

### Screen Clearing Commands

| Function                 | Sequence            | Effect                                    |
|--------------------------|---------------------|-------------------------------------------|
| Clear Screen from Cursor | `ESC[J` or `ESC[0J` | Clear from cursor to end of screen        |
| Clear Entire Screen      | `ESC[2J`            | Clear entire screen (doesn't move cursor) |
| Clear Screen to Cursor   | `ESC[1J`            | Clear from beginning to cursor            |
| Clear Line from Cursor   | `ESC[K` or `ESC[0K` | Clear from cursor to end of line          |
| Clear Entire Line        | `ESC[2K`            | Clear entire line                         |
| Clear Line to Cursor     | `ESC[1K`            | Clear from start of line to cursor        |

**Critical Pattern:**

```scala
// Clear screen and home cursor
print("\033[2J\033[H")

// Clear line and return to start
print("\r\033[K")
```

### Cursor Visibility

| Function                 | Sequence     | Effect                   |
|--------------------------|--------------|--------------------------|
| Hide Cursor              | `ESC[?25l`   | Make cursor invisible    |
| Show Cursor              | `ESC[?25h`   | Make cursor visible      |
| Save Cursor + Attributes | `ESC[?1049h` | Enter alternative buffer |
| Restore Cursor + Screen  | `ESC[?1049l` | Exit alternative buffer  |

### Text Styling (SGR - Select Graphic Rendition)

All SGR sequences follow the pattern: `ESC[<code>m`

Multiple codes can be combined: `ESC[1;31m` (bold + red)

#### Basic Styling

| Style         | Enable   | Disable   | Notes                                |
|---------------|----------|-----------|--------------------------------------|
| Reset All     | `ESC[0m` | -         | Reset to defaults                    |
| Bold          | `ESC[1m` | `ESC[22m` | Often renders as bright color        |
| Dim           | `ESC[2m` | `ESC[22m` | Lower intensity                      |
| Italic        | `ESC[3m` | `ESC[23m` | Not widely supported                 |
| Underline     | `ESC[4m` | `ESC[24m` | Single underline                     |
| Blink         | `ESC[5m` | `ESC[25m` | Rarely supported in modern terminals |
| Reverse       | `ESC[7m` | `ESC[27m` | Swap foreground/background           |
| Hidden        | `ESC[8m` | `ESC[28m` | Invisible text                       |
| Strikethrough | `ESC[9m` | `ESC[29m` | Limited support                      |

#### Standard 16 Colors

**Foreground:** 30-37, 90-97
**Background:** 40-47, 100-107

| Color   | Foreground | Background | Bright FG | Bright BG |
|---------|------------|------------|-----------|-----------|
| Black   | 30         | 40         | 90        | 100       |
| Red     | 31         | 41         | 91        | 101       |
| Green   | 32         | 42         | 92        | 102       |
| Yellow  | 33         | 43         | 93        | 103       |
| Blue    | 34         | 44         | 94        | 104       |
| Magenta | 35         | 45         | 95        | 105       |
| Cyan    | 36         | 46         | 96        | 106       |
| White   | 37         | 47         | 97        | 107       |

**Default Colors:**

- `ESC[39m` - Default foreground
- `ESC[49m` - Default background

#### 256-Color Mode

**Foreground:** `ESC[38;5;<n>m`
**Background:** `ESC[48;5;<n>m`

Color palette (0-255):

- **0-15:** Standard colors (same as 16-color mode)
- **16-231:** 6×6×6 RGB cube (16 + 36×r + 6×g + b where r,g,b ∈ [0,5])
- **232-255:** Grayscale from black to white (24 shades)

**RGB Cube Calculation:**

```scala
def color256(r: Int, g: Int, b: Int): Int = {
  require(r >= 0 && r <= 5 && g >= 0 && g <= 5 && b >= 0 && b <= 5)
  16 + 36 * r + 6 * g + b
}
```

#### True Color (24-bit RGB)

**Foreground:** `ESC[38;2;<r>;<g>;<b>m`
**Background:** `ESC[48;2;<r>;<g>;<b>m`

Where r, g, b ∈ [0, 255]

**Example:**

```scala
// Red foreground: RGB(255, 0, 0)
print("\u001B[38;2;255;0;0mRed Text\u001B[0m")

// Blue background: RGB(0, 0, 255)
print("\u001B[48;2;0;0;255mBlue Background\u001B[0m")
```

### Scrolling and Regions

| Function            | Sequence              | Parameters              | Description            |
|---------------------|-----------------------|-------------------------|------------------------|
| Set Scroll Region   | `ESC[<top>;<bottom>r` | top, bottom (1-indexed) | Define scrollable area |
| Reset Scroll Region | `ESC[r`               | -                       | Reset to full screen   |
| Scroll Up           | `ESC M`               | -                       | Scroll up one line     |
| Scroll Down         | `ESC D`               | -                       | Scroll down one line   |

**Critical for Fixed Status Bars:**

```scala
// Reserve bottom line as status bar
// For 24-line terminal:
print("\033[0;23r") // Lines 1-23 scroll, line 24 is fixed
```

### Alternative Screen Buffer

| Function           | Sequence     | Description                |
|--------------------|--------------|----------------------------|
| Enable Alt Buffer  | `ESC[?1049h` | Switch to alternate screen |
| Disable Alt Buffer | `ESC[?1049l` | Return to normal screen    |

Used by applications like `vim`, `less`, `htop` to preserve the original terminal content.

### Mouse Support

| Function           | Sequence     | Description             |
|--------------------|--------------|-------------------------|
| Enable Normal Mode | `ESC[?1000h` | Click events only       |
| Enable Button Mode | `ESC[?1002h` | Click + drag events     |
| Enable Any Event   | `ESC[?1003h` | All mouse events        |
| Disable Mouse      | `ESC[?1000l` | Turn off mouse tracking |

Mouse events arrive as: `ESC[M<button><x><y>` (button and coordinates are single bytes)

Modern terminals support SGR mouse mode: `ESC[?1006h`
Events: `ESC[<<button>;<x>;<y>M` (press) or `m` (release)

### Terminal Modes

| Function            | Sequence     | Description                    |
|---------------------|--------------|--------------------------------|
| Line Wrap On        | `ESC[?7h`    | Enable automatic line wrapping |
| Line Wrap Off       | `ESC[?7l`    | Disable wrapping               |
| Bracketed Paste On  | `ESC[?2004h` | Wrap pasted text in markers    |
| Bracketed Paste Off | `ESC[?2004l` | Normal paste behavior          |

Bracketed paste surrounds pasted text with `ESC[200~` and `ESC[201~`

### Cursor Shape

| Shape              | Sequence  | Description           |
|--------------------|-----------|-----------------------|
| Default            | `ESC[0 q` | Terminal default      |
| Blinking Block     | `ESC[1 q` | Blinking block cursor |
| Steady Block       | `ESC[2 q` | Steady block cursor   |
| Blinking Underline | `ESC[3 q` | Blinking underline    |
| Steady Underline   | `ESC[4 q` | Steady underline      |
| Blinking Bar       | `ESC[5 q` | Blinking vertical bar |
| Steady Bar         | `ESC[6 q` | Steady vertical bar   |

### Terminal Query Sequences

| Query             | Sequence | Response Format    |
|-------------------|----------|--------------------|
| Cursor Position   | `ESC[6n` | `ESC[<row>;<col>R` |
| Device Attributes | `ESC[c`  | Terminal-specific  |

**Reading Cursor Position:**

```scala
// Send query
print("\033[6n")
// Read response from stdin
// Parse: \033[<row>;<col>R
```

### Full Reset

| Function       | Sequence | Description                               |
|----------------|----------|-------------------------------------------|
| Reset Terminal | `ESC c`  | Full reset (RIS - Reset to Initial State) |

Clears screen, resets all attributes, moves cursor home.

---

## Core Terminal Manipulation Techniques

### 1. Carriage Return (`\r`) - Basic In-Place Update

The simplest technique for updating a single line without scrolling.

**Mechanism:** `\r` returns the cursor to the start of the current line without advancing to a new line.

**Example:**

```scala
def simpleProgress(percent: Int): Unit = {
  print(s"\rProgress: $percent%")
  System.out.flush()
}

// Usage
for (i <- 0 to 100) {
  simpleProgress(i)
  Thread.sleep(50)
}
println() // Move to next line when done
```

**Limitations:**

- Only works for single-line updates
- If new text is shorter than previous, remnants remain
- Solution: Clear to end of line with `ESC[K`

### 2. Carriage Return + Clear Line

**Improved Pattern:**

```scala
def betterProgress(percent: Int): Unit = {
  print(s"\r\033[KProgress: $percent%")
  System.out.flush()
}
```

The `\033[K` clears from cursor to end of line, removing any remnants.

### 3. Multi-Line Updates

For updating multiple lines (e.g., multiple progress bars):

```scala
def updateMultiLine(lines: List[String]): Unit = {
  // Move cursor to start of first line
  print(s"\033[${lines.length}A") // Up N lines

  lines.foreach { line =>
    print(s"\r\033[K$line\n") // Clear and write each line
  }

  // Move back to bottom
  print(s"\033[${lines.length}A")
  System.out.flush()
}
```

**Key Sequences:**

- `ESC[<n>A` - Move up n lines
- `ESC[<n>B` - Move down n lines

### 4. Save/Restore Cursor Position

For complex updates where cursor position must be preserved:

```scala
def updateAtPosition(row: Int, col: Int, text: String): Unit = {
  print("\0337") // Save cursor position
  print(s"\033[${row};${col}H") // Move to target
  print(text) // Write text
  print("\0338") // Restore cursor
  System.out.flush()
}
```

**Best Practice:** Use DEC sequences (`ESC 7` / `ESC 8`) over SCO (`ESC[s` / `ESC[u`) for better compatibility.

### 5. Buffered Rendering

To minimize flicker, build output in memory before writing:

```scala
def render(content: String): Unit = {
  val buffer = new StringBuilder

  buffer.append("\033[H") // Home cursor
  buffer.append(content)

  print(buffer.toString)
  System.out.flush()
}
```

---

## In-Place Update Strategies

### Progress Bars

**Basic Progress Bar:**

```scala
def progressBar(percent: Int, width: Int = 50): String = {
  val filled = (percent * width) / 100
  val empty = width - filled
  s"[${"=" * filled}${" " * empty}] $percent%"
}

def showProgress(percent: Int): Unit = {
  print(s"\r\033[K${progressBar(percent)}")
  System.out.flush()
}
```

**Fancy Progress Bar with Unicode:**

```scala
def fancyProgressBar(percent: Int, width: Int = 50): String = {
  val filled = (percent * width) / 100
  val empty = width - filled
  val bar = "█" * filled + "░" * empty
  s"[$bar] $percent%"
}
```

**Partial Block Characters:**

```scala
val blocks = Array(" ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█")

def preciseProgress(percent: Double, width: Int = 50): String = {
  val totalBlocks = percent * width / 100.0
  val fullBlocks = totalBlocks.toInt
  val partial = ((totalBlocks - fullBlocks) * 8).toInt

  val filled = "█" * fullBlocks
  val partialBlock = if (fullBlocks < width && partial > 0) blocks(partial) else ""
  val empty = " " * (width - fullBlocks - (if (partial > 0) 1 else 0))

  s"[$filled$partialBlock$empty] ${percent.formatted("%.1f")}%"
}
```

### Spinners

**Frame-Based Animation:**

```scala
object SpinnerFrames {
  val dots = Array("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
  val line = Array("-", "\\", "|", "/")
  val arrow = Array("←", "↖", "↑", "↗", "→", "↘", "↓", "↙")
  val arc = Array("◜", "◠", "◝", "◞", "◡", "◟")
  val circle = Array("◡", "⊙", "◠")
  val squareCorners = Array("◰", "◳", "◲", "◱")
  val circleQuarters = Array("◴", "◷", "◶", "◵")
  val bouncingBar = Array(
    "[    ]", "[=   ]", "[==  ]", "[=== ]",
    "[ ===]", "[  ==]", "[   =]", "[    ]"
  )
}

class Spinner(frames: Array[String], interval: Int = 80) {
  private var frameIndex = 0
  private var lastUpdate = System.currentTimeMillis()

  def nextFrame(): String = {
    val now = System.currentTimeMillis()
    if (now - lastUpdate >= interval) {
      frameIndex = (frameIndex + 1) % frames.length
      lastUpdate = now
    }
    frames(frameIndex)
  }
}
```

**Usage:**

```scala
val spinner = new Spinner(SpinnerFrames.dots)
print("\033[?25l") // Hide cursor

for (_ <- 0 until 100) {
  print(s"\r${spinner.nextFrame()} Loading...")
  System.out.flush()
  Thread.sleep(80)
}

print("\r\033[K✓ Done!\n")
print("\033[?25h") // Show cursor
```

### Multi-Line Spinners

**Ora-Style Multi-Line Updates:**

```scala
class MultiLineUpdater(lineCount: Int) {
  def update(lines: List[String]): Unit = {
    require(lines.length == lineCount, s"Expected $lineCount lines")

    // Clear all lines
    for (i <- 0 until lineCount) {
      print("\033[2K") // Clear line
      if (i < lineCount - 1) {
        print("\033[B") // Move down
      }
    }

    // Return to top
    print(s"\033[${lineCount - 1}A")
    print("\r")

    // Write new content
    lines.foreach { line =>
      print(s"$line\n")
    }

    // Return cursor to top
    print(s"\033[${lineCount}A")
    System.out.flush()
  }
}
```

---

## Scrolling Region Manipulation

### APT-Style Fixed Status Bar

The **critical technique** for fixed footer/header while allowing content to scroll.

**Concept:** Use `ESC[<top>;<bottom>r` to define which lines participate in scrolling. Lines outside this region remain
fixed.

**Implementation:**

```scala
object FixedStatusBar {
  def setup(terminalRows: Int): Unit = {
    print("\0337") // Save cursor
    print(s"\033[0;${terminalRows - 1}r") // Scroll region: 1 to N-1
    print("\0338") // Restore cursor
    print("\033[2J\033[H") // Clear screen, home
    System.out.flush()
  }

  def updateStatus(terminalRows: Int, message: String): Unit = {
    print("\0337") // Save cursor
    print(s"\033[${terminalRows};1H") // Jump to last line
    print("\033[2K") // Clear line
    print(s"\033[7m$message\033[0m") // Inverse video
    print("\0338") // Restore cursor
    System.out.flush()
  }

  def teardown(): Unit = {
    print("\033[r") // Reset scroll region to full screen
    System.out.flush()
  }
}
```

**Usage:**

```scala
val rows = 24

FixedStatusBar.setup(rows)

// Normal output scrolls in the region
println("Line 1")
println("Line 2")
// ... more lines ...

// Status bar stays fixed at bottom
FixedStatusBar.updateStatus(rows, " Status: Processing | Time: 12:34 ")

// More scrolling output
println("Line 50")
println("Line 51")

// Update status again
FixedStatusBar.updateStatus(rows, " Status: Done! | Items: 100 ")

// Cleanup
FixedStatusBar.teardown()
```

**Key Points:**

- Scrolling region is 1-indexed
- `\033[r` with no parameters resets to full screen
- Always restore scroll region on exit
- Content within the region scrolls normally
- Content outside the region is fixed

### Split Screen

Create multiple independent scrolling regions:

```scala
object SplitScreen {
  def setupTopBottom(splitRow: Int, terminalRows: Int): Unit = {
    // Top region scrolls independently
    print(s"\033[1;${splitRow}r")

    // Draw separator
    print(s"\033[${splitRow + 1};1H")
    print("─" * 80)

    // Bottom region needs manual scrolling
    // (Only one scrolling region can be active)
  }
}
```

**Limitation:** VT100 only supports one scrolling region at a time. Multiple regions require manual scrolling logic.

---

## Box Drawing and Layout Systems

### Unicode Box Drawing Characters

**Single-Line Borders:**

```scala
object BoxDrawing {
  // Corners
  val TOP_LEFT = "┌"
  val TOP_RIGHT = "┐"
  val BOTTOM_LEFT = "└"
  val BOTTOM_RIGHT = "┘"

  // Lines
  val HORIZONTAL = "─"
  val VERTICAL = "│"

  // Intersections
  val CROSS = "┼"
  val T_DOWN = "┬" // ┬
  val T_UP = "┴" // ┴
  val T_RIGHT = "├" // ├
  val T_LEFT = "┤" // ┤
}
```

**Double-Line Borders:**

```scala
object DoubleBoxDrawing {
  val TOP_LEFT = "╔"
  val TOP_RIGHT = "╗"
  val BOTTOM_LEFT = "╚"
  val BOTTOM_RIGHT = "╝"
  val HORIZONTAL = "═"
  val VERTICAL = "║"
  val CROSS = "╬"
}
```

**Rounded Corners:**

```scala
object RoundedBoxDrawing {
  val TOP_LEFT = "╭"
  val TOP_RIGHT = "╮"
  val BOTTOM_LEFT = "╰"
  val BOTTOM_RIGHT = "╯"
  val HORIZONTAL = "─"
  val VERTICAL = "│"
}
```

**Heavy Lines:**

```scala
object HeavyBoxDrawing {
  val TOP_LEFT = "┏"
  val TOP_RIGHT = "┓"
  val BOTTOM_LEFT = "┗"
  val BOTTOM_RIGHT = "┛"
  val HORIZONTAL = "━"
  val VERTICAL = "┃"
}
```

### Drawing Boxes

```scala
case class Box(
                x: Int, // Column (1-indexed)
                y: Int, // Row (1-indexed)
                width: Int, // Total width including borders
                height: Int, // Total height including borders
                title: String = ""
              )

def drawBox(box: Box, style: BoxStyle = BoxStyle.Single): Unit = {
  import BoxDrawing._

  val buffer = new StringBuilder

  // Top border
  buffer.append(s"\033[${box.y};${box.x}H")
  buffer.append(TOP_LEFT)

  if (box.title.nonEmpty) {
    val titleLen = box.title.length + 2 // " title "
    val leftPad = (box.width - 2 - titleLen) / 2
    val rightPad = box.width - 2 - titleLen - leftPad

    buffer.append(HORIZONTAL * leftPad)
    buffer.append(s" ${box.title} ")
    buffer.append(HORIZONTAL * rightPad)
  } else {
    buffer.append(HORIZONTAL * (box.width - 2))
  }

  buffer.append(TOP_RIGHT)

  // Sides
  for (row <- 1 until box.height - 1) {
    buffer.append(s"\033[${box.y + row};${box.x}H$VERTICAL")
    buffer.append(s"\033[${box.y + row};${box.x + box.width - 1}H$VERTICAL")
  }

  // Bottom border
  buffer.append(s"\033[${box.y + box.height - 1};${box.x}H")
  buffer.append(BOTTOM_LEFT)
  buffer.append(HORIZONTAL * (box.width - 2))
  buffer.append(BOTTOM_RIGHT)

  print(buffer.toString)
  System.out.flush()
}
```

### Layout Managers

**Vertical Split:**

```scala
def verticalSplit(
                   x: Int, y: Int,
                   totalWidth: Int, totalHeight: Int,
                   splitPercentage: Int
                 ): (Box, Box) = {
  val leftWidth = (totalWidth * splitPercentage) / 100
  val rightWidth = totalWidth - leftWidth

  val left = Box(x, y, leftWidth, totalHeight)
  val right = Box(x + leftWidth, y, rightWidth, totalHeight)

  (left, right)
}
```

**Horizontal Split:**

```scala
def horizontalSplit(
                     x: Int, y: Int,
                     totalWidth: Int, totalHeight: Int,
                     splitPercentage: Int
                   ): (Box, Box) = {
  val topHeight = (totalHeight * splitPercentage) / 100
  val bottomHeight = totalHeight - topHeight

  val top = Box(x, y, totalWidth, topHeight)
  val bottom = Box(x, y + topHeight, totalWidth, bottomHeight)

  (top, bottom)
}
```

**Grid Layout:**

```scala
def gridLayout(
                x: Int, y: Int,
                totalWidth: Int, totalHeight: Int,
                rows: Int, cols: Int
              ): Array[Array[Box]] = {
  val cellWidth = totalWidth / cols
  val cellHeight = totalHeight / rows

  Array.tabulate(rows, cols) { (r, c) =>
    Box(
      x = x + c * cellWidth,
      y = y + r * cellHeight,
      width = cellWidth,
      height = cellHeight
    )
  }
}
```

### Text Rendering in Boxes

```scala
def renderTextInBox(
                     box: Box,
                     text: String,
                     padding: Int = 1
                   ): Unit = {
  val innerWidth = box.width - 2 - 2 * padding
  val innerHeight = box.height - 2 - 2 * padding

  // Word wrap
  val lines = wordWrap(text, innerWidth)
    .take(innerHeight) // Don't overflow box

  // Render each line
  lines.zipWithIndex.foreach { case (line, idx) =>
    val row = box.y + 1 + padding + idx
    val col = box.x + 1 + padding
    print(s"\033[${row};${col}H$line")
  }

  System.out.flush()
}

def wordWrap(text: String, width: Int): List[String] = {
  val words = text.split(" ")
  var lines = List.empty[String]
  var currentLine = ""

  words.foreach { word =>
    if ((currentLine + " " + word).trim.length <= width) {
      currentLine = (currentLine + " " + word).trim
    } else {
      if (currentLine.nonEmpty) lines = lines :+ currentLine
      currentLine = word
    }
  }

  if (currentLine.nonEmpty) lines = lines :+ currentLine
  lines
}
```

### Block Elements

For more granular graphics:

```scala
object BlockElements {
  // Quadrants
  val UPPER_HALF = "▀"
  val LOWER_HALF = "▄"
  val LEFT_HALF = "▌"
  val RIGHT_HALF = "▐"

  // Full block
  val FULL = "█"

  // Shades
  val LIGHT_SHADE = "░"
  val MEDIUM_SHADE = "▒"
  val DARK_SHADE = "▓"

  // Eighths (for progress bars)
  val EIGHTHS = Array(" ", "▏", "▎", "▍", "▌", "▋", "▊", "▉", "█")
}
```

---

## Terminal Capability Detection

### Environment Variables

```scala
object TerminalInfo {
  def termType: Option[String] = sys.env.get("TERM")

  def colorTerm: Option[String] = sys.env.get("COLORTERM")

  def isInTmux: Boolean = sys.env.contains("TMUX")

  def isInScreen: Boolean = termType.exists(_.startsWith("screen"))

  def isSSH: Boolean = sys.env.contains("SSH_CONNECTION")

  def isTTY: Boolean = System.console() != null

  def supportsColor: Boolean = {
    termType match {
      case Some("dumb") => false
      case None => false
      case _ => isTTY
    }
  }

  def supportsTrueColor: Boolean = {
    colorTerm.exists(ct => ct == "truecolor" || ct == "24bit")
  }
}
```

### Using tput

`tput` queries the terminfo database for terminal capabilities:

```scala
import scala.sys.process._
import scala.util.Try

object TputCapabilities {
  def getCapability(cap: String): Option[String] = {
    Try(s"tput $cap".!!.trim).toOption
  }

  def getIntCapability(cap: String): Option[Int] = {
    getCapability(cap).flatMap(s => Try(s.toInt).toOption)
  }

  def colors: Int = getIntCapability("colors").getOrElse(0)

  def columns: Int = getIntCapability("cols").getOrElse(80)

  def lines: Int = getIntCapability("lines").getOrElse(24)

  def hasCapability(cap: String): Boolean = {
    Try(s"tput $cap".! == 0).getOrElse(false)
  }

  def supportsItalic: Boolean = hasCapability("sitm")

  def supportsBold: Boolean = hasCapability("bold")
}
```

### Querying Terminal Size

```scala
object TerminalSize {
  // Method 1: Using tput
  def viaTput: Option[(Int, Int)] = {
    for {
      rows <- TputCapabilities.lines
      cols <- TputCapabilities.columns
    } yield (rows, cols)
  }

  // Method 2: Using stty
  def viaStty: Option[(Int, Int)] = {
    Try {
      val output = "stty size".!!.trim
      val Array(rows, cols) = output.split(" ").map(_.toInt)
      (rows, cols)
    }.toOption
  }

  // Method 3: Query cursor position (last resort)
  def viaCursorQuery: Option[(Int, Int)] = {
    // Move to 999,999 (way beyond any terminal)
    print("\0337") // Save cursor
    print("\033[999;999H") // Move to large position
    print("\033[6n") // Query position
    System.out.flush()

    // Read response: ESC[<rows>;<cols>R
    // This requires reading from stdin, which is complex in Scala
    // Usually better to use tput or stty

    print("\0338") // Restore cursor
    None // Simplified - actual implementation needs stdin reading
  }

  def get: (Int, Int) = {
    viaTput
      .orElse(viaStty)
      .getOrElse((24, 80)) // Fallback to standard size
  }
}
```

### Capability Detection Matrix

```scala
case class TerminalCapabilities(
                                 colors: Int,
                                 width: Int,
                                 height: Int,
                                 isTTY: Boolean,
                                 supportsUnicode: Boolean,
                                 supportsTrueColor: Boolean,
                                 supportsMouseTracking: Boolean,
                                 supportsAlternateBuffer: Boolean
                               )

object TerminalCapabilities {
  def detect(): TerminalCapabilities = {
    val isTTY = System.console() != null
    val colors = if (isTTY) TputCapabilities.colors else 0
    val (height, width) = if (isTTY) TerminalSize.get else (24, 80)

    // Unicode support heuristic
    val supportsUnicode = {
      val lang = sys.env.getOrElse("LANG", "")
      lang.toLowerCase.contains("utf")
    }

    val supportsTrueColor = TerminalInfo.supportsTrueColor

    // Most modern terminals support these
    val supportsMouseTracking = isTTY && colors >= 8
    val supportsAlternateBuffer = isTTY

    TerminalCapabilities(
      colors = colors,
      width = width,
      height = height,
      isTTY = isTTY,
      supportsUnicode = supportsUnicode,
      supportsTrueColor = supportsTrueColor,
      supportsMouseTracking = supportsMouseTracking,
      supportsAlternateBuffer = supportsAlternateBuffer
    )
  }
}
```

### Fail Fast - No Degradation

ws-console does not degrade gracefully. It validates the terminal at startup and fails if requirements are not met:

```scala
object TerminalValidator {
  def validate(): Unit = {
    val errors = List.newBuilder[String]

    if (!TerminalInfo.isTTY) {
      errors += "Interactive TTY required (not a pipe or redirected I/O)"
    }
    if (!TerminalInfo.supportsColor) {
      errors += "256+ color support required"
    }
    if (!TerminalInfo.supportsUnicode) {
      errors += "Unicode support required (set LANG to UTF-8 locale)"
    }

    val issues = errors.result()
    if (issues.nonEmpty) {
      throw new UnsupportedTerminalException(
        s"Terminal requirements not met:\n${issues.mkString("\n- ", "\n- ", "")}\n\n" +
          "Supported terminals: iTerm2, Terminal.app, Windows Terminal, GNOME Terminal, etc."
      )
    }
  }
}

// After validation passes, use full ANSI/Unicode features freely
class Renderer {
  def render(text: String): String = s"\033[1;32m✓\033[0m $text"
}
```

---

## Existing Library Analysis

### JVM/Scala Libraries

#### 1. JLine 3

**Repository:** https://github.com/jline/jline3
**Language:** Java
**License:** BSD

**Features:**

- Line editing with history
- Completion
- Syntax highlighting
- Terminal abstraction (Windows, Unix, SSH)
- Comprehensive ANSI support
- Terminal size detection
- Raw mode support

**Architecture:**

```java
Terminal terminal = TerminalBuilder.builder()
        .system(true)
        .build();

int width = terminal.getWidth();
int height = terminal.getHeight();

terminal.

writer().

println("Hello");
terminal.

flush();
```

**Pros:**

- Mature, battle-tested
- Excellent Windows support
- Rich feature set
- Used by major projects (Maven, Groovy REPL, etc.)

**Cons:**

- Heavy dependency
- Java-centric API (not idiomatic Scala)
- Complex for simple use cases

#### 2. tui-scala

**Repository:** https://github.com/oyvindberg/tui-scala
**Language:** Scala 3
**License:** MIT

**Features:**

- Port of Rust's tui-rs
- Widget-based (BarChart, Gauge, Table, List, etc.)
- Layout system (constraints, splits)
- Crossterm backend (JNI to Rust)
- GraalVM native image support

**Architecture:**

```scala
val terminal = Terminal(CrosstermBackend.create())

terminal.draw { frame =>
  val gauge = Gauge.default()
    .percent(65)
    .label("Progress")

  frame.renderWidget(gauge, frame.size)
}
```

**Pros:**

- Modern, idiomatic Scala 3
- Rich widget library
- Excellent for dashboards
- Cross-platform (via crossterm)

**Cons:**

- Requires native library (crossterm via JNI)
- Opinionated widget system
- Limited low-level control

#### 3. Scurses

**Repository:** https://github.com/Tenchi2xh/Scurses
**Language:** Scala
**License:** MIT

**Features:**

- Low-level terminal drawing
- Event handling (keyboard, mouse)
- Color support
- Onions framework for widgets

**Architecture:**

```scala
Scurses { screen =>
  screen.put(10, 5, "Hello", Colors.RED)
  screen.refresh()

  screen.keypress() match {
    case 'q' => // quit
    case key => // handle
  }
}
```

**Pros:**

- Lightweight
- Direct control
- No external dependencies

**Cons:**

- Less actively maintained
- Limited documentation
- Fewer widgets than alternatives

#### 4. scala.io.AnsiColor

**Built into Scala standard library**

**Features:**

- Basic color constants
- Simple to use

**Example:**

```scala
import scala.io.AnsiColor._

println(s"${RED}Error${RESET}: File not found")
println(s"${GREEN}${BOLD}Success!${RESET}")
```

**Pros:**

- Zero dependencies
- Always available
- Simple and lightweight

**Cons:**

- Very limited (colors only, no cursor control)
- No capability detection
- No layout support

### Node.js Libraries (Reference Implementations)

#### 1. Ora

**Repository:** https://github.com/sindresorhus/ora
**Language:** JavaScript/TypeScript
**Weekly Downloads:** ~24 million

**Key Techniques:**

- Frame-based spinner animation
- Stream-based cursor manipulation (`stream.cursorTo()`, `stream.clearLine()`)
- Multi-line clearing (tracks `linesToClear`)
- Interval-based rendering with frame rate limiting
- TTY detection for graceful degradation
- Cursor hiding during animation

**Core Algorithm:**

```javascript
// Render loop
setInterval(() => {
    this.clear();        // Clear previous lines
    this.render();       // Draw new frame
}, this.interval);

// Clear
clear()
{
    stream.cursorTo(0);
    for (let i = 0; i < linesToClear; i++) {
        if (i > 0) stream.moveCursor(0, -1);
        stream.clearLine(1);
    }
}

// Frame selection
frame()
{
    const now = Date.now();
    if (now - lastFrameTime >= interval) {
        frameIndex = (frameIndex + 1) % frames.length;
    }
    return frames[frameIndex];
}
```

**Lessons for Scala Implementation:**

- Track how many lines were written
- Clear upward for multi-line updates
- Rate-limit frame updates regardless of render calls
- Always flush output
- Provide semantic completion methods (succeed, fail, warn)

#### 2. Blessed

**Repository:** https://github.com/chjj/blessed
**Note:** No longer actively maintained, but influential

**Features:**

- Full ncurses reimplementation in JS
- Parses terminfo/termcap
- High-level widget API
- Event system
- Layout management

**Architectural Insights:**

- Screen buffer abstraction (draw to buffer, then render diff)
- Element/widget tree
- Event bubbling
- Focus management

#### 3. Ink (React for CLIs)

**Repository:** https://github.com/vadimdemedes/ink
**Approach:** Use React's component model for terminal UIs

**Example:**

```javascript
const Counter = () => {
    const [count, setCount] = useState(0);

    return (
        <Box>
            <Text color="green">Count: {count}</Text>
        </Box>
    );
};
```

**Architectural Pattern:**

- Component-based
- Virtual DOM diffing for terminal
- Reconciliation to minimize redraws

**Relevance to Scala:**

- Could use ZIO's functional reactive patterns
- Immutable state with efficient diffing

### Rust Libraries (Reference)

#### tui-rs (now ratatui)

**Repository:** https://github.com/ratatui-org/ratatui
**Language:** Rust

**Architecture:**

- Backend abstraction (Crossterm, Termion, Termwiz)
- Immediate mode rendering (redraw everything each frame)
- Layout constraints system
- Widget trait

**Key Pattern - Immediate Mode:**

```rust
loop {
    terminal.draw(|frame| {
        // Rebuild entire UI each frame
        let gauge = Gauge::default().percent(progress);
        frame.render_widget(gauge, area);
    })?;
}
```

**Advantages:**

- Simple mental model (no state sync)
- Efficient with diffing (only changed cells sent to terminal)
- Easy to reason about

**Ported to Scala:** This is what tui-scala implements

---

## Implementation Patterns

### 1. Output Stream Abstraction

```scala
trait OutputStream {
  def write(s: String): Unit

  def flush(): Unit
}

class StdErrOutputStream extends OutputStream {
  def write(s: String): Unit = System.err.print(s)

  def flush(): Unit = System.err.flush()
}

class BufferedOutputStream extends OutputStream {
  private val buffer = new StringBuilder

  def write(s: String): Unit = buffer.append(s)

  def flush(): Unit = {
    System.err.print(buffer.toString)
    System.err.flush()
    buffer.clear()
  }
}
```

### 2. ANSI Code Builder

```scala
class AnsiBuilder {
  private val buffer = new StringBuilder

  def moveTo(row: Int, col: Int): this.type = {
    buffer.append(s"\033[${row};${col}H")
    this
  }

  def moveUp(n: Int): this.type = {
    buffer.append(s"\033[${n}A")
    this
  }

  def clearLine(): this.type = {
    buffer.append("\033[2K")
    this
  }

  def saveCursor(): this.type = {
    buffer.append("\0337")
    this
  }

  def restoreCursor(): this.type = {
    buffer.append("\0338")
    this
  }

  def hideCursor(): this.type = {
    buffer.append("\033[?25l")
    this
  }

  def showCursor(): this.type = {
    buffer.append("\033[?25h")
    this
  }

  def text(s: String): this.type = {
    buffer.append(s)
    this
  }

  def fg(color: Color): this.type = {
    color match {
      case Color.Rgb(r, g, b) =>
        buffer.append(s"\033[38;2;${r};${g};${b}m")
      case Color.Indexed(n) =>
        buffer.append(s"\033[38;5;${n}m")
      case Color.Standard(n) =>
        buffer.append(s"\033[${n}m")
    }
    this
  }

  def reset(): this.type = {
    buffer.append("\033[0m")
    this
  }

  def build(): String = buffer.toString
}

// Usage
val output = new AnsiBuilder()
  .saveCursor()
  .moveTo(10, 5)
  .fg(Color.Rgb(255, 0, 0))
  .text("Hello")
  .reset()
  .restoreCursor()
  .build()
```

### 3. Screen Buffer Pattern

```scala
class Cell(var char: Char, var fg: Color, var bg: Color, var attrs: Set[Attribute])

class ScreenBuffer(width: Int, height: Int) {
  private val buffer = Array.fill(height, width)(
    new Cell(' ', Color.Default, Color.Default, Set.empty)
  )

  def set(x: Int, y: Int, char: Char, fg: Color = Color.Default): Unit = {
    if (x >= 0 && x < width && y >= 0 && y < height) {
      buffer(y)(x).char = char
      buffer(y)(x).fg = fg
    }
  }

  def get(x: Int, y: Int): Option[Cell] = {
    if (x >= 0 && x < width && y >= 0 && y < height) {
      Some(buffer(y)(x))
    } else {
      None
    }
  }

  def diff(other: ScreenBuffer): List[(Int, Int, Cell)] = {
    val changes = List.newBuilder[(Int, Int, Cell)]

    for {
      y <- 0 until height
      x <- 0 until width
      if !cellsEqual(buffer(y)(x), other.buffer(y)(x))
    } {
      changes += ((x, y, buffer(y)(x)))
    }

    changes.result()
  }

  private def cellsEqual(a: Cell, b: Cell): Boolean = {
    a.char == b.char && a.fg == b.fg && a.bg == b.bg && a.attrs == b.attrs
  }

  def clear(): Unit = {
    for {
      y <- 0 until height
      x <- 0 until width
    } {
      buffer(y)(x) = new Cell(' ', Color.Default, Color.Default, Set.empty)
    }
  }
}
```

### 4. Immediate Mode Rendering

```scala
trait Widget {
  def render(area: Rect, buffer: ScreenBuffer): Unit
}

class Gauge(percent: Int, label: String) extends Widget {
  def render(area: Rect, buffer: ScreenBuffer): Unit = {
    val barWidth = area.width - 2
    val filled = (percent * barWidth) / 100

    // Draw border
    for (x <- area.x until area.x + area.width) {
      buffer.set(x, area.y, '─')
      buffer.set(x, area.y + area.height - 1, '─')
    }

    // Draw bar
    for (x <- 0 until filled) {
      buffer.set(area.x + 1 + x, area.y + 1, '█', Color.Green)
    }

    // Draw label
    val labelX = area.x + (area.width - label.length) / 2
    label.zipWithIndex.foreach { case (ch, i) =>
      buffer.set(labelX + i, area.y + 1, ch)
    }
  }
}

class Terminal(backend: Backend) {
  private var currentBuffer = new ScreenBuffer(backend.width, backend.height)
  private var previousBuffer = new ScreenBuffer(backend.width, backend.height)

  def draw(f: Frame => Unit): Unit = {
    val frame = new Frame(currentBuffer, backend.width, backend.height)
    f(frame)

    // Diff and render only changes
    val changes = currentBuffer.diff(previousBuffer)
    backend.renderChanges(changes)

    // Swap buffers
    val temp = previousBuffer
    previousBuffer = currentBuffer
    currentBuffer = temp
    currentBuffer.clear()
  }
}
```

### 5. Resource Management (ZIO)

```scala
import zio._

trait Terminal {
  def enterRawMode(): UIO[Unit]

  def exitRawMode(): UIO[Unit]

  def clearScreen(): UIO[Unit]

  def hideCursor(): UIO[Unit]

  def showCursor(): UIO[Unit]

  def size: UIO[(Int, Int)]
}

object Terminal {
  def withRawMode[R, E, A](effect: ZIO[R, E, A]): ZIO[R with Terminal, E, A] = {
    ZIO.acquireReleaseWith(
      acquire = ZIO.serviceWithZIO[Terminal](_.enterRawMode())
    )(
      release = _ => ZIO.serviceWithZIO[Terminal](_.exitRawMode())
    )(
      use = _ => effect
    )
  }

  def withHiddenCursor[R, E, A](effect: ZIO[R, E, A]): ZIO[R with Terminal, E, A] = {
    ZIO.acquireReleaseWith(
      acquire = ZIO.serviceWithZIO[Terminal](_.hideCursor())
    )(
      release = _ => ZIO.serviceWithZIO[Terminal](_.showCursor())
    )(
      use = _ => effect
    )
  }

  def withAlternateBuffer[R, E, A](effect: ZIO[R, E, A]): ZIO[R with Terminal, E, A] = {
    ZIO.acquireReleaseWith(
      acquire = ZIO.succeed(print("\033[?1049h"))
    )(
      release = _ => ZIO.succeed(print("\033[?1049l"))
    )(
      use = _ => effect
    )
  }
}
```

---

## Code Examples

### Example 1: Production Spinner

```scala
package terminal

import java.util.{Timer, TimerTask}
import scala.concurrent.duration._

case class SpinnerConfig(
                          frames: Array[String],
                          interval: FiniteDuration
                        )

object Spinners {
  val dots = SpinnerConfig(
    frames = Array("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"),
    interval = 80.millis
  )

  val line = SpinnerConfig(
    frames = Array("-", "\\", "|", "/"),
    interval = 130.millis
  )

  val dots2 = SpinnerConfig(
    frames = Array("⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"),
    interval = 80.millis
  )

  val arc = SpinnerConfig(
    frames = Array("◜", "◠", "◝", "◞", "◡", "◟"),
    interval = 100.millis
  )
}

class Spinner(
               initialText: String,
               config: SpinnerConfig = Spinners.dots,
               stream: java.io.PrintStream = System.err
             ) {
  private var _text = initialText
  private var frameIndex = 0
  private var linesToClear = 0
  private var timer: Option[Timer] = None
  private val isTTY = System.console() != null

  def text: String = _text

  def text_=(newText: String): Unit = _text = newText

  def start(): this.type = {
    if (!isTTY) {
      stream.println(_text)
      return this
    }

    stream.print("\033[?25l") // Hide cursor

    val task = new TimerTask {
      def run(): Unit = render()
    }

    val t = new Timer(true)
    t.scheduleAtFixedRate(task, 0, config.interval.toMillis)
    timer = Some(t)

    this
  }

  def stop(): this.type = {
    timer.foreach(_.cancel())
    timer = None

    if (isTTY) {
      clear()
      stream.print("\033[?25h") // Show cursor
      stream.flush()
    }

    this
  }

  def succeed(finalText: String = ""): this.type = {
    stop()
    val msg = if (finalText.nonEmpty) finalText else _text
    stream.println(s"\033[32m✔\033[0m $msg")
    this
  }

  def fail(finalText: String = ""): this.type = {
    stop()
    val msg = if (finalText.nonEmpty) finalText else _text
    stream.println(s"\033[31m✖\033[0m $msg")
    this
  }

  def warn(finalText: String = ""): this.type = {
    stop()
    val msg = if (finalText.nonEmpty) finalText else _text
    stream.println(s"\033[33m⚠\033[0m $msg")
    this
  }

  private def render(): Unit = {
    if (!isTTY) return

    clear()

    val frame = config.frames(frameIndex)
    frameIndex = (frameIndex + 1) % config.frames.length

    val output = s"$frame $_text"
    stream.print(output)
    stream.flush()

    linesToClear = output.count(_ == '\n') + 1
  }

  private def clear(): Unit = {
    stream.print("\r\033[K")

    if (linesToClear > 1) {
      for (_ <- 1 until linesToClear) {
        stream.print("\033[A\033[K")
      }
    }

    linesToClear = 0
  }
}

// Usage
object SpinnerExample {
  def main(args: Array[String]): Unit = {
    val spinner = new Spinner("Loading data", Spinners.dots)
    spinner.start()

    Thread.sleep(2000)
    spinner.text = "Processing data"

    Thread.sleep(2000)
    spinner.succeed("Done!")
  }
}
```

### Example 2: Multi-Progress Bars

```scala
package terminal

case class TaskProgress(
                         id: String,
                         label: String,
                         var current: Int,
                         total: Int
                       )

class MultiProgress(tasks: List[TaskProgress]) {
  private val isTTY = System.console() != null

  def start(): this.type = {
    if (!isTTY) return this

    // Reserve space
    tasks.foreach(_ => println())

    // Move cursor back up
    print(s"\033[${tasks.length}A")
    System.out.flush()

    this
  }

  def update(taskId: String, current: Int): Unit = {
    tasks.find(_.id == taskId).foreach { task =>
      task.current = current
      render()
    }
  }

  def render(): Unit = {
    if (!isTTY) return

    // Save cursor, move to start of progress area
    print("\0337")

    tasks.foreach { task =>
      val percent = (task.current * 100) / task.total
      val barWidth = 30
      val filled = (task.current * barWidth) / task.total
      val bar = "█" * filled + "░" * (barWidth - filled)

      print(s"\r\033[K${task.label.padTo(20, ' ')} [$bar] $percent%\n")
    }

    // Restore cursor
    print("\0338")
    System.out.flush()
  }

  def finish(): Unit = {
    if (!isTTY) return

    // Move past the progress area
    print(s"\033[${tasks.length}B\r")
    System.out.flush()
  }
}

// Usage
object MultiProgressExample {
  def main(args: Array[String]): Unit = {
    val tasks = List(
      TaskProgress("download", "Downloading", 0, 100),
      TaskProgress("process", "Processing", 0, 200),
      TaskProgress("upload", "Uploading", 0, 50)
    )

    val progress = new MultiProgress(tasks).start()

    // Simulate work
    for (i <- 0 to 100) {
      progress.update("download", i)
      progress.update("process", i * 2)
      progress.update("upload", i / 2)
      Thread.sleep(50)
    }

    progress.finish()
  }
}
```

### Example 3: Fixed Status Bar

```scala
package terminal

class StatusBar(totalRows: Int) {
  private val statusRow = totalRows

  def setup(): Unit = {
    print("\0337") // Save cursor
    print(s"\033[0;${totalRows - 1}r") // Scroll region
    print("\0338") // Restore cursor
    print("\033[2J\033[H") // Clear and home
    System.out.flush()
  }

  def update(message: String): Unit = {
    print("\0337") // Save cursor
    print(s"\033[${statusRow};1H") // Jump to status line
    print("\033[2K") // Clear line

    // Inverse video for status bar
    print(s"\033[7m $message\033[0m")

    print("\0338") // Restore cursor
    System.out.flush()
  }

  def teardown(): Unit = {
    print("\033[r") // Reset scroll region
    print(s"\033[${statusRow};1H\n") // Move past status
    System.out.flush()
  }
}

// Usage
object StatusBarExample {
  def main(args: Array[String]): Unit = {
    val (rows, _) = TerminalSize.get
    val statusBar = new StatusBar(rows)

    statusBar.setup()

    // Scrolling content
    for (i <- 1 to 50) {
      println(s"Line $i")
      statusBar.update(s"Processing line $i of 50")
      Thread.sleep(100)
    }

    statusBar.teardown()
  }
}
```

### Example 4: Box Layout

```scala
package terminal

import BoxDrawing._

case class Rect(x: Int, y: Int, width: Int, height: Int)

class Layout {
  def drawBox(rect: Rect, title: String = ""): Unit = {
    val buffer = new StringBuilder

    // Top border
    buffer.append(s"\033[${rect.y};${rect.x}H")
    buffer.append(TOP_LEFT)

    if (title.nonEmpty) {
      val padding = (rect.width - title.length - 4) / 2
      buffer.append(HORIZONTAL * padding)
      buffer.append(s" $title ")
      buffer.append(HORIZONTAL * (rect.width - 2 - padding - title.length - 2))
    } else {
      buffer.append(HORIZONTAL * (rect.width - 2))
    }

    buffer.append(TOP_RIGHT)

    // Sides
    for (row <- 1 until rect.height - 1) {
      buffer.append(s"\033[${rect.y + row};${rect.x}H$VERTICAL")
      buffer.append(s"\033[${rect.y + row};${rect.x + rect.width - 1}H$VERTICAL")
    }

    // Bottom
    buffer.append(s"\033[${rect.y + rect.height - 1};${rect.x}H")
    buffer.append(BOTTOM_LEFT)
    buffer.append(HORIZONTAL * (rect.width - 2))
    buffer.append(BOTTOM_RIGHT)

    print(buffer.toString)
    System.out.flush()
  }

  def horizontalSplit(rect: Rect, percent: Int): (Rect, Rect) = {
    val topHeight = (rect.height * percent) / 100
    val bottomHeight = rect.height - topHeight

    (
      Rect(rect.x, rect.y, rect.width, topHeight),
      Rect(rect.x, rect.y + topHeight, rect.width, bottomHeight)
    )
  }

  def verticalSplit(rect: Rect, percent: Int): (Rect, Rect) = {
    val leftWidth = (rect.width * percent) / 100
    val rightWidth = rect.width - leftWidth

    (
      Rect(rect.x, rect.y, leftWidth, rect.height),
      Rect(rect.x + leftWidth, rect.y, rightWidth, rect.height)
    )
  }
}

// Usage
object LayoutExample {
  def main(args: Array[String]): Unit = {
    print("\033[2J\033[H") // Clear screen

    val layout = new Layout
    val (rows, cols) = TerminalSize.get
    val screen = Rect(1, 1, cols, rows)

    val (top, bottom) = layout.horizontalSplit(screen, 30)
    val (left, right) = layout.verticalSplit(bottom, 50)

    layout.drawBox(top, "Header")
    layout.drawBox(left, "Left Panel")
    layout.drawBox(right, "Right Panel")

    // Position cursor in first panel
    print(s"\033[${top.y + 1};${top.x + 2}H")
    println("Welcome!")
  }
}
```

---

## Best Practices

### 1. Always Flush Output

Terminal output is often buffered. Always flush after writing:

```scala
print("\033[2J\033[H")
System.out.flush()
```

Or use `println()` which auto-flushes.

### 2. Restore Terminal State

Always restore terminal to a clean state, even on errors:

```scala
def withCleanup[A](f: => A): A = {
  try {
    print("\033[?1049h\033[?25l") // Alt buffer, hide cursor
    System.out.flush()
    f
  } finally {
    print("\033[?25h\033[?1049l") // Show cursor, normal buffer
    System.out.flush()
  }
}
```

ZIO version:

```scala
ZIO.acquireReleaseWith(
  acquire = ZIO.succeed(setupTerminal())
)(
  release = _ => ZIO.succeed(cleanupTerminal())
)(
  use = _ => program
)
```

### 3. Require TTY

ws-console requires an interactive TTY. Non-TTY environments are not supported:

```scala
val isTTY = System.console() != null

if (!isTTY) {
  throw new UnsupportedTerminalException(
    "ws-console requires an interactive terminal. " +
      "Pipes, redirected I/O, and non-TTY environments are not supported."
  )
}

// Proceed with full ANSI support
print("\033[1;32mSuccess!\033[0m")
```

### 4. Handle Window Resize

On Unix, `SIGWINCH` signals window resize:

```scala
import sun.misc.{Signal, SignalHandler}

Signal.handle(new Signal("WINCH"), new SignalHandler {
  def handle(sig: Signal): Unit = {
    val (rows, cols) = TerminalSize.get
    redrawUI(rows, cols)
  }
})
```

### 5. Require Unicode

ws-console requires Unicode support. Non-Unicode terminals are not supported:

```scala
val supportsUnicode = sys.env.get("LANG").exists(_.toLowerCase.contains("utf"))

if (!supportsUnicode) {
  throw new UnsupportedTerminalException(
    "ws-console requires Unicode support. Set LANG to a UTF-8 locale."
  )
}

// Use Unicode freely
val checkmark = "✓"
```

### 6. Minimize Redraws

Only redraw what changed:

```scala
// Bad
def update(): Unit = {
  clearScreen()
  drawEverything()
}

// Good
def update(changedRegion: Rect): Unit = {
  redrawRegion(changedRegion)
}
```

Use screen buffer diffing for automatic optimization.

### 7. Test on Multiple Terminals

Different terminals have varying support:

- **xterm** - Reference implementation
- **iTerm2** (macOS) - Excellent support
- **Windows Terminal** - Modern, good support
- **Terminal.app** (macOS) - Good support
- **Alacritty** - GPU-accelerated, excellent
- **tmux/screen** - Multiplexers, some limitations
- **Linux console** - Limited (no true color)

### 8. No Fallbacks - Fail Fast

ws-console does not provide fallback themes. Modern terminals are required:

```scala
// Validate terminal at startup
def validateTerminal(): Unit = {
  if (!TerminalInfo.isTTY) {
    throw new UnsupportedTerminalException("Interactive TTY required")
  }
  if (!TerminalInfo.supportsColor) {
    throw new UnsupportedTerminalException("256+ color support required")
  }
  if (!TerminalInfo.supportsUnicode) {
    throw new UnsupportedTerminalException("Unicode support required")
  }
}

// After validation, use full features without checks
object Theme {
  val success = "\033[32m✔\033[0m"
  val error = "\033[31m✖\033[0m"
  val warning = "\033[33m⚠\033[0m"
}
```

### 9. Performance: Batch Writes

```scala
// Bad
def slowRender(cells: List[Cell]): Unit = {
  cells.foreach { cell =>
    print(s"\033[${cell.y};${cell.x}H${cell.char}")
    System.out.flush() // Flush after each cell!
  }
}

// Good
def fastRender(cells: List[Cell]): Unit = {
  val buffer = new StringBuilder
  cells.foreach { cell =>
    buffer.append(s"\033[${cell.y};${cell.x}H${cell.char}")
  }
  print(buffer.toString)
  System.out.flush() // Single flush
}
```

### 10. Document Escape Sequences

Use constants with meaningful names:

```scala
object Ansi {
  val CLEAR_SCREEN = "\033[2J"
  val HOME = "\033[H"
  val HIDE_CURSOR = "\033[?25l"
  val SHOW_CURSOR = "\033[?25h"

  def moveTo(row: Int, col: Int) = s"\033[${row};${col}H"

  def moveUp(n: Int) = s"\033[${n}A"

  object Color {
    val RED = "\033[31m"
    val GREEN = "\033[32m"
    val RESET = "\033[0m"
  }
}

// Usage
print(Ansi.CLEAR_SCREEN + Ansi.HOME)
```

---

## Cross-Platform Considerations

> **Note:** ws-console targets modern interactive terminals only. Legacy terminals and non-interactive environments are
> explicitly out of scope.

### Supported Platforms

#### macOS (Full Support)

- **Terminal.app** - Default terminal, full ANSI/Unicode support
- **iTerm2** - Excellent, recommended

No special handling needed. macOS terminals have excellent support.

#### Linux (Full Support)

- **GNOME Terminal** - Excellent
- **Konsole** - Excellent
- **Alacritty** - GPU-accelerated, excellent
- **Kitty** - Excellent

All modern Linux terminal emulators work without special handling.

#### Windows 10+ (Full Support)

- **Windows Terminal** - Full support, recommended
- Requires Windows Terminal (not cmd.exe)

#### SSH Sessions (Full Support)

SSH sessions work if:

1. Client is a modern terminal (iTerm2, GNOME Terminal, etc.)
2. Connection is interactive (TTY allocated)

### NOT Supported (Out of Scope)

| Environment                  | Status                                   |
|------------------------------|------------------------------------------|
| cmd.exe                      | **Not supported** - use Windows Terminal |
| PowerShell (legacy)          | **Not supported** - use Windows Terminal |
| Linux raw console (TTY1-6)   | **Not supported**                        |
| Dumb terminals               | **Not supported**                        |
| Non-interactive environments | **Not supported**                        |
| Pipes/redirected I/O         | **Not supported**                        |

**There are no fallback code paths.** If the terminal is unsupported, the library fails with a clear error message.

### Terminal Multiplexers

**tmux and screen** work if the outer terminal is modern:

```scala
// Multiplexers are supported if configured correctly
// $COLORTERM=truecolor indicates proper configuration
val supportsTrueColor = sys.env.get("COLORTERM").exists(_ == "truecolor")
```

---

## Performance Considerations

### 1. Minimize Escape Sequences

```scala
// Slow: Many small writes
print("\033[1m")
print("\033[31m")
print("Text")
print("\033[0m")

// Fast: Combined
print("\033[1;31mText\033[0m")
```

### 2. Use Screen Buffering

Don't write directly to terminal for complex UIs:

```scala
// Build in memory
val buffer = new ScreenBuffer(width, height)
widgets.foreach(_.render(buffer))

// Diff and write only changes
val changes = buffer.diff(previousBuffer)
renderChanges(changes)
```

### 3. Limit Render Frequency

Cap at human perception (~60 FPS max, 30 FPS sufficient):

```scala
val minFrameTime = 33 // ~30 FPS

var lastRender = 0L

def render(): Unit = {
  val now = System.currentTimeMillis()
  if (now - lastRender < minFrameTime) return

  actualRender()
  lastRender = now
}
```

### 4. Batch Terminal Queries

Querying terminal (e.g., cursor position, size) can be slow:

```scala
// Cache terminal size
class CachedTerminalSize {
  private var cached: Option[(Long, (Int, Int))] = None
  private val cacheTime = 1000 // ms

  def get: (Int, Int) = {
    val now = System.currentTimeMillis()

    cached match {
      case Some((time, size)) if now - time < cacheTime =>
        size
      case _ =>
        val size = TerminalSize.query()
        cached = Some((now, size))
        size
    }
  }
}
```

### 5. Avoid Cursor Position Queries

Reading cursor position requires stdin, which blocks:

```scala
// Slow
def getCursorPosition(): (Int, Int) = {
  print("\033[6n")
  // Read from stdin... blocks!
}

// Fast: Track cursor position yourself
class CursorTracker {
  private var row = 1
  private var col = 1

  def moveTo(r: Int, c: Int): Unit = {
    row = r
    col = c
    print(s"\033[${row};${col}H")
  }

  def position: (Int, Int) = (row, col)
}
```

### 6. Optimize String Building

```scala
// Slow: String concatenation
var s = ""
for (i <- 0 until 1000) {
  s += s"\033[${i};0H*" // Creates new string each time!
}

// Fast: StringBuilder
val builder = new StringBuilder
for (i <- 0 until 1000) {
  builder.append(s"\033[${i};0H*")
}
val s = builder.toString
```

### 7. Benchmark Critical Paths

```scala
def benchmark[A](name: String)(f: => A): A = {
  val start = System.nanoTime()
  val result = f
  val end = System.nanoTime()
  println(s"$name: ${(end - start) / 1_000_000}ms")
  result
}

benchmark("Full render") {
  clearScreen()
  drawAllWidgets()
}
```

---

## References

### Official Standards

1. **ANSI X3.64 / ECMA-48**
   Original ANSI escape sequence standard

2. **VT100 User Guide**
   https://vt100.net/docs/vt100-ug/
   Reference implementation of terminal control

3. **XTerm Control Sequences**
   https://invisible-island.net/xterm/ctlseqs/ctlseqs.html
   Comprehensive modern terminal sequence documentation

### Terminal Databases

4. **terminfo(5) man page**
   Terminal capability database format

5. **ncurses**
   https://invisible-island.net/ncurses/
   Industry standard terminal handling library (C)

### Libraries Analyzed

6. **JLine 3**
   https://github.com/jline/jline3

7. **tui-scala**
   https://github.com/oyvindberg/tui-scala

8. **Scurses**
   https://github.com/Tenchi2xh/Scurses

9. **Ora (Node.js)**
   https://github.com/sindresorhus/ora

10. **Blessed (Node.js)**
    https://github.com/chjj/blessed

11. **Ratatui (Rust)**
    https://github.com/ratatui-org/ratatui

### Educational Resources

12. **ANSI Escape Codes Cheat Sheet**
    https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797

13. **How APT Does Its Fancy Progress Bar**
    https://mdk.fr/blog/how-apt-does-its-fancy-progress-bar.html

14. **Build Your Own Command Line with ANSI Escape Codes**
    https://www.lihaoyi.com/post/BuildyourownCommandLinewithANSIescapecodes.html

### Code Examples

15. **cli-spinners**
    https://github.com/sindresorhus/cli-spinners
    Collection of spinner frame definitions

16. **VT100 Examples**
    https://github.com/0x5c/VT100-Examples

---

## Appendix A: Complete ANSI Reference Table

| Category              | Sequence                | Description          |
|-----------------------|-------------------------|----------------------|
| **Cursor Movement**   |                         |                      |
|                       | `ESC[H`                 | Home (1,1)           |
|                       | `ESC[<r>;<c>H`          | Position (row, col)  |
|                       | `ESC[<n>A`              | Up n lines           |
|                       | `ESC[<n>B`              | Down n lines         |
|                       | `ESC[<n>C`              | Forward n cols       |
|                       | `ESC[<n>D`              | Backward n cols      |
|                       | `ESC[<n>E`              | Next line, col 1     |
|                       | `ESC[<n>F`              | Previous line, col 1 |
|                       | `ESC[<n>G`              | Column n             |
|                       | `ESC 7`                 | Save (DEC)           |
|                       | `ESC 8`                 | Restore (DEC)        |
|                       | `ESC[s`                 | Save (SCO)           |
|                       | `ESC[u`                 | Restore (SCO)        |
| **Cursor Visibility** |                         |                      |
|                       | `ESC[?25l`              | Hide                 |
|                       | `ESC[?25h`              | Show                 |
| **Screen Clear**      |                         |                      |
|                       | `ESC[J`                 | From cursor to end   |
|                       | `ESC[1J`                | From start to cursor |
|                       | `ESC[2J`                | Entire screen        |
|                       | `ESC[3J`                | + scrollback buffer  |
|                       | `ESC[K`                 | Line from cursor     |
|                       | `ESC[1K`                | Line to cursor       |
|                       | `ESC[2K`                | Entire line          |
| **Scrolling**         |                         |                      |
|                       | `ESC[<t>;<b>r`          | Set scroll region    |
|                       | `ESC[r`                 | Reset region         |
|                       | `ESC M`                 | Scroll up            |
|                       | `ESC D`                 | Scroll down          |
| **Text Style**        |                         |                      |
|                       | `ESC[0m`                | Reset all            |
|                       | `ESC[1m`                | Bold                 |
|                       | `ESC[2m`                | Dim                  |
|                       | `ESC[3m`                | Italic               |
|                       | `ESC[4m`                | Underline            |
|                       | `ESC[5m`                | Blink                |
|                       | `ESC[7m`                | Reverse              |
|                       | `ESC[8m`                | Hidden               |
|                       | `ESC[9m`                | Strikethrough        |
| **16 Colors**         |                         |                      |
|                       | `ESC[30-37m`            | FG standard          |
|                       | `ESC[40-47m`            | BG standard          |
|                       | `ESC[90-97m`            | FG bright            |
|                       | `ESC[100-107m`          | BG bright            |
| **256 Colors**        |                         |                      |
|                       | `ESC[38;5;<n>m`         | FG (n: 0-255)        |
|                       | `ESC[48;5;<n>m`         | BG (n: 0-255)        |
| **True Color**        |                         |                      |
|                       | `ESC[38;2;<r>;<g>;<b>m` | FG RGB               |
|                       | `ESC[48;2;<r>;<g>;<b>m` | BG RGB               |
| **Alt Buffer**        |                         |                      |
|                       | `ESC[?1049h`            | Enable               |
|                       | `ESC[?1049l`            | Disable              |
| **Mouse**             |                         |                      |
|                       | `ESC[?1000h`            | Enable normal        |
|                       | `ESC[?1002h`            | Enable button        |
|                       | `ESC[?1003h`            | Enable any           |
|                       | `ESC[?1006h`            | SGR mode             |
|                       | `ESC[?1000l`            | Disable              |
| **Modes**             |                         |                      |
|                       | `ESC[?7h/l`             | Line wrap on/off     |
|                       | `ESC[?2004h/l`          | Bracketed paste      |
| **Query**             |                         |                      |
|                       | `ESC[6n`                | Cursor position      |
|                       | `ESC[c`                 | Device attrs         |
| **Reset**             |                         |                      |
|                       | `ESC c`                 | Full reset (RIS)     |

---

## Appendix B: Unicode Drawing Characters

### Box Drawing

```
┌─┬─┐  ╔═╦═╗  ╭─┬─╮  ┏━┳━┓
│ │ │  ║ ║ ║  │ │ │  ┃ ┃ ┃
├─┼─┤  ╠═╬═╣  ├─┼─┤  ┣━╋━┫
│ │ │  ║ ║ ║  │ │ │  ┃ ┃ ┃
└─┴─┘  ╚═╩═╝  ╰─┴─╯  ┗━┻━┛
```

### Block Elements

```
█ Full block
▀ Upper half
▄ Lower half
▌ Left half
▐ Right half

░ Light shade
▒ Medium shade
▓ Dark shade

▏▎▍▌▋▊▉ Vertical blocks (eighths)
```

### Progress Indicators

```
⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏ Braille dots
◐◓◑◒ Circle halves
◜◠◝◞◡◟ Arcs
▁▂▃▄▅▆▇█ Bars
```

### Symbols

```
✓ ✔ ✗ ✘ Check/X marks
→ ← ↑ ↓ Arrows
⚠ Warning
ℹ Info
● ○ Bullet points
```

---

**End of Research Document**
