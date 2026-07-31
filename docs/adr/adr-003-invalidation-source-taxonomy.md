# ADR-003: Invalidation Source Taxonomy and the Collapsing Diff

**Status**: Accepted  
**Implementation**: Partial  
**Date**: 2026-06-18  
**Deciders**: ws-console core  
**Builds on**: ADR-001 (`docs/adr-001-render-context.md`), ADR-002 (`docs/adr-002-renderer-write-monopoly.md`)  
**Source ADT**: rendering/invalidation rewrite (ratified decisions 1 + 2)

---

## Implementation Status

`Implementation: Partial`. The vocabulary and the invalidation primitive are in the
code; the five-source taxonomy, the coalescing accumulator, and the temporal source
are not. Verified 2026-07-20.

**In the code (enforceable):**

- The *invalidate* vocabulary is adopted throughout code, tests, and Scaladoc — no
  "draw", "damage", or "dirty" (`buffer/Frame.scala:73, :103, :190`).
- `Frame.invalidate` exists as the flicker-free baseline-wipe primitive, distinct from
  `Frame.clearScreen`.
- `\e[2J` is reserved for the corruption-reset path (`Frame.clearScreen`) rather than
  being part of the ordinary refresh mechanism.

**Not built (not enforceable — see `.claude/tasks/JUDGEMENT-animated-panel-flush-cadence.md`):**

- The five invalidation sources are not modelled. No `InvalidationSource` type exists;
  the loop still carries the single global `invalidateNext: Ref[Boolean]`
  (`render/RenderLoop.scala:129`) that Alternative A describes as the thing being replaced.
- No coalescing accumulator. The `Option[Rect]` bounding region ratified in Q1 does not
  exist, and no partial re-derivation entry point exists — `redraw` re-renders the full
  tree.
- The temporal source is not implemented. `SpinnerPanel` and `ProgressBarPanel` still own
  sleep-driven fibers that flush directly; the only cadence in the loop is resize polling.
- Invalidation is not yet a consequence of mutation — it remains a separate call.

## Terminology

This document uses **invalidation** throughout: *invalidate*, *invalidated*,
*invalidation region*, *invalidation source*. An entity that needs the screen to
change does not "draw" and does not "mark damage" — it **invalidates** a region,
and the renderer repaints. (The industry term for this is "dirty-region
rendering"; the library's own vocabulary is invalidation, and that is the only
vocabulary used in code, tests, scaladoc, and design.)

## Context

ADR-002 established that the renderer is the sole writer and that **invalidation
is the only channel** by which anything affects the screen. That settles *who
writes* and *that invalidation is the mechanism*. It does not say **what kinds of
invalidation exist**, **how an entity invalidates itself**, or **how multiple
invalidations between two frames collapse into one minimal repaint**. This ADR
defines that.

### What the current system actually has

One global redraw bit. `RenderLoop.requestRedraw` enqueues a bare `Unit` on a
queue; a redraw means "re-render the whole root and diff it." There is no notion
of *which region* is stale or *why*. The escalations — `requestRefresh`
(`Frame.invalidate`, wipe the diff baseline) and `requestFullRedraw`
(`Frame.clearScreen`, baseline wipe **plus** `\e[2J`) — are not finer-grained
invalidation; they are blunter. They are the fossil record of a missing model:
when you cannot name a stale region, your only tools are "redraw everything" and
"nuke the screen." Three hammers, one missing concept.

### Empirical motivation (each source maps to a bug)

- **`demo-focus-flicker.md`** — Tab mutated focus but scheduled no frame.
  Invalidation must be a *consequence* of state mutation, not a manual call a
  human can forget. → **State invalidation**.
- **`demo-toolbar-disappearance.md`** — a panel swap is a structural tree change;
  the geometry the old subtree occupied and the new one will occupy must both be
  repainted. The shipped band-aid (`requestRefresh` re-blast the whole frame) is
  exactly "we have no way to say *this region* changed, so redo all of it." →
  **Structural invalidation** (and the band-aid's replacement).
- **Panels are opaque and stack** (`docs/component-styleguide.md`: "a Panel owns
  every cell in its bounds; content beneath does not leak through"). When a panel
  resizes or pops, it *reveals* cells that were occluded. Those revealed cells are
  stale and nobody drew them this frame. → **Layout/occlusion invalidation**.
- **`demo-missing-panels-restoration.md`** — spinner (80 ms) and progress bar
  (30 ms) need a redraw cadence with no external pacing. Animation is a region
  that says "re-invalidate me every N ms." → **Temporal invalidation**.
- **`demo-missing-panels-restoration.md` / `demo-toolbar-disappearance.md`** —
  resize, alt-buffer entry, scroll-region install (terminal state *outside* the
  cell model), and subprocess ANSI mutate the display behind the buffer's back.
  Per ADR-002 these must announce themselves. → **External / model-invalidating**.

ADR-001 supplies the conceptual hinge for the first source: a component reads
framework state from a per-frame `RenderContext` snapshot. **What you read, you
depend on.** If a value a region read during render is later mutated, that region
is invalidated. Invalidation is the dependency-tracking dual of ADR-001's
read-only snapshot.

## Decision

Define **five invalidation sources**. Every request for the screen to change is
one of these. The renderer coalesces all invalidations raised between two frames
into a single invalidation set, re-derives only the invalidated regions into the
buffer model, and lets the cell-diff produce minimal terminal I/O.

The signatures below are **ILLUSTRATIVE** — they show the *shape* of each
contract, not an implementation to build. Per project policy no code is
mandated here; the implementing task ratifies the concrete API.

### The five sources

#### 1. State invalidation

*A value a region read during render is mutated → that region is invalidated.*

The dual of ADR-001's `RenderContext`. A region that read a piece of
framework-owned or application-owned state (focus, a `State[S]` value, the
per-frame timestamp) depends on it; when that state changes, the region that
depended on it is invalidated. The entity does not call "redraw" — the *mutation*
raises the invalidation.

```scala
// ILLUSTRATIVE
state.update(f)   // mutating the value invalidates every region that read it
```

**Why it exists / bug it prevents:** `demo-focus-flicker.md`. Focus changed but
no frame was scheduled because invalidation was a manual, forgettable call.
Binding invalidation to mutation makes "forgot to schedule a redraw" unreachable.
Ties directly to `State[S]` (`app/State.scala`) as the canonical mutable-value
source.

#### 2. Structural invalidation

*A subtree is swapped, inserted, or removed → the geometry it occupied (old) and
will occupy (new) is invalidated.*

A panel swap, a child added to a container, a row removed from a list. The union
of the vacated rect and the new rect is invalidated.

```scala
// ILLUSTRATIVE
host.replace(panel)   // invalidates (old subtree bounds ∪ new subtree bounds)
```

**Why it exists / bug it prevents:** `demo-toolbar-disappearance.md`. The current
fix re-blasts the *entire* frame on every swap because there is no way to name the
changed region. Structural invalidation names exactly the old∪new geometry, so
the swap repaints precisely what changed — the toolbar, untouched by the swap, is
not in the set and is not needlessly re-emitted (and, per ADR-002, cannot have
drifted).

#### 3. Layout / occlusion invalidation

*Reflow, or an opaque panel resizing or popping to reveal what was beneath →
the union of vacated and revealed rects is invalidated.*

Mandatory because Panels are opaque and stack. When the top panel shrinks or
pops, the cells it occluded are revealed and stale — no component drew them this
frame, and the buffer must re-derive whatever is now visible there.

```scala
// ILLUSTRATIVE
// popping a panel invalidates the rect it vacated, so the layer
// beneath is re-derived into the buffer and diffed onto the screen
```

**Why it exists / bug it prevents:** the opacity/stacking model in
`component-styleguide.md`. Without occlusion invalidation, popping a panel leaves
the revealed region holding the popped panel's cells in the terminal forever
(another instance of the disappearance class, inverted). This is the source that
makes overdraw + stacking safe.

#### 4. Temporal invalidation

*An animated region declares a cadence — "re-invalidate me every N ms" — and the
renderer re-invalidates it on that schedule.*

Animation is not a special rendering mode; it is a *timed invalidation source*. A
spinner declares an 80 ms cadence, a progress bar 30 ms. The renderer wakes the
region at its interval and re-invalidates it; the region re-derives its glyph from
the per-frame timestamp (ADR-001 / the style-guide's `Spinner` contract) and the
diff emits the single changed cell.

```scala
// ILLUSTRATIVE
def invalidationCadence: Option[Duration] = Some(80.millis)
```

**Why it exists / bug it prevents:** `demo-missing-panels-restoration.md`. The
pre-L7 panels owned their own fiber and called `Frame.run` directly — a direct
write, now forbidden by ADR-002. Temporal invalidation gives animation a path
that respects the write-monopoly: the region declares a cadence, the renderer
paces it. No panel-owned fiber races the loop.

#### 5. External / model-invalidating event

*Resize, alt-buffer entry, scroll-region install, subprocess ANSI, or any
terminal-state mutation outside the cell model → invalidates a region or the
whole screen.*

This is the bridge to ADR-002 and the safety valve for the unproven
toolbar-disappearance root cause. Anything that changes the terminal behind the
buffer's back must announce itself here so the single writer's model stays
authoritative. Resize invalidates the whole viewport. A scroll-region install
invalidates the region it claims. A subprocess that emitted its own ANSI
invalidates whatever it could have touched (in the limit, everything).

```scala
// ILLUSTRATIVE
Event.Resize(w, h)        // invalidates entire viewport
installScrollRegion(r)    // invalidates r; model now tracks region state
afterSubprocess()         // invalidates entire viewport (model untrustworthy)
```

**Why it exists / bug it prevents:** `demo-toolbar-disappearance.md` (the
unproven terminal-side drift) and `demo-missing-panels-restoration.md`
(scroll regions are terminal state outside the buffer). If every such mutation
*must* invalidate, the drift is no longer silent — it is declared, and the
renderer re-establishes the affected region.

### The collapsing diff

Invalidations raised between two frames **union into one invalidation set per
frame**. The renderer:

1. Coalesces every invalidation since the last frame into a single region
   (see Q1: an `Option[Rect]` bounding accumulator, starting simple).
2. Re-derives **only** the invalidated regions of the component tree into the
   buffer — the authoritative model of the screen (ADR-002).
3. Runs the existing cell-diff of the buffer against `previous`
   (`ScreenBuffer.diff` via `BufferManager.diff`), which already yields minimal
   ANSI I/O.

The cell-diff is the collapsing mechanism's substrate. It is kept. The buffer is
reconceived, per ADR-002, as the authoritative model of the screen's current
state — not a scratch drawing surface. The collapsing happens at two levels:
re-deriving only the invalidated region saves tree walks (avoids re-running
expensive render logic for untouched subtrees), and the cell-diff merges
*redundant cell writes* (avoids emitting bytes for cells that did not actually
change). Ten state mutations to one widget between frames produce one region in
the set and, after diff, only the cells that genuinely changed.

#### Full redraw is not a separate mechanism

A "full redraw" is simply **invalidation set = entire viewport**. Resize raises
it. First render raises it. There is no `requestFullRedraw` concept — there is one
invalidation mechanism, and "everything" is just its maximal region. The
`requestRefresh` / `requestFullRedraw` / `requestRedraw` hammers collapse into
this single mechanism.

#### The `\e[2J` corruption-reset is orthogonal

Clearing the terminal with `\e[2J` is **not** part of the invalidation set and is
**not** how full redraws work. It is a separate, rare concern reserved for
"external corruption — the model can no longer be trusted at all" (e.g. a
subprocess scrambled the display and even the buffer model is suspect). It blanks
the physical terminal and forces the buffer baseline empty so the next frame
re-establishes from scratch. It is orthogonal to invalidation: a whole-viewport
invalidation repaints every cell *trusting the buffer model*; a corruption-reset
*discards trust in the model* and starts the physical screen over. The three
hammers therefore collapse to: **one invalidation mechanism** (with whole-viewport
as its maximum) **plus one rare corruption-reset**.

## Alternatives Considered

### A. Keep the single global redraw bit + the three hammers

**Rejected.** This is the status quo whose absence of a region model produced the
band-aids. It cannot express "this region is stale," so it cannot stop
re-blasting the whole frame on every structural change, and it conflates
"repaint everything" with "the screen is corrupt."

### B. Per-component dirty flags (component sets its own bit)

**Rejected as the primary model, folded in as State invalidation.** A raw
per-component flag the component sets manually has the same failure mode as
ADR-001's per-component focus cache: the component (or its author) can *forget* to
set it. Binding invalidation to *state mutation* (source 1) and to *structure/
layout/time/external events* (sources 2–5) makes the common cases automatic. A
component does not raise its own invalidation by remembering to; the mutation it
performed does.

### C. Full React reconciler / virtual-DOM keyed diff

**Rejected**, consistent with ADR-001 Alternative B. The Layer 2 cell-diff already
performs reconciliation at the right granularity. A second, tree-level reconciler
is a large new surface for marginal benefit. Region invalidation + cell-diff is
the minimum that solves the bugs.

### D. Only two sources (state + "everything else")

**Rejected.** Collapsing structural, occlusion, temporal, and external into one
bucket loses exactly the distinctions that matter: occlusion *must* compute
old∪revealed because panels are opaque; temporal *must* carry a cadence; external
*must* be allowed to invalidate the whole viewport and possibly trigger the
orthogonal corruption-reset. The five-way split is not gratuitous; each source has
a distinct trigger and a distinct region-derivation rule.

## Consequences

### Positive

- **Invalidation becomes a consequence, not a chore.** State mutation, structure
  change, reflow, a cadence tick, an external event — each *is* the invalidation.
  The `demo-focus-flicker.md` "forgot to schedule a frame" class is structurally
  closed, mirroring how ADR-001 closed the focus-cache-drift class.
- **The three hammers become one mechanism plus one reset.** `requestRedraw` /
  `requestRefresh` / `requestFullRedraw` retire. "Full redraw" is whole-viewport
  invalidation; `\e[2J` is the rare, explicit corruption-reset. The surface
  shrinks and each remaining concept means exactly one thing.
- **Opaque stacking is finally safe.** Occlusion invalidation makes reveal-on-pop
  and reveal-on-resize correct by construction — the inverse disappearance bug
  cannot occur.
- **Animation respects the write-monopoly.** Temporal invalidation gives spinners
  and progress bars a cadence without a panel-owned fiber writing directly
  (forbidden by ADR-002).
- **Minimal I/O is preserved and sharpened.** Region coalescing avoids
  re-deriving untouched subtrees; the kept cell-diff avoids redundant bytes. The
  toolbar untouched by a panel swap is not in the invalidation set and not
  re-emitted.

### Negative

- **Occlusion / union-rect computation has a cost.** Computing old∪revealed for
  opaque stacked panels, merging overlapping rects into a minimal set, and
  re-deriving partial subtrees is more complex than "re-render the root." For
  small trees the whole-viewport path may be cheaper than the bookkeeping;
  the implementation needs a threshold below which it just invalidates everything.
- **Temporal sources need wakeup machinery.** A cadence per animated region means
  the renderer schedules timed re-invalidations and coalesces them with
  event-driven ones. This is a scheduler the current single redraw queue does not
  have. Multiple cadences (80 ms spinner + 30 ms bar) must align without drift or
  thundering wakeups.
- **Every external terminal mutation must be routed through invalidation.** The
  burden ADR-002 names lands here: scroll-region install, alt-buffer entry,
  subprocess return each must raise the right invalidation. Miss one and the drift
  ADR-002 was meant to eliminate reappears — now as a missing invalidation rather
  than a rogue write. The taxonomy must be exhaustive for the guarantee to hold.
- **Partial re-derivation must honor the component contract.** A region re-derived
  in isolation must produce the same cells it would in a full walk. ADR-001's
  "render is a pure function of `(area, canvas, ctx)`" is what makes this sound;
  any component that smuggles in hidden state breaks partial invalidation. The
  discipline ADR-001 introduced becomes load-bearing here.

### Neutral

- **The buffer is kept, reconceived.** No rewrite of `ScreenBuffer.diff` /
  `BufferManager`; their role is relabeled from "double-buffer for flicker-free
  drawing" to "authoritative model of the screen, diffed to minimal I/O."
- **`State[S]` gains a first real consumer.** Currently unused by the demo
  (`app/State.scala`), it becomes the canonical state-invalidation source.

## What is NOT in this decision

- **Event routing / dispatch.** Which component receives a keypress is a separate
  axis (`demo-toolbar-shortcut-ownership.md`). Invalidation is about *output*
  regions; dispatch is about *input* targets. Out of scope.
- **The concrete invalidation API.** The illustrative signatures are shapes, not
  mandates. Region representation, the coalescing data structure, the cadence
  scheduler, and the partial-re-derivation entry point are ratified by the
  implementing task.
- **A per-component re-render cache.** Beyond region invalidation, memoizing
  individual component output is a future optimization with its own diagnosis
  (consistent with ADR-001's closing scope note).

## Open Questions

- **Q1 — Region representation. [RESOLVED — start with `Option[Rect]`; measure
  before going richer.]** *Resolution:* the starting representation is decided;
  what remains is post-implementation measurement, not an open design choice.
  Start with a
  single `Option[Rect]` bounding accumulator that expands (union) as each
  invalidation is raised. This is simpler and more honest than "minimal set of
  rects": a single rect is not a set, and `Rect` is already the geometry type
  throughout the stack (`geometry/Rect.scala:10`). Over-invalidation (using a
  bounding rect instead of an optimal region) costs **component re-derivation**
  (CPU to re-run render into the buffer), but the cell-diff (`ScreenBuffer.diff`
  @224) scans the **full buffer unconditionally** — so over-invalidation does not
  increase scan cost or I/O (cell-diff is always full-buffer; it skips only the
  cells that did not change). If re-derivation cost proves significant on large
  component trees, `Option[Rect]` → `List[Rect]` with `intersects` filters is an
  additive step; true minimal-cover merging is NP-hard and unjustified at current
  scale (bounded by ~5 invalidation sources per frame). Requires `Rect.union`
  and `Rect.boundingBox` helper methods (currently absent). If buffer-scan cost
  ever matters (very large terminals), the fix is adding a region parameter to
  `ScreenBuffer.diff` — a separate axis, kept orthogonal to invalidation shape.
  Measure before pursuing either axis.
- **Q2 — Whole-viewport threshold. [RESOLVED — the threshold is not the real
  question; defer partial re-derivation, do two cheap steps first.]**
  *Resolution:* the layout pass is inherently whole-tree
  (`LayoutManager.resolve`, `render/LayoutManager.scala:35`) — every node's rect
  derives top-down from its parent's, so the whole tree must be laid out to know
  which components intersect the invalidated rect. **Partial re-derivation can
  therefore skip only `render` calls, never the layout pass**, and the cell-diff
  (`ScreenBuffer.diff` @224) is an unconditional full-buffer scan (see Q1). Two
  fixed floors — full layout + full diff — bracket any saving. Invalidation falls
  into three regimes, not one continuous threshold: **(A) tiny** — temporal
  (spinner cell) and focused-state invalidation; partial obviously wins. **(B)
  structural** — a panel swap invalidates old∪new bounds ≈ full viewport; partial
  saves nothing, always full. **(C) intermediate** — the only regime where a
  threshold is meaningful, but at current tree scale (~35 nodes) the crossover is
  a minor slope, not a cliff. Decision: **do not build subtree-pruning partial
  re-derivation now.** Instead (1) rect-clip the draw phase
  (`RenderPipeline.scala:49`) — skip a component's `render` when its laid-out rect
  misses the invalidated rect (cheap, additive, captures regime A); (2) add a
  region parameter to `ScreenBuffer.diff` (Q1's orthogonal axis) — likely a larger
  win than render-skipping while the diff is the dominant fixed cost. If a number
  is forced, the crossover sits ~60–70% of viewport area, and only bites when the
  skipped components are Panels (O(area) fill) or `RawCanvas`. **Coupling:** Q1's
  single bounding rect and Q5's coarse subtree tracking both inflate the effective
  invalidated fraction (coalescing two far-apart sources → near-whole-viewport
  rect), pushing further toward always-full. Revisit only when a real consumer
  (log widget, file viewer with many Text rows) makes some subtree's render
  genuinely expensive; measure then.
- **Q3 — Cadence scheduler shape.** One timer multiplexing all temporal sources,
  or per-region schedules? How do cadences coalesce with event-driven invalidations
  in one frame without drift?
- **Q4 — Subprocess protocol.** Does ADR-002's "yield the terminal, full-invalidate
  on return" (Q3 there) trigger a whole-viewport invalidation, a corruption-reset,
  or both? Likely: invalidate-all if the subprocess stayed within our region model,
  corruption-reset if it could have scrambled arbitrary cells.
- **Q5 — State→region dependency tracking.** How precisely is "every region that
  read this value" tracked? Coarse (any state change invalidates the reader's whole
  subtree) is simple; fine (cell-level read tracking) is expensive. Coarse first.
- **Q6 — Panel unload and occlusion invalidation.** `Panel.onUnload`
  (`src/main/scala/app/Panel.scala:50`) currently writes `Cell.Empty` directly
  into the canvas to clear the panel's bounds when it unloads. Under this ADR,
  that is a direct write — exactly what ADR-002's renderer write-monopoly forbids.
  A panel popping is a structural invalidation (source 2) and/or occlusion
  invalidation (source 3): the bounds it vacated must be invalidated so the
  renderer re-derives whatever is now revealed beneath, not the panel hand-
  clearing its own cells. How does `onUnload`'s clear-bounds behavior migrate
  onto the invalidation model? Does popping a panel simply raise an occlusion/
  structural invalidation of its vacated bounds, letting the renderer (ADR-002's
  sole writer) repaint the revealed region — making the direct write disappear
  entirely?

## References

- Builds on: `docs/adr-001-render-context.md`, `docs/adr-002-renderer-write-monopoly.md`
- Bug evidence:
  - `.claude/tasks/demo-focus-flicker.md` (state invalidation)
  - `.claude/tasks/demo-toolbar-disappearance.md` (structural + external)
  - `.claude/tasks/demo-missing-panels-restoration.md` (temporal + external)
- Scoped out: `.claude/tasks/demo-toolbar-shortcut-ownership.md` (event routing)
- Model substrate:
  - `src/main/scala/buffer/ScreenBuffer.scala`, `buffer/BufferManager.scala` (cell-diff)
  - `src/main/scala/geometry/Rect.scala` (region substrate — union/boundingBox needed)
  - `src/main/scala/buffer/Frame.scala` (`invalidate` / `clearScreen` — the hammers being retired)
  - `src/main/scala/render/RenderLoop.scala` (the three-hammer surface this replaces)
  - `src/main/scala/component/RenderContext.scala` (the read-snapshot dual of state invalidation)
  - `src/main/scala/app/State.scala` (canonical state-invalidation source)
  - `docs/component-styleguide.md` (Panel opacity / stacking → occlusion source)
