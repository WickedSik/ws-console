# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Code Modification Policy

- **NEVER write code without explicit "proceed" or "implement" instruction**
- **Always propose approach first and wait for approval**
- **Separate analysis/planning from implementation phases**
- When expanding tasks or analyzing requirements, DO NOT start coding
- Wait for explicit permission phrases like "proceed", "implement", or "start coding"

## Project Overview

**ws-console** is a ZIO-native console library providing rich terminal interfaces for **modern interactive terminals only**. The library uses pure ANSI escape codes with ZIO effects for colorized output, layout, components, event handling, and differential rendering.

Package prefix: `io.github.wickedsik.wsconsole` (set in `build.sbt` via `idePackagePrefix`).

Terminal support scope (supported vs unsupported terminals) lives in `README.md`; consult it there. No fallback code paths.

### Library Purpose and Demo Role

**Library purpose.** ws-console is a TUI library that exposes the full capabilities of ANSI on modern terminals. The intended consumers are downstream applications building terminal interfaces — REPL panes, log widgets, file viewers, paged scrollback, build/test output streams, dashboards, status displays. All architectural decisions are made in service of those consumers.

**Demo role.** The `demo/` application is a *presentation* of capabilities the library makes available. It is not the audience, the goal, or the design constraint. Demo panels exist to showcase features; they do not justify or limit those features.

**Counting consumers.** When evaluating whether infrastructure is justified, count the eventual library consumers — not the demo panels currently in tree. A primitive that serves one demo panel today and a planned widget category tomorrow is justified by the widget category. Building for the demo alone is the wrong frame.

**Every task ships a demo.** A new feature is accompanied by either a new demo panel or the migration of an existing panel onto the new abstraction. This keeps the demo set in sync with the library's surface area.

**Layer integrity over demo migration.** When a demo panel exercises a Layer N capability that has no representation in Layer N+1, the answer is to design the Layer N+1 representation for the library's downstream consumers, not to bypass Layer N+1 for the panel's convenience. Escape hatches that fragment the abstraction are rejected unless no consumer category needs the underlying capability.

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
sbt ~compile                # Continuous compilation during development

# Testing (ZIO Test framework)
sbt test                    # Run all tests
sbt "testOnly *FooSpec"     # Run a single spec by suffix match
sbt testQuick               # Run only previously-failed tests

# Running the demo — NEVER `sbt run`, see below
scripts/run-demo.sh             # Run the demo application (Main → DemoApp)

# Debug logging
WS_CONSOLE_DEBUG_LOG=/tmp/ws.log scripts/run-demo.sh
# DebugTerminal.live wraps TerminalFactory.live and mirrors every terminal
# op to the given file when the env var is set. No-op when absent.

# REPL
sbt console                 # Scala REPL with project classpath loaded
```

### Do not run the TUI under sbt

sbt shares the controlling terminal with the program it runs and appends `ED 0`
(`erase from cursor to end of screen`) to the TTY after our writes — 545 times in
a 20-second session, with super-shell already off. Because a frame's last cell
write used to leave the cursor wherever the diff ended, that erase destroyed
every row below it: a Tab on the Focus Demo panel repaints only row 14, and
rows 15–24 died with it, toolbar included. This is the root cause of
`.claude/tasks/demo-toolbar-disappearance.md`, misdiagnosed for two months as
terminal-side cell drift.

Measured 2026-07-31, identical pty and keystrokes throughout:

| Launch | `ED 0` on alt screen | cells destroyed | toolbar |
|---|---|---|---|
| `scripts/run-demo.sh` (own JVM) | **0** | 0 | intact |
| `sbt run`, before the cursor park | 545 | **266** | rows 22–24 blank |
| `sbt run`, after the cursor park | 545 | **9** | intact, less the corner cell |

**The cursor park is why the third row is survivable.** `Frame.render` ends every
frame with the cursor at the bottom-right corner (`BufferFlusher.toAnsi`'s
`parkAt`), so an injected cursor-relative erase has nothing below it to take.
`ED 0` erases from the cursor *inclusive*, so no park position makes it a true
no-op — the corner cell is the irreducible cost, and that is the best any
in-process mitigation can do.

Use `scripts/run-demo.sh` anyway. The park is defence in depth against a foreign
writer we do not control, not a contract with sbt; sbt also competes for stdin,
which a raw-mode TUI cannot share. `fork := true` with
`outputStrategy := Some(StdoutOutput)` does **not** help — sbt's shell writes to
the terminal independently of the program's stdout. `sbt -batch run` is the one
configuration never measured.

`build.sbt` sets `useSuperShell := false` for the same family of reasons. Do not
re-enable it, and note that disabling it is not sufficient on its own — every
number above was measured with super-shell already off.

**Diagnostic note.** `DebugTerminal` logs only *our* writes, so a foreign writer
on the TTY is invisible to it. That is precisely how this bug survived three
investigations that each concluded "our bytes are correct, the terminal disagrees."
Diagnose display corruption from a raw pty capture (`script`), never from the
debug log alone.

## Codebase Layout

Top-level Scala packages under `src/main/scala/`:

```
ansi/       Layer 1 ANSI primitives — AnsiBuilder, Csi, Style, Color, Cursor,
            Screen, Scroll, Mouse, Mode, Query, AlternateBuffer, Reset, ...
terminal/   Layer 1 Terminal trait, AnsiTerminal impl, TerminalFactory,
            TerminalCapabilities, ColorSupport, HostSystem, RawInput,
            ResizeSignal, TerminalSize, DebugTerminal (logging wrapper).
buffer/     Layer 2 double-buffered cell grid — Cell, CellStyle, Line,
            ScreenBuffer, BufferManager, Canvas, ScrollableCanvas,
            ScrollRegion, RenderOp, BufferFlusher, BoxStyle, Frame.
geometry/   Rect.
layout/     Layer 3 pure constraint solver — Constraint, Direction, Layout,
            LayoutEngine.
component/  Layer 4 component model — Component, Container (HBox/VBox),
            Panel, Text, Spacer, RawCanvas, ComponentId, RenderContext.
event/      Layer 5 input events — Event, KeyEvent, EventParser,
            EventResult, TerminalEvents.
render/    Layer 6 orchestration — Renderer, RenderPipeline, RenderOptimizer,
            RenderLoop, LayoutManager, LayoutResult, EventDispatcher,
            FocusManager, FocusOrder, FocusPolicy.
app/        Layer 7 application envelope — Application, State, Panel,
            PanelHost, PanelHostError.
unicode/    BoxDrawing constants, SequencedDrawing helpers.
demo/       Demo application — DemoApp, DemoUtils, panels/, widgets/.
Main.scala  ZIOAppDefault entry point; wires TerminalFactory.live >>>
            DebugTerminal.live with Frame.live and runs DemoApp.
```

Tests live under `src/test/scala/` mirroring the same packages, plus `testkit/`.

## Architecture

The canonical, up-to-date architecture reference is **`docs/reference/terminal-architecture.md`**. It carries layer-by-layer status notes, ratified design decisions (Q1–Q4 for Layer 6, Q3 for Layer 7), class diagrams, and shipped/deferred surface for every abstraction. Read it before making architectural changes.

Shipped status (as of the last architecture doc revision):

| Layer | Name                       | Status                                                    |
|-------|----------------------------|-----------------------------------------------------------|
| 1     | Core Terminal Abstraction  | Done — `Terminal`, `AnsiTerminal`, `TerminalFactory`, `AnsiBuilder`, `RawInput` |
| 2     | Buffer and Cell Management | Done — cell grid, double-buffer, `Frame`, `ScrollableCanvas`, `RenderOp` |
| 3     | Layout System              | Done — pure `LayoutEngine.resolve`/`split`, `Constraint` ADT |
| 4     | Component Model            | Done — `Component`, pair-per-child `HBox`/`VBox`, `Panel`, `Text`, `Spacer`, `RawCanvas` |
| 5     | Event System               | Done — `EventParser`, `Terminal.events` `ZStream`, byte-faithful key encoding |
| 6     | Rendering Pipeline         | Done — `Renderer`, `RenderPipeline`, `RenderOptimizer`, `RenderLoop`, `LayoutManager`, `EventDispatcher`, `FocusManager`; resize by 100ms polling |
| 7     | Application Framework      | Partial — `Application`, `State`, `Panel`, `PanelHost` shipped; `EventLoop`/`StateManager`/`ResourceManager` superseded by lower-layer designs |

Cross-cutting invariants worth internalising before edits:

- **Immediate-mode rendering.** Components redraw from scratch every frame. Buffer diffing at Layer 2 keeps terminal I/O minimal.
- **Component isolation.** Components render into their allocated `Rect` via a clipped `Canvas`. No direct sibling access; communication is via events and shared state.
- **Event bubbling with consumption.** Focused component → parent chain → application `onEvent`. `EventResult.Consumed` halts propagation; `Ignored` bubbles; `RequestRedraw` triggers a render.
- **Pair-per-child containers.** `HBox`/`VBox` carry `Seq[(Constraint, Component)]` — never separate constraint/child lists. Drift is structurally impossible.
- **Resource safety via `ZIO.acquireRelease` inside `ZIO.scoped`.** Alt-buffer, cursor visibility, raw mode, line-wrap all release on every exit path including interruption. In raw mode `Ctrl+C` is a parsed `CharKey('c', Set(Ctrl))`, not SIGINT.
- **`buffer.Frame` vs `render.Renderer`.** Layer 2's per-frame primitive is `Frame` (was `Renderer` pre-2026-05-10). Layer 6's orchestrator is `Renderer`. Do not conflate.

### The `Csi` invariant (build-enforced)

`ansi.Csi` is the single source of truth for the C0 control bytes NUL (`0x00`) and ESC (`0x1B`). No other source file may define, escape, or inline either byte. `ControlByteHygieneSpec` walks `src/` and fails the build if:

- any `.scala` file contains a raw `0x00` or `0x1B` byte, or
- any `.scala` file outside `ansi/Csi.scala` contains the escape text ` ` or ``.

Use `Csi.ESC` (String) for sequence construction — `s"${Csi.ESC}[H"` — and `Csi.EscChar` (Char) for parser/`match` arms. The rule exists because control bytes are invisible in most editors and slip through review; the spec turns invisible bugs into build failures.

### Task scrolls and ADRs

- **`.claude/tasks/`** — active work queue. Layer-N task scrolls, judgement records from doctrine reviews, and named bug/feature scrolls (e.g. `panel-opacity-and-panelhost-activation.md`, `library-packaging.md`). Consult before starting new work.
- **`docs/adr/`** — ratified architectural decisions. Currently ADR-001 (render context), ADR-002 (renderer write monopoly — **superseded by ADR-004**), ADR-003 (invalidation source taxonomy), ADR-004 (write-monopoly by capability narrowing). These are load-bearing constraints, not history. An ADR records a decision at a moment in time; when reality moves, a *new* ADR supersedes it rather than the old one being rewritten.
- **`docs/reference/terminal-architecture.md`** — the layer reference above.
- **`.claude/commissar.yml`** — doctrine manifest for the Imperial Commissar (conformance judgements).

**Implementation priority:** defer to active task scrolls first; if none apply, the architecture document is the guiding principle for what comes next.

## Testing

**Framework.** ZIO Test — `zio-test` + `zio-test-sbt` at 2.1.23; SBT test framework is `zio.test.sbt.ZTestFramework` (see `build.sbt`). Test specs extend `ZIOSpecDefault`.

**Test surface (as of writing): 51 spec files** across `ansi/`, `app/`, `buffer/`, `component/`, `event/`, `geometry/`, `layout/`, `render/`, `terminal/`, `testkit/`. Every shipped layer carries specs; the pattern for new work follows the existing package layout.

### `testkit/`

Test infrastructure lives in `src/test/scala/testkit/`. **`docs/testkit.md`** is the contributor-facing guide — how to render, assert, and decode the wire, with the sharp edges called out. Read it before writing a new visual or integration spec. The pieces:

- **`CaptureTerminal`** — a `Terminal` implementation that records every ANSI output into a byte log instead of writing to a real TTY. Use for asserting on emitted sequences.
- **`AnsiGrid`** — parses a stream of ANSI bytes into a 2D `(char, style)` grid. The visual assertion foundation.
- **`GridAssertions`** — grid-shape and content assertions layered on `AnsiGrid`.
- **`FrameHarness`** / **`RenderHarness`** — spin up a `Frame` / `RenderLoop` against `CaptureTerminal`, drive it through frames, and expose the resulting grid to the test.

Prefer the harnesses over asserting on raw ANSI strings. Cell-grid assertions survive refactors of the flusher; raw-string assertions do not.

### `ControlByteHygieneSpec`

`src/test/scala/ansi/ControlByteHygieneSpec.scala` is the guard for the `Csi` invariant described above. Runs as part of the normal test suite; a failure here means someone typed a control byte outside `Csi.scala`.

### Deliberate exclusion

`AnsiBuilder` is not unit-tested. Nearly every method is `def x = append(SomeConstant)` and asserting `constant == constant` is tautological. The demo application and the harness-based specs exercise the builder end-to-end.

### Testing conventions

- Use comma-separated `assertTrue` for readable multi-condition assertions:
  ```scala
  assertTrue(
    condition1,
    condition2,
    condition3
  )
  ```
- Specs declare explicit return type: `def spec: Spec[TestEnvironment & Scope, Any] = suite("...")(...)`.
- Direct boolean assertions (`assertTrue(flag)`, not `assertTrue(flag == true)`).
- Cover Unicode (Chinese, Cyrillic, emoji), edge cases (empty, whitespace-only, extreme widths), and round-trips (input → transform → back).

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

### Scala 3 Syntax

Prefer `:`-indented syntax for class/trait/object/enum/def bodies over `{ }` blocks. The codebase is Scala 3 (3.3.6) throughout.

```scala
// Preferred
trait Terminal:
  def write(text: String): IO[IOException, Unit]

object Csi:
  val ESC: String = ""

// Avoid
trait Terminal {
  def write(text: String): IO[IOException, Unit]
}
```

### Import Management

Group imports: Scala standard library, third-party, project-internal — each block separated by a blank line.

```scala
import scala.util.Try

import zio.{Chunk, ZIO}
import zio.stream.ZStream

import ansi.Csi
import buffer.Cell
```

### ZIO-Specific

- `ZIO.sleep` returns `ZIO[Any, Nothing, Unit]` (infallible). Do NOT call `.orDie` on it.
- Prefer `.as(value)` on the prior effect over `*> ZIO.succeed(value)` in long chains — IntelliJ's Scala 3 inferencer sometimes reports phantom errors on the latter shape (sbt compile is unaffected).
- `System.in.read()` is uninterruptible on the JVM. When racing keypress reads against animations, fork the reader **once** and race against `Fiber.await` — never re-fork on each loop iteration. A re-forked reader leaves a zombie that steals the next byte. See `DemoApp.animatedStep` for the canonical shape.

### Type Safety

Provide return types for public methods and complex expressions:

```scala
def processConfig(config: Config): Either[ConfigError, ValidConfig] =
  // implementation

// Required for recursive functions
def factorial(n: Int): Int = if n <= 1 then 1 else n * factorial(n - 1)
```
