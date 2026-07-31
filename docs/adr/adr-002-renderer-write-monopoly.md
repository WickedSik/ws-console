# ADR-002: Renderer Write-Monopoly

**Status**: Accepted  
**Implementation**: Partial  
**Date**: 2026-06-18  
**Deciders**: ws-console core  
**Supersedes**: nothing (new invariant)  
**Builds on**: ADR-001 (`docs/adr-001-render-context.md`)  
**Source ADT**: rendering/invalidation rewrite (ratified decisions 1 + 2)

---

## Implementation Status

`Implementation: Partial`. The policy is substantially observed; the enforcement
mechanism and the cleanup decisions are not built. Verified 2026-07-20.

**In the code (enforceable):**

- No component, container, layout, panel, widget, or `RawCanvas` writes to the terminal.
  Writes are confined to `buffer/Frame.scala` and the four lifecycle acquisitions in
  `app/Application.scala:128-139`.
- The read/write classification of the `Terminal` surface (18 write members, 4 read
  members) is accurate against the trait as it stands.
- The `restoreState` cleanup path exists as a post-render failsafe in
  `terminal/AnsiTerminal.scala`, fired from `TerminalFactory`'s outer scoped release.

**Not built (not enforceable — see `.claude/tasks/JUDGEMENT-adr-002-write-monopoly-implementation.md`):**

- `WriteAuthority` does not exist. The monopoly has no compile-time enforcement; every
  write member remains publicly reachable by anything holding `Terminal`.
- `TerminalCleanupAuthority` does not exist; `restoreState` is unguarded.
- `setScrollRegion` / `resetScrollRegion` are still on the trait and its companion
  (`terminal/Terminal.scala:79-83`, `:166-171`).
- `withAlternateBuffer` / `withHiddenCursor` / `withRawMode` are still present
  (`terminal/Terminal.scala:200-227`).
- `SpinnerPanel` and `ProgressBarPanel` still drive their own flush cadence, breaching
  the monopoly in practice.
- The yield/reclaim protocol for full-screen subprocess invocation is policy only; the
  scenario does not exist in the codebase.

The Q2 cleanup block below records these as *ratified decisions*, not as completed work.
Read its present-tense phrasing ("they are retired", "they are removed") as decision
language, not as a description of the tree.

## Context

ws-console owns the entire screen. It runs in the alternate buffer, hides the
cursor, disables line wrap, and positions every cell explicitly. There are no
fallback code paths and no shared ownership of the terminal with the host
application. Total ownership is the project's foundational policy.

Today, that ownership is asserted by convention, not enforced. The terminal
write surface is the public `Terminal` trait (`src/main/scala/terminal/Terminal.scala`),
and every method on it — `write`, `writeBuilder`, `flush`, `moveCursor`,
`clearScreen`, `setScrollRegion`, `enterAlternateBuffer` — is reachable by
**anything** that has the `Terminal` service in its environment. A component, a
panel lifecycle hook, an application `onEvent` callback, or a demo panel can all
acquire `Terminal` and emit bytes directly. Nothing in the type system stops
them. The render loop *also* writes, through `Frame.render`. So the screen has
several independent authors and no single source of truth for "what is on the
display right now."

This is the structural cause behind a class of bugs that has cost the project
real diagnostic time.

### Empirical motivation

**`demo-toolbar-disappearance.md` — model-vs-reality drift.** On the first panel
swap, the toolbar vanished. The investigation proved, at the byte level, that
the buffer's `previous` correctly held the toolbar cells and the diff correctly
skipped them as unchanged — yet the terminal had lost them. The buffer's model
of the screen and the terminal's actual display had drifted apart, and *the
application could not detect or correct this from inside the buffer/diff
abstraction*. The root terminal-side mechanism was never proven (H8/DECAWM was
refuted; H9/H13 remain untested). The shipped fix is an admitted policy
band-aid: `requestRefresh` re-blasts the whole frame on every swap. The drift is
masked, not eliminated, and the unproven root cause is still a landmine.

The deeper lesson: a single writer cannot drift against itself. If the renderer
is the **only** author of terminal bytes, and every event that mutates terminal
state outside the cell model (alt-buffer entry, resize, scroll-region install,
cursor side effects, subprocess ANSI) is forced to announce itself through one
channel, then "the buffer thinks the screen says X, but something else wrote Y"
becomes either impossible-by-construction or explicitly accounted for. The
unproven root cause stops being a landmine because there is no second writer to
be the culprit.

**`demo-focus-flicker.md` — uncoordinated writes.** Tab mutated focus but
scheduled no frame; the visible flicker came from `requestFullRedraw` +
`\e[2J`. The fix wired focus mutations to auto-schedule a redraw. The takeaway
that motivates *this* ADR: a screen with one writer that consumes a single
redraw signal is far easier to reason about than several writers each deciding
independently when and what to emit.

### The fossil record

`RenderLoop` currently exposes three escalating "hammers" — `requestRedraw`,
`requestRefresh` (invalidate the diff baseline, no clear), and
`requestFullRedraw` (invalidate + `\e[2J`) — backed by `Frame.invalidate` and
`Frame.clearScreen`. These exist *because there is no invalidation model*. When
you cannot say "this region is stale," your only tools are "redraw everything"
and "nuke the screen." Three hammers are the symptom; the disease is the absence
of a single, structured way to request a repaint. This ADR establishes the
precondition for curing it: a single writer, reached through a single channel.
The taxonomy that replaces the hammers is ADR-003. This ADR establishes *who is
allowed to write at all*.

This decision is the write-authority half. ADR-003 is the mechanism half.

## Decision

**The renderer is the sole writer to the terminal. This is a permanent,
immutable invariant.**

1. **MUST**: Only the renderer (the Layer 6 render loop and the Layer 2 `Frame`
   flush it drives) may emit bytes to the terminal — cells, cursor moves,
   screen clears, scroll-region installs, mode changes, every byte.

2. **MUST NOT**: No component, container, layout, panel, panel lifecycle hook,
   application callback, widget, or demo panel may write to the terminal
   directly. The terminal write surface is unreachable from any of these.

3. **MUST**: The *only* way any entity may affect the screen is by **marking
   itself invalidated** — requesting a repaint through the invalidation channel.
   Components invalidate themselves. Layouts invalidate themselves when the tree
   changes. Anything else that needs the screen to change raises an invalidation.
   The renderer alone consumes invalidations and writes.

4. **MUST**: Every terminal-state mutation that is *not* a cell write — entering
   the alternate buffer, resize handling, installing a scroll region, a
   subprocess emitting its own ANSI — is routed through the invalidation channel
   so the single writer remains the single source of truth. (The taxonomy of
   these is ADR-003; the requirement that they go through one channel is here.)

This invariant does not change. It is not a default to be overridden, not a
policy with an escape hatch. A consumer that believes it needs to write to the
terminal directly is, by the terms of this decision, mistaken — it needs to
invalidate a region and let the renderer paint.

### Enforcement

Convention is insufficient; the toolbar-disappearance investigation is the proof
that "everyone agrees only the renderer should write" does not prevent the
problem when the type system permits otherwise. Enforcement MUST be
compile-time. Three mechanisms were evaluated.

The illustrative signatures below are **not** an implementation mandate — they
show the *shape* of each option for trade-off comparison. The implementing task
chooses and ratifies the concrete form.

#### Option A — Capability witness (recommended)

Split the `Terminal` trait into a **read/query surface** (size, capabilities,
the `events` stream, raw input) that is freely available, and a **write surface**
(`write`, `writeBuilder`, `flush`, `moveCursor`, `clearScreen`,
`setScrollRegion`, the mode toggles) whose methods require an unforgeable
capability value the framework hands *only* to the renderer.

```scala
// ILLUSTRATIVE — not an implementation mandate
final class WriteAuthority private[render] ()

trait Terminal:
  def size: IO[IOException, TerminalSize]
  def events: ZStream[Any, IOException, Event]
  // ... read surface, unrestricted ...

  // write surface — every method demands the witness
  def writeBuilder(builder: AnsiBuilder)(using WriteAuthority): IO[IOException, Unit]
  def clearScreen(using WriteAuthority): IO[IOException, Unit]
```

`WriteAuthority`'s constructor is `private[render]`, so only code in the render
package can mint one. The render loop holds the single instance and threads it
into `Frame.render`. A component cannot call `writeBuilder` because it cannot
produce a `WriteAuthority` and cannot summon one as a `given` — none is in
scope outside the renderer.

- **Pros**: Compile-time. The write surface stays on one cohesive trait. The
  capability is a value, so it composes with ZIO naturally (threaded, not
  ambient). Reads stay unrestricted, which matches reality — components legitimately
  query `size`. Honest at the call site: a method that needs authority says so in
  its signature.
- **Cons**: Every write call site gains a `(using WriteAuthority)`. The witness
  must be plumbed from the renderer down into `Frame`. One new concept for
  contributors to learn.

#### Option B — Package-private write surface + sealed boundary

Move the write methods off the public `Terminal` trait onto a `private[render]`
(or `private[buffer]`) sub-interface that only the render package can name. The
public `Terminal` exposes reads only.

```scala
// ILLUSTRATIVE
trait Terminal:               // public — reads only
  def size: IO[IOException, TerminalSize]

private[render] trait TerminalWrite extends Terminal:
  def writeBuilder(builder: AnsiBuilder): IO[IOException, Unit]
```

- **Pros**: Compile-time, zero call-site ceremony — no `using` to thread. The
  visibility modifier *is* the enforcement.
- **Cons**: Package-private is coarse. Everything that legitimately writes must
  live in (or be granted access from) one package, which couples `Frame` (Layer
  2) and the render loop (Layer 6) into a shared visibility scope and may force
  package gymnastics. Less granular than a value you can hand to exactly one
  object. The `events`-stream and ZLayer wiring that constructs the terminal
  need care to not leak the write subtype.

#### Option C — ZLayer scoping (rejected as primary)

Provide the write-capable `Terminal` only in the environment of the render
loop's effect; provide a read-only `Terminal` everywhere else.

- **Pros**: Idiomatic ZIO; no trait surgery.
- **Cons**: This is **runtime** enforcement, not compile-time. A component that
  writes against a read-only environment fails to *resolve* the service, but only
  when that code path runs — exactly the "silent until it bites you" failure mode
  ADR-001 warned about. The user's demand is enforcement, and "enforcement" here
  means "the compiler rejects the program," not "the effect dies at runtime if
  reached." Rejected as the primary mechanism; usable as defense-in-depth on top
  of A.

#### Recommendation

**Option A (capability witness), optionally backed by C for defense-in-depth.**
A is compile-time, granular (exactly the write methods, exactly the renderer),
and keeps the read surface honestly open. Its call-site cost is the price of the
invariant being real instead of aspirational. B is a viable fallback if witness
plumbing proves too invasive, accepting coarser granularity. C alone does not
satisfy "must be enforced."

## Alternatives Considered

### A. Status quo — convention only

**Rejected.** This is what produced the toolbar-disappearance class. "Only the
renderer should write" is already the intended rule; nothing enforces it, and the
buffer/terminal drift is the result. An invariant the user calls permanent and
immutable cannot rest on reviewer vigilance.

### B. A lint rule / reviewer checklist

**Rejected.** Same failure mode as ADR-001's per-component cache: drift is
silent, and a human gate is the thing that fails. Compile-time is available
(Option A); a weaker mechanism is not justified.

### C. Make `Frame` the monopoly instead of the render loop

**Rejected as the framing, accepted as a detail.** `Frame.render` is *part of*
the renderer — it is the Layer 2 flush the loop drives. The monopoly belongs to
"the renderer" as a whole (loop + the `Frame` it owns), not to `Frame` in
isolation, because `Frame` alone cannot see invalidations or schedule frames.
The capability witness is held by the loop and threaded into `Frame`, which keeps
the authority single while letting the flush live where it does.

## Consequences

### Positive

- **Model-vs-reality drift becomes structurally impossible or explicit.** With
  one writer, the buffer *is* the screen — there is no second author to silently
  contradict it. The unproven toolbar-disappearance root cause stops being a
  landmine: any non-cell terminal mutation must now announce itself, so a class of
  "the terminal lost cells we thought it had" failures is closed by construction.
- **The buffer becomes the authoritative model, not just a drawing surface.**
  This is the precondition for ADR-003 treating the cell-diff as the collapsing
  mechanism's trustworthy substrate.
- **Three hammers collapse to one channel.** Once nothing else can write,
  `requestRedraw` / `requestRefresh` / `requestFullRedraw` lose their reason to
  exist as separate escalations. ADR-003 replaces them with a single invalidation
  mechanism plus a rare corruption-reset.
- **The contract becomes honest.** A method that writes to the terminal says so
  in its type (Option A). "Who can change the screen?" has a one-word answer the
  compiler enforces.
- **Consumers get a simpler mental model.** Downstream widget authors never reach
  for the terminal. They invalidate. There is exactly one thing to learn.

### Negative

- **Enforcement friction.** Option A threads a `WriteAuthority` through every
  write call site and from the renderer into `Frame`. This is real plumbing cost
  and a new concept for contributors.
- **The read/write split must be drawn correctly.** Some methods are ambiguous
  (does `flush` belong to writes? yes). Mis-categorizing a method either leaks
  write authority or needlessly restricts a read. The split is a one-time design
  cost that must be gotten right.
- **Every non-cell terminal mutation now needs a route through invalidation.**
  Scroll-region install, alt-buffer entry, and subprocess ANSI can no longer "just
  write." They must raise an invalidation the renderer honors. That is more
  machinery than a direct write — it is the cost of the single-writer guarantee,
  and it is paid in ADR-003's external/model-invalidating source.
- **Lifecycle writes need a home.** `enterAlternateBuffer`, `disableLineWrap`,
  and friends run at application setup/teardown, outside any frame. They are still
  terminal writes. `RenderLoop.make` mints the single `WriteAuthority` and returns
  it to `Application.run`, which receives it and uses it for the four lifecycle
  acquisitions (alt-buffer entry, line-wrap disable, cursor hide, raw-mode entry)
  and threads it into `loop.start`. No lifecycle code relocates; the four acquisitions
  stay in `Application.run` where they already live. This keeps the authority single
  with one mint point in the render package, and the monopoly intact.

### Neutral

- **The `Terminal` trait gains structure** (read surface vs. write surface). The
  ZLayer that constructs the terminal and the `events` accessor need to expose the
  read surface broadly while keeping the write surface narrow.

## What is NOT in this decision

- **The invalidation taxonomy and the collapsing diff.** That is ADR-003. This
  ADR establishes *that* invalidation is the only channel and *that* the renderer
  is the only writer; ADR-003 defines *what kinds* of invalidation exist and how
  they coalesce.
- **Event routing / dispatch ownership.** Which component receives a keypress is a
  separate axis (`demo-toolbar-shortcut-ownership.md`). It is not rendering and is
  out of scope here.
- **The concrete enforcement form.** Option A is recommended, but the exact
  `WriteAuthority` shape, where the witness is minted, and how it threads into
  `Frame` are ratified by the implementing task.

## Open Questions

- **Q1 — Where do lifecycle writes sit?** 

  **Resolved.** `RenderLoop.make` (in package `render`) mints the single `WriteAuthority` and returns it to `Application.run` (package `app`), which receives it as a value and uses it for the four lifecycle acquisitions (alt-buffer entry, line-wrap disable, cursor hide, raw-mode entry) before threading the same authority into `loop.start`, which carries it down into `Frame.render`. No lifecycle code relocates; the four acquisitions remain in `Application.run` where they live today. This satisfies the monopoly invariant: one authority, one mint point in the render package, one legitimate receiver in the application layer.
  
  **Sub-resolution: `restoreState` cleanup bypass.** `AnsiTerminal.restoreState` is a JVM-level failsafe (resetting scroll region, enabling line wrap, showing cursor, exiting raw mode) fired from `TerminalFactory`'s outer `ZLayer.scoped` release, after the render loop and its `WriteAuthority` have ceased to exist. It is granted a narrow, dedicated cleanup bypass — a separate, small capability (e.g. a `TerminalCleanupAuthority`) minted in the `terminal` package and accepted only by `restoreState`. This is not a second writer in practice: by the time `restoreState` fires, no frames are being written. It is an exception only in the type system — a post-render failsafe — and this distinction must be stated so the bypass is never widened or mistaken for a sanctioned alternative write path.

- **Q2 — Read/write line for `flush` and mode toggles.**
  
  **Resolved.** Complete method-by-method classification of the `Terminal` trait confirms: **Write surface (18 methods):** `enterRawMode`, `exitRawMode`, `enterAlternateBuffer`, `exitAlternateBuffer`, `disableLineWrap`, `enableLineWrap`, `moveCursor`, `hideCursor`, `showCursor`, `saveCursor`, `restoreCursor`, `clearScreen`, `clearLine`, `setScrollRegion`, `resetScrollRegion`, `write`, `writeBuilder`, `flush`. **Read surface (4 members):** `readRaw`, `events`, `size`, `capabilities`. The rule: Lifecycle, Cursor, Screen, Scroll, and Output groupings are writes; Input and Info are reads.
  
  **Key findings:** `flush` is a write (forces buffered bytes to the device, guarded by write-lock); `enterRawMode`/`exitRawMode` are writes (mutate kernel tty state via `stty`, even without byte output); `size` is a clean read (uses `stty size`/`tput` on separate `/dev/tty` descriptor, not the output stream); no method requires splitting (every member is cleanly pure-read or pure-write); no write-to-read query entanglement exists (Device Status Report pattern absent).
  
  **Cleanup decisions ratified and recorded here:**
  
  **Retire `setScrollRegion` / `resetScrollRegion` from the `Terminal` trait.** They are dead on the active path — all scroll-region ANSI flows through `writeBuilder(BufferFlusher.toAnsi(ops))` (Canvas → ScreenBuffer → diff → RenderOp → Frame), never via trait methods. The trait-level methods could surface incorrect information; they are retired rather than kept.
  
  **Remove companion-object resource helpers** (`withAlternateBuffer`, `withHiddenCursor`, `withRawMode`). They are dead code with zero callers (`Application.run` uses manual `ZIO.acquireRelease`). Making them public `WriteAuthority`-bearing helpers would surface the write surface from the companion; they are removed.

- **Q3 — Subprocess ANSI.**
  
  **Resolved.** The write-monopoly forbids raw terminal handoff during normal operation. A consumer needing subprocess output in its display must capture the subprocess stdout and route it through the cell model (log widget, scrolling-text component). The sole sanctioned exception is a bounded, explicit, opt-in **yield/reclaim protocol** reserved for full-screen subprocess invocation (`$EDITOR`, `less`, `fzf`, or equivalent): (i) suspend the render loop, (ii) exit the framework's terminal state (alt-buffer, raw mode, cursor), (iii) run the subprocess to completion, (iv) re-enter the framework's terminal state, (v) raise a whole-viewport external invalidation through ADR-003's source 5, plus a corruption-reset if the subprocess may have scrambled arbitrary cells. The yield protocol is the only sanctioned path by which a non-renderer entity transiently becomes sole writer. **Reachability finding:** the scenario does not exist in the codebase today (only process executions — `stty`/`tput` in `HostSystem` — are out-of-band: their output is captured into the JVM, never written to the display), but it is reachable today through the `Panel.onMount`/`onUnload`/`onRemount` hooks, which carry `Terminal` in scope; this OS-level bypass is why Q3 is resolved with policy rather than deferred. **ADR division:** ADR-002 owns permission/policy (is a handoff sanctioned, and under what terms); ADR-003 owns the repaint contract (what invalidation the return raises) — its source 5 and Q4 already anticipate this. **Deferred to implementing task (one terse line):** the concrete yield API shape, whether the render loop pauses/resumes or stops/restarts, and the corruption-reset threshold.

## References

- Builds on: `docs/adr-001-render-context.md`
- Bug evidence: `.claude/tasks/demo-toolbar-disappearance.md`,
  `.claude/tasks/demo-focus-flicker.md`
- Scoped out: `.claude/tasks/demo-toolbar-shortcut-ownership.md` (event routing)
- Current machinery:
  - `src/main/scala/terminal/Terminal.scala` (the write surface to constrain)
  - `src/main/scala/render/RenderLoop.scala` (the three hammers; the future sole writer)
  - `src/main/scala/buffer/Frame.scala` (the flush the renderer drives)
  - `src/main/scala/render/RenderPipeline.scala`, `render/Renderer.scala`
