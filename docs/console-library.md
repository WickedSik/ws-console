# WS-Console Library Architecture & Implementation Guide

## Executive Summary

This document provides a complete specification for extracting the console functionality from scala-ollama into a standalone, reusable JVM console library called **ws-console**. The library provides a rich terminal interface with automatic capability detection, colorized output, text wrapping, and cross-platform support through both JLine3 and fallback implementations.

## Project Identity

- **Name:** ws-console
- **Package:** `io.github.wickedsik.wsconsole`
- **Philosophy:** ZIO-native console library with capability-based progressive enhancement
- **Target:** Scala developers needing reliable console I/O with colors and wrapping, not full TUI applications

## Core Architecture

### Design Philosophy

The library follows a **capability-based progressive enhancement** model:
1. Detect terminal capabilities at runtime
2. Choose optimal implementation (JLine3 for rich terminals, ANSI fallback for basic)
3. Gracefully degrade features based on environment (CI/CD, Docker, SSH, etc.)
4. Provide consistent API regardless of underlying implementation

### Component Hierarchy

```
Terminal (trait)
├── JLineConsole (rich terminal implementation)
│   ├── JLine3 Terminal & LineReader
│   ├── AttributedString colorization
│   └── Emergency shutdown hooks
├── ColorizedConsole (fallback implementation)
│   ├── Basic ANSI color codes
│   └── Standard I/O operations
└── ConsoleFactory (capability-aware factory)
    ├── TerminalCapabilities detection
    └── Implementation selection logic
```

## Dependencies

### Required Libraries

```scala
// build.sbt
libraryDependencies ++= Seq(
  // Core
  "dev.zio" %% "zio" % "2.1.18",
  
  // JLine3 for rich terminal support
  "org.jline" % "jline-terminal" % "3.27.1",
  "org.jline" % "jline-reader" % "3.27.1",
  "org.jline" % "jline-style" % "3.27.1",
  
  // Testing
  "dev.zio" %% "zio-test" % "2.1.18" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.18" % Test
)
```

### Scala Version
- **Scala 3.3.6** (can be cross-compiled to 2.13 with minor syntax adjustments)

## Core Components

### 1. Terminal Trait (API Contract)

**File:** `Terminal.scala`

The public API that all implementations must provide:

```scala
trait Terminal {
  def printIntroduction(text: String): IO[IOException, Unit]
  def printParagraphsWrapped(text: String, maxWidth: Int = 80): IO[IOException, Unit]
  def readLine: IO[IOException, String]
  def print(line: String): IO[IOException, Unit]
  def printLine(line: String): IO[IOException, Unit]
  def printWrapped(text: String, maxWidth: Int = 80): IO[IOException, Unit]
}
```

**Design Decisions:**
- All methods return ZIO effects for composability
- IOException as error channel for consistency
- Default width of 80 characters (standard terminal width)

### 2. JLineConsole (Rich Implementation)

**File:** `JLineConsole.scala`

**Key Features:**
- JLine3-based terminal with advanced input handling
- AttributedString for proper color support
- Emergency shutdown hooks for terminal state restoration
- Cross-platform compatibility (Windows, Unix, macOS)

**Critical Implementation Details:**

1. **Resource Management:**
   - Uses ZIO's acquire/release pattern
   - Emergency shutdown hook as safety net
   - Proper cleanup even during JVM crashes

2. **Color Pattern Support:**
   - `*action*` → Magenta + Bold
   - `"speech"` → Blue
   - `<intro>text</intro>` → Yellow
   - Patterns are converted to XML-style tags for processing

3. **Text Processing Pipeline:**
   ```
   Input → Pattern Detection → Tag Conversion → AttributedString Building → Terminal Output
   ```

**Potential Issues:**
- **Q: Why both shutdown hooks AND ZIO cleanup?**
  - A: Shutdown hooks handle unexpected JVM termination, ZIO handles normal cleanup
- **Q: Thread safety of terminal operations?**
  - A: JLine3 terminal is thread-safe, but consider adding synchronization for critical sections

### 3. ColorizedConsole (Fallback Implementation)

**File:** `ColorizedConsole.scala`

**Purpose:** Lightweight fallback for environments where JLine3 won't work properly

**Implementation:**
- Uses standard ANSI escape codes
- Delegates to ZIO's Console.ConsoleLive
- Same pattern recognition as JLineConsole

**Critical Consideration:**
- Currently hardcoded to ANSI colors - needs NO_COLOR environment variable check

### 4. TextWrapper Utility

**File:** `TextWrapper.scala`

**Features:**
- Word-aware wrapping (never breaks words)
- Color code preservation during wrapping
- Paragraph support with preserved breaks
- Whitespace normalization

**Algorithm:**
```scala
1. Strip color codes for length calculation
2. Split into words
3. Build lines using tail-recursive accumulator
4. Preserve color codes in output
```

**Performance Consideration:**
- Tail-recursive implementation prevents stack overflow on large texts
- Could benefit from streaming for very large inputs

### 5. Terminal Capability Detection

**Files:** 
- `capabilities/TerminalCapabilities.scala`
- `capabilities/JLine3TerminalDetector.scala`

**Detection Matrix:**

| Environment | Detection Method | Chosen Implementation |
|------------|------------------|----------------------|
| CI/CD | Environment variables | ColorizedConsole |
| Docker | File system markers | ColorizedConsole |
| SSH Session | TTY detection | JLineConsole |
| IDE Terminal | JLine3 capabilities | JLineConsole |
| Windows Terminal | Platform detection | JLineConsole |
| Dumb Terminal | TERM variable | ColorizedConsole |

**Key Capabilities Detected:**
- Color support (0, 16, 256, or 16M colors)
- Terminal dimensions
- Resize support (SIGWINCH)
- Cursor positioning
- Raw mode support

**Critical Issues to Address:**

1. **Signal Handler Testing:**
   - Current implementation tests WINCH signal support
   - May interfere with application signal handlers
   - Consider non-intrusive detection methods

2. **Docker Detection:**
   - Checks for `/.dockerenv` file
   - May give false positives in some container environments

### 6. ConsoleFactory

**File:** `ConsoleFactory.scala`

**Selection Logic:**
```scala
if (CI_CD || Headless || Restricted || Dumb) {
  ColorizedConsole
} else {
  JLineConsole
}
```

**Logging Strategy:**
- Uses `System.err` for capability detection logs
- Prefixed with `[scala-ollama]` - should be configurable

## Project Structure

```
ws-console/
├── build.sbt
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── io/
│   │           └── github/
│   │               └── wickedsik/
│   │                   └── wsconsole/
│   │                       ├── Terminal.scala
│   │                       ├── config/
│   │                       │   ├── ConsoleConfig.scala
│   │                       │   ├── PatternConfig.scala
│   │                       │   └── ColorScheme.scala
│   │                       ├── implementations/
│   │                       │   ├── JLineTerminal.scala
│   │                       │   └── AnsiTerminal.scala
│   │                       ├── ConsoleFactory.scala
│   │                       ├── text/
│   │                       │   ├── TextWrapper.scala
│   │                       │   ├── PatternParser.scala
│   │                       │   └── Escaping.scala
│   │                       └── capabilities/
│   │                           ├── TerminalCapabilities.scala
│   │                           └── JLine3TerminalDetector.scala
│   └── test/
│       └── scala/
│           └── io/
│               └── github/
│                   └── wickedsik/
│                       └── wsconsole/
│                           ├── TerminalSpec.scala
│                           ├── implementations/
│                           │   ├── JLineTerminalSpec.scala
│                           │   └── AnsiTerminalSpec.scala
│                           ├── text/
│                           │   ├── TextWrapperSpec.scala
│                           │   └── PatternParserSpec.scala
│                           └── capabilities/
│                               └── TerminalDetectorSpec.scala
```

## Build Configuration

```scala
// build.sbt
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "io.github.wickedsik"
ThisBuild / version := "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "ws-console",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.18",
      "org.jline" % "jline-terminal" % "3.27.1",
      "org.jline" % "jline-reader" % "3.27.1",
      "org.jline" % "jline-style" % "3.27.1",
      "dev.zio" %% "zio-test" % "2.1.18" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.18" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
```

## Usage Example

```scala
import io.github.wickedsik.wsconsole._
import zio._

object ConsoleApp extends ZIOAppDefault {
  // With default configuration
  val program = for {
    terminal <- ZIO.service[Terminal]
    _        <- terminal.writeLine("Welcome to ws-console!")
    _        <- terminal.writeLine("Supports *actions* and \"speech\" patterns")
    name     <- terminal.prompt("Enter your name: ")
    password <- terminal.readPassword("Enter password: ")
    _        <- terminal.writeLine(s"Hello, $name!")
  } yield ()

  def run = program.provide(ConsoleFactory.layer)
}

// With custom configuration
object CustomConsoleApp extends ZIOAppDefault {
  given ConsoleConfig = ConsoleConfig(
    patterns = PatternConfig(
      action = ("**", "**"),
      emphasis = ("_", "_")
    )
  )
  
  val program = for {
    terminal <- ZIO.service[Terminal]
    _        <- terminal.writeLine("Now **actions** use double asterisks")
    _        <- terminal.writeLine("And _emphasis_ uses underscores")
  } yield ()

  def run = program.provide(ConsoleFactory.layer)
}
```

## Testing Strategy

### Unit Tests
- Mock terminal for isolated testing
- Test color pattern recognition
- Verify text wrapping algorithms
- Capability detection edge cases

### Integration Tests
- Real terminal interaction (manual)
- CI/CD environment detection
- Cross-platform verification

### Property-Based Tests
- Text wrapping properties (no word breaking, preserves content)
- Color code preservation
- Paragraph handling

## Design Decisions & Implementation Strategy

### 1. **Configuration System**
**Decision:** Implicit configuration objects with sensible defaults
```scala
case class ConsoleConfig(
  patterns: PatternConfig = PatternConfig.default,
  colors: ColorScheme = ColorScheme.default,
  behavior: BehaviorConfig = BehaviorConfig.default
)

given default: ConsoleConfig = ConsoleConfig()
```

### 2. **Error Handling**
**Decision:** Keep IOException for v1, consider sealed hierarchy for v2
- Simple and familiar for users
- Can evolve based on actual usage patterns

### 3. **Logging**
**Decision:** Remove all hardcoded prefixes, use configurable logger
```scala
trait ConsoleLogger {
  def debug(message: String): Unit
  def warn(message: String): Unit
}

object NoOpLogger extends ConsoleLogger {
  def debug(message: String): Unit = ()
  def warn(message: String): Unit = ()
}
```

### 4. **Pattern Escape Mechanism**
**Decision:** Backslash escaping with single-pass parser
- `\*` → literal asterisk
- `\"` → literal quote
- `\\` → literal backslash

### 5. **Thread Safety**
**Decision:** Document JLine3's guarantees, no additional synchronization
- JLine3 Terminal is thread-safe
- Document this clearly in API docs

### 6. **Resource Management**
**Decision:** Keep triple-safety approach
- ZIO acquire/release for normal paths
- Shutdown hooks for JVM termination
- Try/finally for detector cleanup
- Justified by preventing terminal corruption

### 7. **API Design**
**Decision:** Semantic methods with minimal v1 scope

```scala
trait Terminal {
  // Core I/O
  def write(text: String): IO[IOException, Unit]
  def writeLine(text: String): IO[IOException, Unit]
  def readLine: IO[IOException, String]
  def readPassword(prompt: String): IO[IOException, String]
  
  // Convenience
  def prompt(text: String): IO[IOException, String]
  def confirm(text: String, default: Boolean = false): IO[IOException, Boolean]
  
  // Text formatting
  def wrapped(text: String, width: Int = 80): IO[IOException, Unit]
  def paragraphs(text: String, width: Int = 80): IO[IOException, Unit]
}
```

### 8. **Scala Version**
**Decision:** Scala 3 only for v1
- ZIO ecosystem is Scala 3 focused
- Community can port if needed

## Performance Considerations

1. **Text Wrapping:** O(n) where n = number of words
2. **Color Pattern Matching:** Multiple regex passes (could be optimized)
3. **Terminal Detection:** One-time cost at startup
4. **AttributedString Building:** Allocation-heavy for large outputs

## Security Considerations

1. **Signal Handlers:** May interfere with application handlers
2. **Terminal State:** Ensure restoration on all exit paths
3. **Input Validation:** No current sanitization of user input
4. **Resource Leaks:** Shutdown hooks may accumulate if many terminals created

## Critical Fixes Required

### Signal Handler Testing (Priority: HIGH)
**Current Issue:** Intrusive WINCH signal testing
```scala
// WRONG - Actually installs handler
terminal.handle(Signal.WINCH, SignalHandler.SIG_DFL)

// CORRECT - Non-intrusive detection
private def detectResizeSupport(terminal: JLineTerminal): Boolean = {
  val hasValidDimensions = terminal.getWidth > 0 && terminal.getHeight > 0
  val termType = terminal.getType
  !termType.contains("dumb") && hasValidDimensions
}
```

### Pattern Parser Optimization (Priority: HIGH)
**Current Issue:** Multiple regex passes
```scala
// Single-pass parser implementation
class PatternParser(config: PatternConfig) {
  def parse(input: String): String = {
    val result = StringBuilder()
    var i = 0
    while (i < input.length) {
      input.charAt(i) match {
        case '\\' if i + 1 < input.length =>
          result.append(input.charAt(i + 1))
          i += 2
        case char if isPatternStart(char, i) =>
          val (styled, newIndex) = parsePattern(input, i)
          result.append(styled)
          i = newIndex
        case char =>
          result.append(char)
          i += 1
      }
    }
    result.toString
  }
}
```

### Password Input Implementation (Priority: CRITICAL)
```scala
def readPassword(prompt: String): IO[IOException, String] = {
  ZIO.attempt {
    lineReader.readLine(prompt, '*')  // Masked input
  }.mapError(ex => new IOException(s"Failed to read password: ${ex.getMessage}", ex))
}
```

## Migration Strategy

### Phase 1: Internal Extraction
1. Create `ws-console` as submodule within scala-ollama
2. Move console components to new package structure
3. Implement critical fixes (escaping, signal handler, password)
4. Test with existing ollama application

### Phase 2: Independent Library
1. Extract to separate repository
2. Add configuration system
3. Implement semantic API
4. Create example applications
5. Write comprehensive tests

### Phase 3: Open Source Release
1. Polish documentation
2. Set up CI/CD pipeline
3. Publish to Maven Central
4. Announce to Scala community

## Version Roadmap

### v0.1.0 - Minimal Viable Library
- ✅ Core Terminal trait
- ✅ JLine3 + ANSI implementations
- ✅ Capability detection
- ✅ Text wrapping
- ✅ Password input
- ✅ Escape sequences
- ✅ Configuration system

### v0.2.0 - Enhanced Interactions
- Progress indicators
- Single-select menus
- Confirmation dialogs

### v0.3.0 - Data Display
- Tables/grids
- Tree views
- Formatted lists

### v0.4.0 - Rich Content
- Markdown rendering
- Syntax highlighting
- Unicode art

### Future Versions
- Multi-select menus
- Streaming output
- Terminal multiplexing

## Future Enhancements

1. **Markdown Support:** Render markdown in terminal
2. **Async Operations:** Streaming output support
3. **Interactive Widgets:** Menus, progress bars, spinners
4. **Color Themes:** Configurable color schemes
5. **Unicode Art:** Box drawing, tables, charts
6. **Terminal Multiplexing:** Multiple virtual terminals

## Conclusion

**ws-console** will fill a gap in the Scala ecosystem - a simple, reliable console library that sits between low-level JLine and full TUI frameworks. With ZIO-native design, intelligent capability detection, and progressive enhancement, it provides exactly what Scala developers need for console applications.

**Key Differentiators:**
- Pure JVM with no native dependencies
- ZIO-native with functional design
- Automatic capability detection with graceful fallback
- Configurable patterns without breaking changes
- Minimal API surface with room to grow

**Implementation Priorities:**
1. Fix critical issues (signal handler, parser, password input)
2. Implement configuration system
3. Create semantic API
4. Add comprehensive tests
5. Write clear documentation
6. Ship v0.1.0 and iterate based on feedback

The library's philosophy: **"Do 5 things really well"** rather than attempting to be a complete TUI framework. This focused approach ensures reliability and maintainability while leaving room for community-driven evolution.