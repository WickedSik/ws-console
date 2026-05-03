# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Code Modification Policy

- **NEVER write code without explicit "proceed" or "implement" instruction**
- **Always propose approach first and wait for approval**
- **Separate analysis/planning from implementation phases**
- When expanding tasks or analyzing requirements, DO NOT start coding
- Wait for explicit permission phrases like "proceed", "implement", or "start coding"

## Project Overview

**ws-console** is a ZIO-native console library providing rich terminal interfaces for **modern interactive terminals only**. The library uses pure ANSI escape codes with ZIO effects for colorized output, text wrapping, and pattern-based formatting.

### Terminal Support Policy

**Supported (interactive terminals only):**
- macOS: Terminal.app, iTerm2
- Linux: GNOME Terminal, Konsole, Alacritty, Kitty
- Windows: Windows Terminal (not cmd.exe)
- IDE terminals: VS Code, JetBrains

**NOT Supported (will fail with clear error):**
- cmd.exe, legacy PowerShell
- Dumb terminals, Linux raw console
- Non-interactive environments (CI/CD, pipes, redirected I/O)
- Terminals without Unicode or 256+ colors

This is a deliberate design decision to limit complexity. There are no fallback code paths.

## Reminder for Developer

**REMEMBER TO EXPLICITLY REQUEST APPROVAL**: When asking Claude to work on code, always use phrases like:
- "Analyze first, then wait for approval to implement"
- "Propose the approach but don't start coding yet"
- "Show me the plan, then I'll tell you to proceed"
- "DO NOT CODE - just show me what you would do"

## Development Wisdom

- Do not get stuck in a loop of trying to fix errors, take a step back and evaluate the entire system before simply trying to fix an compilation error. ZIO is difficult, accept this.

## Git Operations

**Always prefer MCP git tools** (`mcp__git__git_status`, `mcp__git__git_log`, `mcp__git__git_diff`, `mcp__git__git_add`, `mcp__git__git_commit`, etc.) over Bash git commands. MCP tools provide structured output and don't require path arguments when operating on this repository.

Only fall back to Bash for git operations that have no MCP equivalent (e.g., `git push`, `git rebase`).

## Build and Development Commands

```bash
# Build and compilation
sbt compile                 # Compile the project
sbt ~compile               # Continuous compilation during development

# Testing
sbt test                   # Run all tests (ZIO Test framework)
sbt testOnly ClassName     # Run specific test class
sbt testQuick              # Run only failed tests

# Running the application
sbt run                    # Run via SBT
./run.sh                   # Run using provided script with proper classpath

# Development utilities
sbt console                # REPL with project classpath loaded
```

## Architecture Overview

### Core Framework
- **ZIO 2.1.23**: Functional effects system with dependency injection via ZLayers
- **Scala 3.3.6**: Modern Scala with improved type system
- **ZIO Test**: Property-based and unit testing framework (planned)

### Key Components

**Terminal Trait (`Terminal.scala`)** ✅ Implemented
- Public API contract that all implementations must provide
- ZIO effects for all operations with IOException error channel
- Methods for reading input, printing output, and text formatting
- Defines ColorDepth enumeration for terminal color capabilities

**ConsoleFactory (`ConsoleFactory.scala`)** 🚧 Skeleton
- Factory for creating Terminal instances with ZLayer support
- Capability detection integration (stubbed)
- Contains skeleton implementations for terminal backends:
  - `JLineTerminal`: Reserved for future rich terminal support
  - `AnsiTerminal`: Standard ANSI escape code implementation
- All terminal methods currently return `???` (not yet implemented)

**Configuration System (`config/ConsoleConfig.scala`)** ✅ Implemented
- `ConsoleConfig`: Main configuration with patterns, colors, and behavior settings
- `PatternConfig`: Defines text patterns (primary, secondary, strong, code, quoted, tagged, marked)
- `ColorScheme`: Maps patterns to colors with semantic message colors
- `BehaviorConfig`: Runtime behavior flags (width, colors, wrapping, ANSI/JLine forcing)

**Terminal Capabilities (`capabilities/TerminalCapabilities.scala`)** ✅ Data Structure
- `TerminalCapabilities`: Capability detection data structure
- `TerminalType`: Enumeration of terminal types (JLine3, Ansi, Dumb, Unknown)
- `EnvironmentType`: Environment classification (Standard, IDE, CI/CD, Docker, SSH)
- Detection logic not yet implemented

**Demo Application (`demo/`)** ✅ Implemented
- `Main.scala` extends `ZIOAppDefault`, delegates to `DemoApp.run`
- `DemoApp`: Panel orchestrator using `ZIO.acquireRelease` in `ZIO.scoped` for resource safety
  - Enters alternate buffer + hides cursor on setup
  - Restores scroll region + shows cursor + exits alt buffer on cleanup (even on CTRL+C)
- `DemoUtils`: Shared rendering utilities (`printAnsi`, `clearAndHeader`, `sectionLabel`, `pause`, `centeredText`)
- `BoxDrawing`: Unicode constants (single/double box drawing, block elements for progress bars, braille spinner frames)
- 8 auto-advancing panels in `demo/panels/`:
  - `WelcomePanel`: Title screen with double-line box
  - `ColorGalleryPanel`: 16-color, 256-color palette, HSV-based RGB gradient
  - `StyleShowcasePanel`: All text styles (bold, dim, italic, underline, strikethrough, reverse, blink) + combinations
  - `CursorDemoPanel`: Absolute positioning via `moveTo`, box drawing, save/restore cursor
  - `ScrollRegionPanel`: Fixed header/status bar with animated scrolling content (self-timed)
  - `SpinnerPanel`: 60-frame braille dot animation at 80ms (self-timed)
  - `ProgressBarPanel`: 480-step precision progress bar using block elements (self-timed)
  - `FarewellPanel`: Summary and exit screen
- All panels use signature `ZIO[Any, IOException, Unit]` (will evolve to `ZIO[Terminal, IOException, Unit]`)
- Serves as a live integration test for AnsiBuilder and all ANSI primitives
- Run with `sbt run`, CTRL+C exits cleanly

**Planned Components** 🔮
- **TextWrapper**: Word-aware text wrapping with color code preservation
- **Pattern Parser**: Single-pass parser for text pattern recognition and colorization
- **Capability Detector**: Runtime terminal feature detection
- **ANSI Terminal Implementation**: Full implementation of Terminal trait using ANSI codes
- **Rich Terminal Implementation**: Optional JLine3-based implementation for advanced features

### Error Handling Architecture

- `IOException`: Used as the error channel for all Terminal operations
- Terminal state restoration on all exit paths via `ZIO.acquireRelease` (implemented in DemoApp)
- ZIO handles SIGINT (CTRL+C) as fiber interruption; release actions in `ZIO.scoped` still execute
- Emergency shutdown hooks as safety net for unexpected termination (planned)

All errors will provide clear messages with context about terminal operations.

## Configuration Structure

Console configuration uses case classes with companion object defaults:

```scala
case class ConsoleConfig(
  patterns: PatternConfig = PatternConfig.default,
  colors: ColorScheme = ColorScheme.default,
  behavior: BehaviorConfig = BehaviorConfig.default
)

case class PatternConfig(
  primary: (String, String) = ("*", "*"),        // *text* → Primary emphasis
  secondary: (String, String) = ("_", "_"),      // _text_ → Secondary emphasis
  strong: (String, String) = ("**", "**"),       // **text** → Strong emphasis
  code: (String, String) = ("`", "`"),           // `text` → Code/literal text
  quoted: (String, String) = ("\"", "\""),       // "text" → Quoted text
  tagged: (String, String) = ("<", ">"),         // <text> → Tagged sections
  marked: (String, String) = ("==", "==")        // ==text== → Marked/highlighted
)

case class ColorScheme(
  primary: String = "magenta_bold",    // Maps to primary pattern
  secondary: String = "italic",        // Maps to secondary pattern
  strong: String = "bold",             // Maps to strong pattern
  code: String = "cyan",               // Maps to code pattern
  quoted: String = "blue",             // Maps to quoted pattern
  tagged: String = "yellow",           // Maps to tagged pattern
  marked: String = "reverse",          // Maps to marked pattern
  // Semantic colors for messages
  error: String = "red",
  success: String = "green",
  warning: String = "yellow",
  info: String = "blue"
)

case class BehaviorConfig(
  defaultWidth: Int = 80,
  enableColors: Boolean = true,
  enableWrapping: Boolean = true,
  enablePatterns: Boolean = true,
  forceAnsi: Boolean = false,
  forceJLine: Boolean = false,
  silentFallback: Boolean = true
)

// Usage with defaults
val config = ConsoleConfig.default

// Usage with customization
given custom: ConsoleConfig = ConsoleConfig(
  patterns = PatternConfig(
    primary = ("**", "**"),
    secondary = ("_", "_")
  ),
  behavior = BehaviorConfig(
    forceAnsi = true
  )
)
```

## Testing Patterns

**Current Status**: No tests implemented yet. Test directory structure exists but is empty.

**Deliberate Exclusion**: AnsiBuilder does NOT need unit tests. It is a thin wrapper over string concatenation where nearly every method is `def x = append(SomeConstant)`. Testing this would be tautological (asserting constants equal constants). The demo application serves as the integration test for ANSI primitives. Tests will matter for future phases with real algorithms: pattern parsing, text wrapping, capability detection.

**Planned Testing Strategy** using ZIO Test Framework:

**Test Environment Setup**:
- Mock terminal environments for isolated testing
- Test only supported modern terminals (iTerm2, GNOME Terminal, Windows Terminal, etc.)
- Terminal capability validation (ensure fail-fast works correctly)
- Text processing and pattern recognition validation

**Planned Test Categories**:
- Terminal trait contract testing (`TerminalSpec.scala`)
- ANSI terminal implementation (`AnsiTerminalSpec.scala`)
- Rich terminal implementation (`RichTerminalSpec.scala`) - if JLine3 is added
- Text wrapping algorithms (`TextWrapperSpec.scala`)
- Pattern parsing and colorization (`PatternParserSpec.scala`)
- Terminal capability detection (`CapabilityDetectorSpec.scala`)
- Configuration system validation (`ConsoleConfigSpec.scala`)
- Cross-platform behavior verification (`CrossPlatformSpec.scala`)
- Factory and ZLayer composition (`ConsoleFactorySpec.scala`)

**Testing Best Practices & Optimizations**:

*Code Style Optimizations*:
- Use direct boolean assertions instead of `== true` comparisons
- Use comma-separated parameters in `assertTrue()` for better readability and maintainability:
  ```scala
  // Preferred
  assertTrue(
    condition1,
    condition2,
    condition3
  )
  
  // Avoid
  assertTrue(
    condition1 && condition2 && condition3
  )
  ```

*Performance Testing Guidelines*:
- Include performance thresholds for text processing operations:
  - Text wrapping for large documents (10KB): <500ms
  - Pattern recognition and colorization: <100ms per 1KB
  - Terminal capability detection: <200ms
- Use `measurePerformance()` helper for execution time validation
- Test text processing integrity (input → wrap → unwrap consistency)

*Comprehensive Coverage Patterns*:
- **Unicode Support**: Test Chinese (你好), Russian (Здравствуй), emojis (🚀🛑), special characters
- **Edge Case Testing**: Very long words, empty strings, whitespace-only content, extreme widths
- **Multi-cycle Validation**: Test text → pattern → color → wrap → output cycles for consistency
- **Supported Terminal Testing**: iTerm2, GNOME Terminal, Windows Terminal, VS Code terminal
- **Fail-Fast Validation**: Verify clear errors on unsupported terminals (dumb, no TTY, no colors)
- **Pattern Recognition**: Nested patterns, escaped sequences, malformed patterns

*Text Processing Testing Strategy*:
- Round-trip text wrapping (wrap → unwrap → wrap consistency)
- Color code preservation during text transformations
- Pattern parsing with complex nested structures
- Unicode handling and display width calculations
- Terminal capability detection across environments
- Cross-platform behavior validation

*Error Scenario Coverage*:
- Terminal state corruption and recovery
- Signal handler interference detection
- Resource cleanup during unexpected termination
- Invalid terminal dimensions and capability detection failures

## Development Patterns

**ZIO Dependency Injection**: Use ZLayers for service composition and dependency management
- `ConsoleFactory.layer` provides default Terminal implementation
- Custom configurations passed via `ConsoleFactory.layer(config)`

**Configuration System**: Use case classes with companion object defaults
- `ConsoleConfig.default` provides sensible defaults
- All configuration is immutable and composable
- Support for `given` instances for implicit configuration passing

**Error Handling**: Use IOException as the error channel for all Terminal operations
- Consistent error handling across all Terminal methods
- Clear error messages with operational context
- Future: Resource cleanup via ZIO's acquire/release pattern

**Resource Management** (Partially Implemented):
- ZIO's `acquireRelease` in `ZIO.scoped` for terminal state management (implemented in DemoApp)
- Pattern: setup enters alt buffer + hides cursor, cleanup restores scroll region + shows cursor + exits alt buffer
- CTRL+C triggers ZIO fiber interruption, release actions still execute within the scoped region
- Emergency shutdown hooks for terminal state restoration (planned)
- `ZIO.sleep` returns `ZIO[Any, Nothing, Unit]` (infallible) - do NOT use `.orDie` on it

**Testing Strategy** (To Be Implemented):
- Mock terminal environments for isolated testing
- Property-based testing for text processing algorithms
- Test only modern supported terminals (no legacy terminal testing)
- Verify fail-fast behavior on unsupported environments
- Edge case coverage for Unicode, ANSI codes, and terminal dimensions

**Pattern Recognition** (Planned):
- Efficient single-pass parsers for text pattern recognition
- Pattern-to-color mapping from configuration
- Support for nested and escaped patterns

**Capability Detection** (Planned):
- Validate terminal requirements at startup (TTY, 256+ colors, Unicode)
- **Fail fast** if requirements not met - no graceful degradation
- Clear error messages listing missing requirements and supported terminals
- Optional `forceUnsafe` flag for advanced users to bypass checks

**Implementation Priority**:
- Defer to active task scrolls in `.claude/tasks/` for current work
- If no tasks remain, `docs/terminal-architecture.md` is the guiding principle for what comes next

## Scala Coding Standards

### String Interpolation Best Practices

**Avoid Unnecessary Braces**: Use simple variable references without braces when possible
```scala
// Preferred
s"Hello $name, welcome!"
s"Model '$modelName' not found"

// Avoid
s"Hello ${name}, welcome!"
s"Model '${modelName}' not found"
```

**Use Braces Only When Necessary**: For complex expressions or when adjacent to other characters
```scala
// Correct usage of braces
s"${user.name.toUpperCase}_profile"
s"Total: ${count + 1} items"
```

### Collection Access Patterns

**Semantic Method Names**: Use descriptive methods instead of indexed access
```scala
// Preferred
results.head          // First element
results.last          // Last element
list.headOption       // Safe first element access
collection.slice(1, 3) // Range access

// Avoid
results(0)            // Indexed access to first
results(results.length - 1) // Indexed access to last
collection.drop(1).take(2)  // Inefficient range access
```

**Safe Collection Operations**: Prefer safe alternatives when possible
```scala
// Preferred
list.headOption.getOrElse(defaultValue)
collection.find(predicate)

// Use with caution
list.head  // Can throw if empty
```

### Method Call Standards

**Parameterless Methods**: Omit parentheses for parameterless methods with no side effects
```scala
// Preferred
builder.toAttributedString
list.length
string.trim

// Avoid
builder.toAttributedString()
list.length()
string.trim()
```

**Constructor Calls**: Omit empty parentheses for constructors
```scala
// Preferred
new ColorizedConsole
new ApiClient(config)

// Avoid
new ColorizedConsole()
```

### Boolean Logic Simplification

**Negative Conditions**: Use more readable negative patterns
```scala
// Preferred
!results.contains(null)
list.nonEmpty
condition.isDefined

// Avoid
results.forall(_ != null)
list.length > 0
condition != None
```

**Boolean Assertions**: Direct boolean usage in tests
```scala
// Preferred
assertTrue(flag)
assertFalse(condition)

// Avoid
assertTrue(flag == true)
assertTrue(condition == false)
```

### Test Writing Standards

**Assertion Patterns**: Use comma-separated assertions for better readability
```scala
// Preferred
assertTrue(
  condition1,
  condition2,
  condition3
)

// Avoid
assertTrue(condition1 && condition2 && condition3)
```

**Test Method Signatures**: Include explicit return types for test specifications
```scala
// Required
def spec: Spec[TestEnvironment & Scope, Any] = suite("TestSuite")(
  // test cases
)
```

### Visibility and Encapsulation

**Object Visibility**: Make utility objects private when they're implementation details
```scala
// Preferred
private object Validators {
  def validateString(s: String): Boolean = s.nonEmpty
}

// Avoid exposing internal utilities
object Validators {  // Public when it should be private
  def validateString(s: String): Boolean = s.nonEmpty
}
```

### Import Management

**Clean Imports**: Remove unused imports and organize them logically
```scala
// Group imports: Scala standard library, third-party, project
import scala.util.Try

import zio.{Chunk, ZIO}
import io.circe.syntax.*

import client.ApiConfig
import models.ModelDefinition
```

### Code Quality Suppressions

**IntelliJ Inspections**: Use suppression comments for acceptable violations
```scala
//noinspection HttpUrlsUsage
val url = s"http://$host:$port"  // Local development URLs

//noinspection RedundantDefaultArgument  
def spec: Spec[TestEnvironment & Scope, Any] = suite("Test")(
  // test cases
)
```

### Performance Considerations

**Efficient Collections**: Choose appropriate collection operations
```scala
// Preferred for range operations
collection.slice(start, end)

// Avoid inefficient chaining
collection.drop(start).take(end - start)
```

**String Operations**: Efficient string building and manipulation
```scala
// For multiple concatenations
val builder = new StringBuilder
// or use string interpolation for simple cases
```

### Type Safety

**Explicit Types**: Provide return types for public methods and complex expressions
```scala
// Preferred
def processConfig(config: Config): Either[ConfigError, ValidConfig] = {
  // implementation
}

// Required for recursive functions
def factorial(n: Int): Int = if (n <= 1) 1 else n * factorial(n - 1)
```