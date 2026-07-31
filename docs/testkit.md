# Testkit — Verifying Rendered Output

If you're writing a spec that needs to prove *"the terminal shows the right thing,"* this page is for you. It covers the `testkit/` package: five pieces of test infrastructure that let you render a component tree and assert on the resulting cell grid or the wire bytes, without touching a real terminal.

The page is for contributors to ws-console. It assumes you know Scala 3, ZIO Test, and the render pipeline described in `docs/terminal-architecture.md`. If any of those are new, start there.

## Why testkit exists

The rendering machinery is deterministic and testable, but the natural read surface — `ScreenBuffer.get(x, y): Option[Cell]` — is one coordinate at a time. A full 20×10 layout assertion in that shape is around 200 `get` calls. Six specs each hand-rolled their own 20-method `Terminal` double. The one ANSI decoder that existed was private to a single spec.

Testkit replaces all of that with one shared `Terminal` double, two rendering harnesses, one wire decoder, and a set of readable value-based assertions.

## The pieces

`src/test/scala/testkit/` holds five files. Read the Scaladoc on each — it is the authoritative reference; the summaries below just tell you which piece to reach for.

| Piece                 | What it gives you                                                                                     |
|-----------------------|-------------------------------------------------------------------------------------------------------|
| `CaptureTerminal`     | One in-memory `Terminal` that records every op and every write. Configurable size, events, signals.   |
| `RenderHarness`       | Pure component render — build a `ScreenBuffer`, call `component.render`, return the buffer.           |
| `FrameHarness`        | Integration render — real diff → flush → swap over a `CaptureTerminal`. Exposes both the wire and the drawn buffer. |
| `GridAssertions`      | Value-based assertions over a `ScreenBuffer` — `assertGrid`, `assertCell`, `assertStyle`, `assertChar`. |
| `AnsiGrid`            | Decodes emitted ANSI back into a `Map[(x, y), Cell]` with structurally-parsed style.                  |

## Two flows: pure or integration

Testkit gives you two distinct entry points. Pick the one that matches what you're proving.

### Pure render — for component logic

`RenderHarness.renderToBuffer` calls `component.render` against a fresh buffer and returns it. Nothing else runs — no diff, no flush, no terminal. This is the right tool when the question is *"does this component draw the right cells into its area?"*

The pattern, from `TextSpec.scala`:

```scala
import testkit.GridAssertions.{assertCell, assertGrid}
import testkit.RenderHarness.renderToBuffer

test("centers text within the area width") {
  val buf = renderToBuffer(10, 1)(Text("hi", redStyle, Alignment.Center))
  assertGrid(buf, "....hi....") &&
  assertCell(buf, 4, 0, Cell('h', redStyle))
}
```

Every space in the expected string is the sentinel `.`. The harness renders empty cells as `.` too, so trailing padding on a short row is visible in the literal. If the actual cell holds a literal `.`, the harness fails fast — `.` is indistinguishable from padding in a char-only grid, so assert that cell with `assertCell` or `assertChar` instead. The tradeoff is documented at `RenderHarness.scala` and guarded by `GridAssertionsSpec`.

### Integration render — for the whole pipeline

`FrameHarness.renderFrame` runs the real pipeline: `Frame.run` → diff → `BufferFlusher` → `CaptureTerminal.writeBuilder` → swap. Both the emitted ANSI and the drawn buffer come back:

```scala
test("renderFrame returns the emitted ANSI and the drawn buffer") {
  for
    result <- FrameHarness.renderFrame(Text("hi"), 6, 1)
    (ansi, buffer) = result
  yield assertTrue(
    buffer.glyphGrid == Vector("hi...."),
    ansi.contains("[1;1H"),
    ansi.contains("h")
  )
}
```

Use this when the question is *"did the pipeline emit the right wire output?"* — diff correctness, cursor addressing, multi-frame steady-state behavior. For a multi-frame test, hold the harness (`FrameHarness.make`), render, assert, `clearCaptured`, render again:

```scala
for
  h      <- FrameHarness.make(4, 1)
  _      <- h.run(Row("AAAA"))
  frame1 <- h.captured
  _      <- h.clearCaptured
  _      <- h.run(Row("AAAB"))
  frame2 <- h.captured
yield assertTrue(
  frame1.contains("[1;1H"),      // fresh paint addressed the first column
  frame2.contains("[1;4H"),      // steady-state diff addressed only the changed column
  !frame2.contains("[1;1H"),     // and nothing else
  !frame2.contains("A")
)
```

That second frame is the whole reason `FrameHarness` exists: proving the diff engine is minimal, at the wire level, over a real emission path.

### Decoding the wire

`AnsiGrid.decode(bytes)` walks the emitted ANSI and rebuilds a `Map[(x, y), Cell]`. SGR parameters — Select Graphic Rendition, the ANSI escape family that carries colours and text attributes — are parsed into a `CellStyle` value, so style comparison stays structural. Two uses:

- Assert *"the diff emitted glyph G with style S at (x, y)"* directly against the wire.
- Round the wire back into a `ScreenBuffer` (`AnsiGrid.decodeToBuffer`) and hand it to `assertGrid`.

The decoder models the plain `RenderOp.Cell` grammar (`moveTo + reset + style + glyph`). `ScrollRegionLine` ops emit several glyphs after a single `moveTo` and are not reconstructed — for scroll-region tests, read `drawnBuffer` instead of decoding the wire.

## How this differs from a live run

Testkit runs the same code paths as `sbt run`, minus the terminal. Concretely:

- **No PTY, no raw mode, no alt buffer.** A PTY (pseudo-terminal) is the OS-level device a real terminal emulator drives. `CaptureTerminal.enterRawMode` records the string `"enterRawMode"` into an op log and returns; nothing touches the OS terminal driver.
- **No I/O timing.** `readRaw` returns `RawInput.Timeout` unless you inject an event stream. There is no blocking read.
- **Writes are captured, not emitted.** Every `write` and every `writeBuilder(_).build` lands in a `Ref[Chunk[String]]`; call `captured: UIO[String]` to read them as one concatenated wire stream.
- **Size and capabilities are configurable.** Default is 24×80, TrueColor. Pass a `TerminalSize` and `TerminalCapabilities` to `CaptureTerminal.make` if the code under test cares about either.

The verification story is the point:

- **A live run** produces bytes that go to a real terminal, which paints pixels. You can look at those pixels but you cannot programmatically read them back. Assertions have to happen upstream of the emission.
- **Testkit** captures the same bytes into a string, and — through `FrameHarness` — also gives you the `ScreenBuffer` that produced them. You assert on either or both. The pipeline that runs is the production pipeline; only the terminal at the end is the double.

That's the invariant to protect. If a test needs behavior the harness does not provide, extend the harness rather than substituting a hand-rolled `Terminal` or a fake `Frame`. A double that skips the real diff proves nothing about the diff.

## What goes wrong

Three sharp edges have already caught tests. Read these once.

**KI-001 makes ANSI-string style snapshots flaky.** `CellStyle.toAnsi` iterates `attributes: Set[Attribute]`, and Scala's `Set` iteration order is not stable across JVM runs. Two `==` styles can produce different byte sequences. Testkit never compares style as an ANSI string — `AnsiGrid.decode` rebuilds it into a `CellStyle` value first, and `GridAssertions.assertStyle` compares that value. Follow the same rule in your own specs: never assert on a substring that carries a multi-attribute SGR sequence. Full detail in `docs/known-issues.md`.

**The sentinel collision.** `RenderHarness.glyphGrid` renders empty cells as `.`. If your component draws a literal `.`, the grid representation would be ambiguous, so the harness throws instead. Move that cell to `assertCell` / `assertChar` and keep `assertGrid` for the surrounding layout. `GridAssertionsSpec` guards this behavior.

**The swap trap in `FrameHarness`.** After `render`, the manager rotates the just-drawn `current` into `previous` and blanks the new `current`. Read the drawn frame from `harness.drawnBuffer` (== `previous`), not `currentBuffer`. Reading `currentBuffer` after a render gives you the blank slate the next frame will draw into.

## When not to use testkit

- **Animation timing.** Testkit does not step the clock. If you need to prove *"frame 37 shows this glyph,"* extract a pure `renderFrame(canvas, index)` seam on the panel and call it directly with `index = 37` — that pattern is already in `ProgressBarPanel` and `SpinnerPanel`. If you need to prove *cadence* (how many steps happened in an interval), `TestClock.adjust` is available but the codebase has no precedent for it; document the pattern if you introduce it.
- **Terminal capability negotiation.** `CaptureTerminal` reports fixed capabilities. Testing what `TerminalFactory` decides against a real environment belongs in `TerminalFactorySpec` and `HostSystemSpec`, which drive real host detection.
- **`ScrollRegionLine` wire assertions.** `AnsiGrid.decode` does not reconstruct scroll-region ops. Read the drawn buffer via `FrameHarness.drawnBuffer` — the mirror step keeps `previous` aligned with what would be on screen.

## Where to add a test

Every layer has a test package under `src/test/scala/`:

- Component logic → `component/<Foo>Spec.scala` using `RenderHarness`.
- Pipeline / diff / flush → `buffer/<Foo>Spec.scala` or `render/<Foo>Spec.scala` using `FrameHarness`.
- Wire-level ANSI → decode with `AnsiGrid`, then assert with `assertGrid` or a direct `Map` comparison.

The testkit itself is tested — `AnsiGridSpec`, `FrameHarnessSpec`, and `GridAssertionsSpec` are the reference for how the primitives compose. Read those before writing anything unusual.
