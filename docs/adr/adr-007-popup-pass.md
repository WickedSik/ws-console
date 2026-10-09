# ADR-007: Anchored Popups as a Layer 6 Pass

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-10-09  
**Deciders**: ws-console core  
**Builds on**: ADR-001 (`docs/adr/adr-001-render-context.md`), ADR-006 (`docs/adr/adr-006-reveal-pass.md`)  
**Source task**: `.claude/tasks/form-5-select.md`

---

## Context

A dropdown, autocomplete list, context menu or tooltip draws a box anchored to a component and over everything around it. The tree cannot express that today:

- **Clipping.** `ScrollView.render` hands its child `canvas.clip(viewport)`. A popup drawn from the owner's own `render` is cut at the viewport edge, and forms live inside ScrollViews.
- **Z-order.** The draw phase is one `root.render` call, so siblings drawn after the owner overwrite anything the owner draws outside its rect.
- **Overlays are Layer 7 and fixed.** `Panel.overlay(root, fixedBounds)` takes an absolute rect and nothing anchors it to a component. A library widget pushing one would depend on `PanelHost`, inverting the layers, and applications without a `PanelHost` could not use the widget.

The first consumer is `Select` (`form-5`). Combobox autocomplete, tag pickers, context menus and focus tooltips need the same primitive.

## Decision

### The contract

`Component` gains one defaulted method:

```scala
def popups(area: Rect): Seq[Popup] = Seq.empty
```

```scala
final case class Popup(
  content: Component,
  anchor: Rect,
  width: Int,
  height: Int
)
```

`area` is the component's own rect from the current layout. `anchor` is the rect the popup attaches to, in the same coordinate space, typically `area` itself. `width` and `height` are the size the popup wants; placement may shrink them.

Like `childLayouts` and `preferredHeight`, `popups` is pure geometry: no `RenderContext`, no focus. It reads widget-local state, such as a `Select`'s open flag, the same way `childLayouts` reads a ScrollView's offset.

### The pass

`RenderPipeline.runFrame` runs a `PopupPass` after layout and any reveal relayout (ADR-006), before drawing:

1. **Collect from the focus chain.** Walk from the focused component up through `layout.parents` and call `popups(rect)` on each component with a non-empty rect, outermost first. A popup is therefore shown only while its owner holds focus or contains it. This is the one focus-driven step, and like `RevealPass` it lives in Layer 6, so `popups` stays pure.
2. **Nothing focused, nothing shown.** With no focused component the pass returns the layout unchanged.
3. **Clip the anchor.** Intersect the anchor with the rect of every `Scrollable` ancestor, innermost first, as `RevealPass` does. If nothing remains, the anchor is scrolled out of view and the popup is dropped for this frame.
4. **Place.** Within the frame area:
   - width is clamped to the frame; `x` starts at the anchor's `x` and shifts left until the popup fits;
   - the popup goes below the anchor when `height` fits there, else above when it fits there, else on the side with more rows, its height cut to those rows.
5. **Lay out the content.** Resolve each popup's `content` at its placed rect with `LayoutManager` and merge its rects into the result, with the content root's parent set to the owner. Focusables inside popup content are **not** added to the `FocusOrder`.
6. **Draw.** After `root.render`, draw each popup in collection order: fill its rect with `Cell.Empty`, then render the content on `frame.canvas.clip(rect)`. The canvas is the frame's own, so no enclosing `ScrollView` clip applies; the popup's clip keeps the content inside its own box.

`LayoutResult` gains a field holding the placed popups in draw order, defaulted to empty. `LayoutManager` is the only place that constructs a `LayoutResult`, so the change touches a single call site.

### Focus stays with the owner

The popup is drawn, never focused. The owner keeps focus and handles every key while open, so keys never reach ancestors (`EventDispatcher` bubbles only on `Ignored`) and closing needs no focus restore. Confining focus *inside* a popup is the domain of `.claude/tasks/focus-scope.md` and out of scope here.

Owners treat loss of focus as close. Step 1 guarantees that a popup is never drawn for an unfocused owner on the frame where focus left, whether or not the owner has yet noticed.

### Following, not closing

Placement runs every frame from the owner's current rect, so a popup follows its anchor through resize, reflow and programmatic scroll at no cost. Step 3 removes it while the anchor is out of view.

## Alternatives Considered

### A. Overlay panel on `PanelHost`, rect taken from the owner's layout

**Rejected.** A Layer 4 widget would hold a Layer 7 host, and applications that render without `PanelHost` could not use dropdowns. The owner would also have to recompute and re-push the overlay on every layout change.

### B. Hand the owner an unclipped root canvas

**Rejected.** It removes the clip that keeps components inside their rects, the guarantee every container relies on. And it does not fix z-order: siblings rendered after the owner still overwrite the popup.

### C. Popup as a focus stop

**Rejected.** Moving focus into the list needs a focus scope to hold Tab and restore focus on close, which does not exist. It also gains nothing: the owner can drive a highlight on a list it draws.

### D. Collect popups inside `LayoutManager`

**Rejected.** Visibility depends on focus (step 1), and `LayoutManager` is pure of focus. A separate pass keeps that boundary, as `RevealPass` did for scrolling.

## Consequences

### Positive

- Popups escape ScrollView clipping and sibling overdraw by construction.
- No widget depends on `PanelHost`; any application rendering a tree gets popups.
- Placement is one shared rule. Every dropdown, autocomplete and menu flips above the anchor the same way.
- Keys need no new routing: the owner is focused, so dispatch is unchanged.

### Negative

- **Only focus owns popups.** A popup cannot be shown for a component that neither holds nor contains focus, so hover tooltips and toasts are out of scope; toasts belong on `PanelHost`. The upside is cost: each frame asks only the focus chain, a handful of components.
- **Popups ignore panel z-order.** They draw after the whole root, so a popup whose owner sits in a covered `PanelHost` panel would draw over the panel above. Step 1 limits this to the case where focus has reached a covered panel, which `focus-scope.md` is set to prevent.
- **No focusables inside popups.** A popup holding a text field or buttons cannot be tabbed into. That waits for `focus-scope.md`.
- **`Component` grows again.** Source-compatible, as with ADR-005: the method is defaulted.

### Neutral

- **Nested popups are not collected.** A popup's content does not get its own popups. Cascading menus will need a queue in step 1, which is a non-breaking addition.
- **Mouse.** When Layer 5 emits mouse events, hit-testing must try popups first, in reverse draw order, before the main tree. The merged rects and owner parent links in step 5 are what makes that possible.

## References

- `core/src/main/scala/component/Component.scala` (`popups`), `core/src/main/scala/component/Popup.scala`
- `core/src/main/scala/render/PopupPass.scala`
- `core/src/main/scala/render/RenderPipeline.scala`, `core/src/main/scala/render/LayoutResult.scala`
- `core/src/main/scala/render/RevealPass.scala` (the ancestor viewport walk step 3 mirrors)
- `tests/src/test/scala/render/PopupPassSpec.scala`
