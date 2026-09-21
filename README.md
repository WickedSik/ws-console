# ws-console

A Scala 3 and ZIO-native library for building rich terminal interfaces (TUIs) on modern interactive terminals. ws-console exposes the full capabilities of ANSI escape codes for colorized output, layout, components, event handling, and differential rendering.

## What is ws-console?

ws-console is for building terminal applications that need sophisticated layouts, interactive components, and real-time updates. Think REPL panes, log viewers, file explorers, paged scrollback, build output streams, dashboards, and status displays. The library handles the hard parts: terminal mode management, event parsing, cursor positioning, differential rendering, and component lifecycle.

## Terminal Support

ws-console targets modern interactive terminals only. No fallback code paths.

**Supported:**
- macOS: Terminal.app, iTerm2
- Linux: GNOME Terminal, Konsole, Alacritty, Kitty
- Windows: Windows Terminal (not cmd.exe)
- IDE terminals: VS Code, JetBrains

**Not supported (explicit error on unsupported terminal):**
- cmd.exe, legacy PowerShell, dumb terminals, raw console, CI/CD piped environments
- Terminals without Unicode or 256-color support
- Non-interactive I/O (pipes, redirection)

This is a deliberate design choice to keep the library focused and avoid compatibility sprawl.

## Getting Started

### Run the Demo

The best way to see what ws-console does is to run the demo application:

```bash
scripts/run-demo.sh
```

The demo showcases all library features. Every new feature ships with a demo panel, so you'll always find up-to-date examples in `src/main/scala/demo/`.

**Important:** Never use `sbt run` to launch the TUI. sbt corrupts the terminal by injecting control sequences. Use the script instead.

### Build and Test

```bash
# Compile
sbt compile

# Run tests
sbt test

# Continuous compilation during development
sbt ~compile
```

## Documentation

- **`docs/testkit.md`** — Testing guide for contributors. How to write visual and integration specs using `CaptureTerminal`, `FrameHarness`, `RenderHarness`, and cell grid assertions.
- **`docs/known-issues.md`** — Latent issues that have been surfaced but aren't currently scheduled for fix.
- **`docs/event-handling.md`** — Complete reference for the event layer: how events reach components, what components may respond, and who acts on the response.
- **`docs/component-styleguide.md`** — Authority on visual styling across components: style primitives, color grades, attributes, and how each component expresses state through appearance.

## Architecture

ws-console is organized into seven layers, each with a specific responsibility:

| Layer | Name | Responsibility |
|-------|------|---|
| 1 | Core Terminal | Terminal abstraction, ANSI primitives, raw input, terminal capabilities |
| 2 | Buffer & Cells | Double-buffered cell grid, differential rendering, box drawing |
| 3 | Layout | Pure constraint solver for component positioning and sizing |
| 4 | Components | Renderable component model (Text, Panel, Button, containers, etc.) |
| 5 | Events | Input event parsing and typed event stream |
| 6 | Rendering | Orchestration: diff, flush, dispatch, focus management, animations |
| 7 | Application | Application envelope, state management, panel hosting |

Each layer builds on the previous one. Components render to a clipped canvas. The renderer handles events, focus, animations, and efficient I/O via buffer diffing.

### Key Principles

- **Immediate-mode rendering**: Components redraw from scratch every frame; buffer diffing keeps terminal I/O minimal.
- **Component isolation**: Components render into their allocated area via a clipped canvas. No direct sibling access; communication is via events and shared state.
- **Event bubbling**: Focused component → parent chain → application. `EventResult.Consumed` halts propagation; `Ignored` bubbles.
- **Resource safety**: All terminal resources (alt buffer, raw mode, cursor visibility) release on every exit path, including interruption.

## Project Layout

```
src/main/scala/
├── ansi/       Layer 1: ANSI primitives (colors, styles, control sequences)
├── terminal/   Layer 1: Terminal abstraction and capabilities
├── buffer/     Layer 2: Cell grid, double-buffer, differential rendering
├── geometry/   Utility: Rectangle type
├── layout/     Layer 3: Pure constraint-based layout solver
├── component/  Layer 4: Component model and built-in components
├── event/      Layer 5: Input events and event parser
├── render/     Layer 6: Rendering pipeline and orchestration
├── app/        Layer 7: Application framework and state
├── unicode/    Box drawing constants
├── demo/       Demo application showcasing all features
└── Main.scala  Entry point (wires TerminalFactory with DebugTerminal)

src/test/scala/
├── (mirrors src/main layout)
└── testkit/    Shared test infrastructure
```

## Testing

ws-console uses the ZIO Test framework. All 51+ spec files follow the same pattern and cover every shipped layer.

```bash
sbt test                    # Run all tests
sbt "testOnly *FooSpec"     # Run a single spec by pattern
sbt testQuick               # Run only previously-failed tests
```

### Test Infrastructure

The `testkit/` package provides shared test utilities:

- **`CaptureTerminal`** — In-memory Terminal double that records all output.
- **`RenderHarness`** / **`FrameHarness`** — Render a component or full pipeline against `CaptureTerminal`.
- **`AnsiGrid`** — Decode emitted ANSI bytes back into a cell grid with parsed style.
- **`GridAssertions`** — Value-based assertions on grid content, cells, and style.

See `docs/testkit.md` for the full guide.

## License

Licensed under the GNU General Public License v3. See `LICENSE` for details.
