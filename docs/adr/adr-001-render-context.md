# ADR-001: Unidirectional Component State via Render Context

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-05-17  
**Deciders**: ws-console core  
**Supersedes**: prior per-component focus-cache pattern (`focusedFlag` + `setFocused`)  
**Source ADT**: `.claude/tasks/ADT-unidirectional-component-state.md`

---

## Context

Before this decision, `Component` subclasses cached framework-owned state (specifically focus) in private mutable cells, and the application was responsible for keeping those caches in sync with the framework's authoritative state (`FocusManager.focused`). Concretely:

- `FocusManager` held the focused `ComponentId` in a `Ref`.
- Each focusable widget (`ToolbarButton`, `FocusableBox`) held a private `AtomicBoolean focusedFlag`.
- After every focus mutation, the application had to manually push the new focused state into each widget via `setFocused(true|false)`. The demo's Tab handler contained five such calls, run in lockstep after every `focusNext`/`focusPrevious`.
- Adding a new focusable widget required editing the application's Tab handler. Forgetting a `setFocused` call, calling them in the wrong order, or omitting a widget left the visual silently out of sync with the model.

This was the root mechanism behind a class of visual desync bugs investigated in `.claude/tasks/demo-focus-flicker.md` and `.claude/tasks/demo-toolbar-disappearance.md`. The symptoms were patched at higher layers (e.g. `requestFullRedraw`, `requestRefresh`) but the underlying brittleness — *shared state lives in two places and requires application code to reconcile them* — was the recurring cause.

## Decision

Extend the `Component` contract to receive an immutable `RenderContext` at render and event time. The context carries snapshots of state the framework owns. Components query the context for shared state; they no longer cache it.

```scala
trait Component:
  def render(area: Rect, canvas: Canvas, ctx: RenderContext): Unit
  def handleEvent(event: Event, ctx: RenderContext): EventResult = EventResult.Ignored
```

The first context entry is `FocusSnapshot` — a read-only view of `FocusManager.focused` captured at the start of each frame (and at each dispatch boundary). Future entries (`theme`, `layoutConfig`) extend the case class non-breakingly.

The library exposes `FocusPolicy` as an open trait with named values (`DropOnRemoval`, `MoveToFirstOnRemoval`) and a `custom` escape hatch. `FocusManager.make` takes a `FocusPolicy` parameter (default `MoveToFirstOnRemoval`) that decides what happens to focus when the focused widget is no longer in the rendered tree.

The render pipeline (`LayoutManager.resolve`) produces a `FocusOrder` as a side-channel of the same tree walk that resolves layout — components do not register themselves with the manager. "What you can Tab to is exactly what is rendered, focusable, and visible right now."

### Discipline

- **Shared = lifted.** State that two or more components must agree on lives in framework services and is exposed read-only through `RenderContext`.
- **Local = local.** State that belongs to one widget (pending activation, animation phase, scroll position, input cursor) stays in that widget as private mutable state with a `UIO`-shaped public surface. The discipline is documented; reviewers enforce it.

### Snapshot stability

The render loop is single-fiber. The `RenderContext` captured at the start of `RenderLoop.redraw` is stable for the duration of the render walk — focus does not flip mid-frame. The same applies to dispatch: the snapshot captured before `EventDispatcher.dispatch` is stable across the bubble path. This mirrors React's "props don't change during render" guarantee.

## Alternatives Considered

### A. Status quo — per-component caches with manual push

**Rejected.** This is the pattern the decision replaces. Three structural problems:

1. The application is responsible for reconciliation. Every new focusable widget requires editing the Tab handler.
2. Drift is silent. A missed `setFocused` call produces a wrong visual with no error.
3. Diagnosis is hard. The same observable (stale focus visual) can be caused by a missed sync call, a race between the push and the render, or a diff-engine ordering issue.

### B. Full React-style port — virtual DOM, hooks, reactive recomputation

**Rejected.** Borrowing a single principle from React is justified by the problem; importing the whole framework is not. ws-console is a TUI library backed by Layer 2's cell-level diff, which already serves the role of virtual-DOM reconciliation at the right granularity. `useState`/`useEffect`/JSX would add a substantial new surface area for marginal benefit.

### C. Lift focus state to `RenderContext`; keep widget-local state in widgets

**Accepted.** The minimum-viable application of the "shared = lifted, local = local" principle. Captures the value (one source of truth for shared state) without importing the full React mental model.

## Consequences

### Positive

- **One source of truth for focus.** The "did we forget setFocused?" bug class is structurally impossible — there is no per-component focus cache to drift.
- **Adding a focusable widget requires no application changes.** The demo's `FocusDemoPanel` ships with three boxes (previously two); adding the third required only the new `FocusableBox` instance — zero changes to `DemoApp.handleEvent`. This is the AC-4 validation from the source ADT.
- **The Tab handler collapses to one line.** What was an 8-line for-comprehension with five `setFocused` pushes is now `if shift then focusPrevious() else focusNext()` followed by `.as(true)`.
- **Components become more reusable.** A `ToolbarButton` doesn't carry application-specific state; any application that wants buttons in a focus cycle gets the right behaviour by composition alone.
- **`FocusPolicy` is a library primitive.** Consumers choose `DropOnRemoval` for forms-style "focus dies with the widget" semantics, `MoveToFirstOnRemoval` for always-something-focused dashboards, or `custom(f)` for modal-stack / accessibility-driven strategies. The library does not force a single answer.
- **The contract becomes honest.** `Component.render` is closer to "pure function of `(area, canvas, ctx)`". The framework's invariants are explicit at the contract level instead of "by convention."

### Negative

- **Every existing component's signature changed.** ~21 main-source files and 11 test files touched in the sweep. The library is pre-1.0 with no external consumers; the breaking change is acceptable for that reason.
- **One more parameter on every render call.** Cognitively small but not zero.
- **A second contract to defend.** Future contributors must internalise "shared = lifted, local = local." The contract gives them a framework to apply, but mistakes will still happen and reviewers must catch them.
- **`FocusPolicy.MoveToFirstOnRemoval` default changes prior `FocusManager.updateFocusables` behaviour.** Tests that asserted "focus drops on removal" now construct `FocusManager` with explicit `DropOnRemoval` to preserve their original premise.

### Neutral

- **Snapshot timing.** `FocusSnapshot` is captured once per frame and once per dispatch. The single-fiber loop prevents intra-frame focus flips in practice. Documented in `RenderContext`'s scaladoc and in this ADR.
- **`pendingFlag` on `ToolbarButton` stays local.** Activation orchestration (callback vs. polled flag) is a separate concern, deferred to a follow-on task. The discipline applies — local widget state is permitted to stay local.

## What is NOT in this decision

- **Theme / color scheme.** Adding `ctx.theme` is a future non-breaking extension. Theme design has its own open questions (light/dark, semantic vs. literal colors, runtime switching, accessibility) and is deferred to its own ADR when a real consumer expresses the need.
- **Consumer-extensible context.** A `Map[ContextKey[A], A]` lookup on `RenderContext` is not in v1. The fixed-shape case class is intentional. Add the extension point against a concrete consumer requirement, not speculatively.
- **Hooks, virtual DOM, reactive recomputation, component reconciliation by key.** See "Alternatives Considered B."
- **Per-component re-render avoidance.** The Layer 2 cell-level diff already collapses no-op renders to zero ANSI bytes. Per-component skipping is a future optimization with its own diagnosis.

## References

- Source ADT: `.claude/tasks/ADT-unidirectional-component-state.md`
- Bug evidence: `.claude/tasks/demo-focus-flicker.md`, `.claude/tasks/demo-toolbar-disappearance.md`
- Implementation:
  - `src/main/scala/component/RenderContext.scala`
  - `src/main/scala/render/FocusOrder.scala`
  - `src/main/scala/render/FocusPolicy.scala`
  - `src/main/scala/component/Component.scala` (signature change)
  - `src/main/scala/render/FocusManager.scala` (`setOrder` + `FocusPolicy`)
  - `src/main/scala/render/LayoutManager.scala` (`FocusOrder` side-channel)
  - `src/main/scala/render/RenderLoop.scala` (snapshot at frame + dispatch)
  - `src/main/scala/render/EventDispatcher.scala` (ctx through bubble path)
