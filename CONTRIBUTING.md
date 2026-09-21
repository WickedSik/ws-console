# Contributing to ws-console

ws-console is a small, opinionated library with a deliberate architectural spine. Contributions are welcome; PRs should respect the layer boundaries and design decisions already in place. The library is built for downstream consumers — REPL panes, log widgets, file viewers, dashboards — not for the demo panels alone.

## Before you open a PR

Read the relevant public documentation for the area you're touching:

- **`docs/component-styleguide.md`** — if you're adding or modifying a component
- **`docs/event-handling.md`** — if you're working on input handling or event dispatch
- **`docs/testkit.md`** — when writing specs for visual or integration behavior
- **`docs/known-issues.md`** — check for anything that might already be tracked

Reflect what is already there. New code should match existing patterns in style, layer placement, and abstraction level. If your change touches multiple layers or restructures a subsystem, open an issue first to agree on scope and placement before writing code. Small bugfixes and single-layer additions can go straight to PR.

## Development environment

**Requirements:**

- JDK 11 or later
- Scala 3.3.6
- sbt 1.10.11

**Setup:**

```bash
git clone https://github.com/wickedsik/ws-console.git
cd ws-console
sbt compile
sbt test
```

**Running the demo:**

```bash
scripts/run-demo.sh
```

The demo is the reference implementation of all library features. Every new capability ships with a demo panel or migrates an existing one.

**Important:** Never launch the TUI with `sbt run`. The sbt process shares the controlling terminal with the program and injects control sequences that corrupt rendering. This isn't a bug in the library; it's a terminal-sharing problem between two processes. Use the script above instead. For debug logging, set `WS_CONSOLE_DEBUG_LOG=/path/to/log` before launching — every terminal operation is mirrored to that file.

## Architectural expectations

ws-console is organized into seven layers. Each layer depends only on layers below it; lower layers never depend on higher ones.

| Layer | Name | Description |
|-------|------|---|
| 1 | Core Terminal | Terminal trait, ANSI escape primitives, raw input handling, terminal capabilities |
| 2 | Buffer & Cells | Double-buffered cell grid, differential rendering, frame-per-cell primitives |
| 3 | Layout | Pure constraint solver for positioning and sizing components |
| 4 | Components | Renderable component model (Text, Panel, Button, containers) |
| 5 | Events | Key event parsing, event stream, typed event surface |
| 6 | Rendering | Orchestration layer — rendering loop, layout, dispatch, focus management |
| 7 | Application | Application envelope, state management, panel hosting |

**Rules reviewers will hold contributions to:**

- **Which layer belongs this change in?** A new primitive goes in the lowest layer that can express it. A new component goes in Layer 4, not hardcoded into the renderer.

- **Layer integrity over demo convenience.** New capabilities are designed for downstream consumers — applications that need REPL panes, dashboards, build output streams. The demo is a *presentation* of those capabilities, not the audience. If a capability serves demo panels today but a planned widget category tomorrow, design it for the widget category. Escape hatches that let a demo skip a layer to avoid work are rejected.

- **Every feature ships a demo.** New capabilities are accompanied by either a new demo panel in `src/main/scala/demo/panels/` or the migration of an existing panel onto the new abstraction. This keeps the demo set in sync with library surface area.

- **The `Csi` invariant.** No raw NUL (`0x00`) or ESC (`0x1B`) bytes may appear outside `src/main/scala/ansi/Csi.scala`. Use `Csi.ESC` (String) or `Csi.EscChar` (Char). The build enforces this via `ControlByteHygieneSpec`.

- **Patterns every contribution inherits:**
  - Immediate-mode rendering: components redraw from scratch every frame; buffer diffing keeps I/O minimal.
  - Component isolation: components render into a clipped canvas and have no direct access to siblings.
  - Event bubbling with consumption: focused component → parent chain → application; `EventResult.Consumed` halts propagation.
  - Resource safety: all terminal resources (alt buffer, raw mode, cursor visibility) release on every exit path via `ZIO.acquireRelease` inside `ZIO.scoped`.

## Coding standards

The codebase is Scala 3.3.6 throughout. Code style is defined by `.scalafmt.conf` at the project root.

- **Scala 3 indentation:** Prefer `:`-indented syntax for class, trait, object, enum, and def bodies over brace blocks.
  ```scala
  // Preferred
  trait Terminal:
    def write(text: String): IO[IOException, Unit]

  // Avoid
  trait Terminal {
    def write(text: String): IO[IOException, Unit]
  }
  ```

- **Formatting:** Run `scalafmt` before pushing. sbt will verify with `sbt scalafmtCheckAll` in CI.
  ```bash
  sbt scalafmtAll      # Format main + test sources
  sbt scalafmtSbt      # Format build.sbt and project/*.sbt
  ```

- **When in doubt:** Match the surrounding code. Consistency within the project outweighs style preferences. The codebase is internally consistent; your code should reflect what is already there.

## Testing expectations

The project uses ZIO Test. All specs extend `ZIOSpecDefault` and live in `src/test/scala/` mirroring the `src/main/scala/` layout.

**Before you push:**

```bash
sbt test              # All tests must pass
sbt testQuick         # Run only previously-failed tests
sbt "testOnly *FooSpec"  # Run a specific spec by pattern
```

**Visual and integration assertions:**

Prefer harness-based assertions from `testkit/` over raw ANSI string matching. See `docs/testkit.md` for the full guide and utilities:

- `CaptureTerminal` — in-memory Terminal double that records output
- `RenderHarness.renderToBuffer` — pure component render into a buffer
- `FrameHarness` — full pipeline render with diff → flush → swap
- `AnsiGrid` — decode ANSI bytes back into a cell grid with parsed style
- `GridAssertions` — value-based assertions on grid content

```scala
test("centers text within the area width") {
  val buf = renderToBuffer(10, 1)(Text("hi", redStyle, Alignment.Center))
  assertGrid(buf, "....hi....") &&
  assertCell(buf, 4, 0, Cell('h', redStyle))
}
```

**Coverage expectations:**

- New public API needs a spec.
- New demo panels do not require specs; the primitives they exercise do.
- Cover edge cases (empty, whitespace-only, extreme widths), Unicode (Chinese, Cyrillic, emoji), and round-trips (input → transform → output).

## Pull requests

Keep PRs focused. Split unrelated changes into separate PRs.

**In the PR description:**

- State the *why*: what problem is being solved, what alternatives were considered.
- Reference the issue the PR addresses.
- Call out the first thing the reviewer should look at.

**Size matters:**

- A 400-line PR gets careful review.
- A 4000-line PR does not.

Prefer smaller, focused PRs to large omnibus ones. If your change is large, break it into staged PRs that each land independently.

## Reporting bugs

Bug reports that reproduce only under `sbt run` will be closed without further comment. The demo must be launched via `scripts/run-demo.sh`; running the TUI under sbt corrupts rendering in ways that are not the library's responsibility to fix.

When reporting a bug, include:

- **Terminal emulator and OS:** e.g., iTerm2 on macOS 14.1, GNOME Terminal on Ubuntu 22.04
- **JDK version:** e.g., OpenJDK 21
- **Minimal reproducer:** the smallest program or demo state that shows the bug
- **Expected vs observed behavior:** what you expected to see, what you actually saw
- **For rendering/visual bugs:** a raw pty capture (`script`) is more useful than a screenshot. The debug log alone can miss foreign writers on the TTY. See `WS_CONSOLE_DEBUG_LOG` in `scripts/run-demo.sh`.

Before reporting, check `docs/known-issues.md` for anything already tracked.

## License

Contributions are licensed under the GNU General Public License v3, the same terms as the project. See `LICENSE` for details. There is no DCO or sign-off requirement; opening a PR constitutes agreement to license your contribution.
