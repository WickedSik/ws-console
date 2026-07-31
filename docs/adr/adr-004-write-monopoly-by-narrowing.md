# ADR-004: Write-Monopoly by Capability Narrowing

**Status**: Accepted  
**Implementation**: Partial  
**Date**: 2026-07-31  
**Deciders**: ws-console core  
**Supersedes**: ADR-002 (`docs/adr/adr-002-renderer-write-monopoly.md`)  
**Builds on**: ADR-001 (`docs/adr/adr-001-render-context.md`)

---

## Implementation Status

`Implementation: Partial`. The dead-surface retirement has shipped; the narrowing
has not. Verified 2026-07-31 against the tree, not against an earlier record.

**In the code:**

- The renderer is in fact the sole writer. Terminal writes occur only in
  `buffer/Frame.scala` (the flush the loop drives) and the four lifecycle
  acquisitions in `app/Application.scala:167-170`.
- `setScrollRegion` / `resetScrollRegion` are gone from the `Terminal` trait and
  its companion. `AnsiTerminal.restoreState` emits the reset directly; the
  `DebugTerminal` and `CaptureTerminal` mirrors are gone.
- `withAlternateBuffer` / `withHiddenCursor` / `withRawMode` are gone from the
  companion. `TerminalResourceSpec` is deleted with them — its 9 tests exercised
  only those helpers, and `ApplicationSpec` already covers acquire, ordered
  release, and release-on-interruption on the real path.
- No component, container, layout, widget, panel hook or application callback
  writes to the terminal.

**Not built:**

- The narrowing itself. `Panel.onMount` / `onUnload` / `onRemount` / `onRawEvent`,
  `PanelHost.push` / `pop` / `replace`, and `Application.run`'s `onEvent` /
  `onRawEvent` still declare `ZIO[Terminal & Frame, ...]`.

**Explicitly not built, and not to be built:**

- `WriteAuthority`. `TerminalCleanupAuthority`. See the Decision below.

## Context

ADR-002 established the write-monopoly and recommended enforcing it with an
unforgeable capability witness (its Option A). The policy was right. The
mechanism was chosen against a picture of the codebase that measurement does not
support, and against a motivating bug that has since been diagnosed.

Three things are now known that were not known on 2026-06-18.

**1. The motivating bug had an out-of-process cause.** ADR-002 opens with
`demo-toolbar-disappearance.md` and argues that "a single writer cannot drift
against itself." The second writer turned out to be `sbt`, which shares the
controlling terminal with anything it runs unforked and appends `ED 0` after our
frame writes. A Scala capability cannot constrain another OS process. **The
recommended enforcement would not have prevented the bug cited as its primary
justification.** This is not a criticism of ADR-002 — the cause was genuinely
unknown when it was written, and the ADR says so plainly. It is a fact that
changes the cost/benefit.

**2. The one real in-process breach was fixed without enforcement.**
ADR-002 recorded `SpinnerPanel` and `ProgressBarPanel` driving their own flush
cadence as an active violation. Both now call `Application.requestRedraw` and
have since `6ac841f`. The monopoly was restored by convention and has held.

**3. Most of the MUST NOT list is already structurally incapable.** ADR-002
forbids writes from "component, container, layout, panel, panel lifecycle hook,
application callback, widget, or demo panel." `Component` exposes exactly
`render(area, canvas, ctx): Unit`, `childLayouts(area)`, and
`handleEvent(event, ctx): EventResult` — pure, synchronous, no ZIO environment.
Components, containers, layouts and widgets **cannot reach `Terminal` at all**.
ADR-001 closed that half of the list without a capability type.

The genuine exposure is one thing: `ZIO[Terminal & Frame, ...]` in the Layer 7
consumer surface. And nothing uses it. Every demo panel declaring that type does
so to match an override signature; the bodies call `requestRedraw`,
`clearBounds`, `fiberRef`, `state`. Across `app/` and `demo/` the only real uses
of `Terminal` are `Application.run`'s four lifecycle acquisitions and
`DemoUtils:66`'s `Terminal.events` — a read.

`Terminal` sits in those signatures as vestigial breadth.

## Decision

**The renderer remains the sole writer to the terminal. The monopoly is enforced
by not handing out the capability, not by guarding it.**

1. **MUST** (unchanged from ADR-002): only the renderer — the Layer 6 loop and
   the Layer 2 `Frame` flush it drives — emits bytes to the terminal. The four
   lifecycle acquisitions in `Application.run` are part of that boundary.

2. **MUST NOT** (unchanged): no component, container, layout, panel, panel
   lifecycle hook, application callback, widget or demo panel writes to the
   terminal.

3. **`WriteAuthority` and `TerminalCleanupAuthority` are not built.** ADR-002's
   Option A is withdrawn. So is the `restoreState` cleanup bypass it required,
   which existed only to carve an exception out of a mechanism that no longer
   exists.

4. **Enforcement is by narrowing.** Consumer-facing signatures that do not need
   `Terminal` do not receive it. `Panel`'s four hooks, `PanelHost`'s three stack
   operations, and `Application.run`'s `onEvent` / `onRawEvent` parameters narrow
   from `ZIO[Terminal & Frame, ...]` to `ZIO[Frame, ...]`. `Application.run`'s own
   return type keeps `Terminal` — it performs the lifecycle acquisitions.

5. **The dead-surface retirements stand.** ADR-002 Q2's cleanup ratifications
   were correct on their own merits and have shipped.

Narrowing is compile-time enforcement. A consumer writing `Terminal.clearScreen`
inside `onMount` gets a type error, because the declared environment no longer
mentions `Terminal`. This is **not** ADR-002's rejected Option C, which proposed
*providing* a read-only `Terminal` at runtime and failing on service resolution
when a bad path executed. Nothing here defers to runtime.

### Why the capability is not worth building

- **It is bypassable.** Scala's `private[render]` is package-scoped, not
  module-sealed. Any consumer may declare a file in
  `package io.github.wickedsik.wsconsole.render` and mint a `WriteAuthority`. It
  stops accidents, not intent.
- **Our own test seam would be that bypass, committed.** Specs in packages
  `buffer` and `testkit` cannot mint the witness, so the tests need a sanctioned
  minting point in package `render` — which is precisely the bypass technique,
  demonstrated and blessed in-repo. A mechanism whose test fixture documents its
  own defeat is not enforcement.
- **It would not have caught the bug it was designed around.** See Context 1.
- **The one real breach was fixed without it.** See Context 2.
- **The cost is real.** Sixteen trait members plus sixteen companion accessors
  gain a `using` clause; three `Terminal` implementations change; the witness
  plumbs from `RenderLoop.make` through `Application` into `Frame`'s construction;
  every contributor learns a new concept. All of that to guard a door that, after
  narrowing, almost nobody stands at.

The residual benefit is accident-prevention on a surface that will be empty. That
does not pay for the ceremony. **Barring new evidence — a real consumer that
needs `Terminal` reads while being denied writes, or a demonstrated in-process
rogue write that narrowing cannot reach — the capability is not to be
reconsidered.**

## Alternatives Considered

### A. Build `WriteAuthority` as ADR-002 specified

**Rejected.** The full case is above. In short: bypassable, expensive, guards a
surface that narrowing empties, and would not have prevented the failure that
motivated it.

### B. Status quo — convention only, change nothing

**Rejected.** Convention is currently holding, but `Terminal` remains in a dozen
consumer-facing signatures that have no use for it. Removing capability nobody
needs is close to free and converts a convention into a type error. Declining a
cheap compile-time guarantee because a cheaper-still option exists is not
prudence.

### C. Seal the package (JPMS module, sealed JAR) to make `private[render]` unbypassable

**Rejected as disproportionate.** It would make the capability genuinely
unforgeable, but at the cost of imposing module-system constraints on every
downstream consumer's build to defend against an adversary — a consumer
deliberately declaring code in our package to write bytes we did not sanction —
who has chosen to break their own program.

### D. Narrow to a read-only terminal surface instead of removing `Terminal` entirely

**Deferred, not rejected.** If a consumer legitimately needs `capabilities` or
`size` inside a hook, the answer is a small read-only surface in the environment,
not the full `Terminal`. No such consumer exists today; `Frame.width` /
`Frame.height` cover the dimensional case. Recorded as Q2.

## Consequences

### Positive

- **The invariant is enforced by absence.** Nothing to thread, nothing to summon,
  nothing to learn. The strongest form of "you may not do this" is "you cannot
  express this."
- **The public surface shrinks twice.** Five dead members are already gone; the
  narrowing removes `Terminal` from a dozen consumer signatures.
- **No test seam, therefore no documented bypass.**
- **Downstream widget authors get a simpler contract.** A panel hook's environment
  tells them exactly what a panel may touch.

### Negative

- **Narrowing forecloses legitimate reads.** A future panel wanting
  `capabilities` to choose a colour depth would be blocked. Mitigation is Q2's
  read-only surface, added when a real consumer appears rather than in
  anticipation.
- **The monopoly is not defended against determined in-package code.** Anyone may
  still declare themselves in `package terminal` and call `AnsiTerminal` directly.
  This is accepted: so could they under ADR-002's mechanism.
- **`Application.run` remains a `Terminal` holder.** The lifecycle acquisitions
  need it. It is the one legitimate consumer-visible entry point that keeps the
  write surface in scope, and it is framework code.

### Neutral

- **ADR-002's policy language survives intact.** Only its enforcement mechanism is
  replaced. Points 1 and 2 of the Decision are quoted from it deliberately.
- **ADR-003 is unaffected.** Its precondition was "the buffer is the authoritative
  model because one writer produces it," which narrowing satisfies exactly as a
  capability would have.

## What is NOT in this decision

- **The invalidation taxonomy.** ADR-003, unchanged and still gated on its own
  Q3/Q5/Q6.
- **The yield/reclaim protocol for full-screen subprocess invocation.** ADR-002
  Q3 resolved it as policy; no such scenario exists in the codebase. It stays
  policy-only until a consumer needs it.
- **Event routing / dispatch ownership.** Separate axis
  (`demo-toolbar-shortcut-ownership.md`).

## Open Questions

- **Q1 — Narrowing granularity.** Does the narrowing land in one change across
  `Panel`, `PanelHost` and `Application.run`'s callbacks, or incrementally
  starting with `Panel`? `PanelHost`'s signatures are derived from `Panel`'s, so
  they likely move together; `Application.run`'s callback parameters are
  independent and could follow.

- **Q2 — What replaces `Terminal` reads when a consumer needs them?** A panel
  wanting `capabilities` (colour depth, Unicode support) has no route after
  narrowing. Options: extend `RenderContext` with a capabilities snapshot —
  consistent with ADR-001's "framework state is read from a per-frame snapshot" —
  or add a narrow read-only service to the hook environment. Decide when a real
  consumer appears; do not build it speculatively.

- **Q3 — Does `Panel.onUnload`'s direct canvas write survive narrowing?** It
  writes cells to the `Frame`'s canvas outside the render walk. Narrowing does not
  touch it, because it needs `Frame`, not `Terminal`. It is not a terminal write
  and does not breach this ADR — but it is a panel authoring cells outside the
  render walk, which is ADR-003's Q6. Flagged here so the two are not conflated:
  ADR-003 Q6 describes it as "exactly what ADR-002's renderer write-monopoly
  forbids," which is imprecise. It is a buffer write, and it is ADR-003's problem.

## References

- Supersedes: `docs/adr/adr-002-renderer-write-monopoly.md`
- Builds on: `docs/adr/adr-001-render-context.md`
- Related: `docs/adr/adr-003-invalidation-source-taxonomy.md`
- Root-cause evidence for Context 1: `.claude/tasks/demo-toolbar-disappearance.md`
- Current machinery:
  - `src/main/scala/terminal/Terminal.scala` (the surface, post-retirement)
  - `src/main/scala/app/Panel.scala`, `app/PanelHost.scala`, `app/Application.scala`
    (the signatures to narrow)
  - `src/main/scala/component/Component.scala` (already incapable, by ADR-001)
  - `src/main/scala/buffer/Frame.scala` (the flush the renderer drives)
