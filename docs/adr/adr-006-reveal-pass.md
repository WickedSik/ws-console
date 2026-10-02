# ADR-006: Reveal-on-Focus as a Layer 6 Pass

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-10-02  
**Deciders**: ws-console core  
**Builds on**: ADR-001 (`docs/adr/adr-001-render-context.md`), ADR-005 (`docs/adr/adr-005-intrinsic-height.md`)  
**Source task**: `.claude/tasks/form-1-scrollview.md` (Part B)

---

## Context

A `ScrollView` shows a window onto content taller than itself. When focus moves to a descendant outside that window — Tab into an offscreen field — the view must scroll to show it. That is a layout decision driven by focus.

The `Component` contract forbids exactly that coupling: `childLayouts` is pure geometry and must not read `RenderContext` ("layout decisions must not depend on focus"), and `render` must mirror `childLayouts`. Scroll-to-focus therefore needs a sanctioned path outside both.

## Decision

### The pass

`RenderPipeline.runFrame` gains an optional `reveal: Option[ComponentId]`. When set, the frame runs:

1. **Layout** the tree.
2. **Reveal** (`RevealPass`): find the target's rect, then walk its ancestors innermost first. Each ancestor implementing the `Scrollable` capability is asked to `reveal(target, viewport)` and returns how far its content moved. The target rect is shifted by that distance and narrowed to the ancestor's viewport before the next ancestor out sees it, so nested scrollables compose without an intermediate layout.
3. **Layout again**, only if anything moved.
4. **Draw**, diff, flush as before.

The reveal therefore lands on the frame that follows the focus change, with no extra keypress and no second frame.

```scala
trait Scrollable:
  def reveal(target: Rect, viewport: Rect): Int
```

`ScrollView` implements it by scrolling the minimum distance; a target taller than the viewport aligns its top edge.

### Edge-triggered

`RenderLoop` remembers the id it revealed last and passes `reveal = Some(focused)` only when focus differs from it. Running the pass on every frame would undo any manual scroll: a user who pages away from the focused field would be snapped straight back on the next frame. Every route that changes focus — `focusNext`, `focus(id)`, policy reconciliation in `setOrder` — schedules a frame, and that frame sees the new id and reveals it.

### State ownership

The scroll offset stays widget-local state (ADR-001, "local = local"). The pass mutates it through the `Scrollable` capability, the same way a key handler does. `childLayouts` remains pure of focus; it reads the offset like any other widget-local field.

## Alternatives Considered

### A. ScrollView adjusts its offset inside `render` from `ctx.focus`

**Rejected.** `render` would make a layout decision that `childLayouts` never saw, breaking the rule that the two are views of the same layout. The `LayoutResult` used for dispatch and the focus order would disagree with what is on screen for a frame.

### B. ScrollView intercepts Tab as it bubbles

**Rejected.** It misses programmatic focus changes (`focusManager.focus(id)`) and policy reconciliation, and it misses Tab bound above the ScrollView — which is where the demo binds it, in a root-level `GlobalShortcuts`.

### C. Level-triggered reveal (every frame)

**Rejected.** See "Edge-triggered" above: it fights the user's own scrolling.

## Consequences

### Positive

- Tab walks a form of any height and the view follows, whoever moved focus and however deep the scrollable nesting.
- `childLayouts` and `render` keep their contracts.
- Offscreen focusables need no special casing: their translated rects stay full-size, so `FocusOrder` keeps them.

### Negative

- **Two layouts on reveal frames.** Only frames following a focus change pay it.
- **Typing does not re-reveal.** A user who pages away from a focused text field and types sees no scroll back; browsers do scroll back. Recorded as an open question in the task scroll rather than solved speculatively.
- **`Renderer` / `RenderPipeline` signatures grew a defaulted parameter.** Source-compatible for callers; a custom implementation of either trait must add it.

### Neutral

- **Mouse wheel.** When Layer 5 emits mouse events, wheel input should scroll the innermost `ScrollView` under the pointer. Hit-testing must intersect the pointer with the viewport, not only with the descendant's translated rect, which can lie offscreen.
- **Horizontal scrolling** is out of scope; `ScrollView` is vertical only.

## References

- `src/main/scala/render/RevealPass.scala`
- `src/main/scala/render/RenderPipeline.scala`, `src/main/scala/render/Renderer.scala` (`reveal` parameter)
- `src/main/scala/render/RenderLoop.scala` (`lastRevealed`, `revealTarget`)
- `src/main/scala/component/Scrollable.scala`, `src/main/scala/component/ScrollView.scala`
- `src/test/scala/render/RevealPassSpec.scala`, `src/test/scala/render/RenderLoopSpec.scala` ("reveal lands on the first frame after a focus change")
