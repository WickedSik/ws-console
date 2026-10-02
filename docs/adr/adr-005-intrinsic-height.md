# ADR-005: Intrinsic Height on the Component Contract

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-10-02  
**Deciders**: ws-console core  
**Source task**: `.claude/tasks/form-1-scrollview.md` (Part A)

---

## Context

Until now every height in a `VBox` was chosen by the caller. `Component` exposed `render` and `childLayouts` and nothing that reported a natural size; `Constraint` offered `Fixed`, `Percentage`, `Fill`, and `Bounded`, none of which asks the child.

Two consumer categories need the child's own answer:

- **Viewports.** A `ScrollView` cannot scroll content whose height nobody knows. Forms, settings screens, log panes, and lists are all taller than the area they get.
- **Content-sized rows.** Word-wrapped text (`form-2`) is the first component whose height depends on its width. A caller cannot write `Fixed(n)` for it without re-implementing the wrap.

## Decision

Add one defaulted method to `Component`:

```scala
def preferredHeight(width: Int): Option[Int] = None
```

and one constraint:

```scala
case object Fit extends Constraint
```

### Semantics

- **Height-for-width only.** The answer may depend on `width`, never the reverse. There is no preferred width.
- **`None` means no preference.** The component takes whatever its parent assigns, exactly as before. The default keeps every existing component and every consumer widget source-compatible.
- **Pure geometry.** Like `childLayouts`, `preferredHeight` takes no `RenderContext` and must not depend on focus.
- **Leaves answer from content and chrome.** `Text` = 1; `TextInput`, `Button` = 1 + border rows + vertical padding; `Checkbox`, `ProgressBar`, `Spinner` = 1; `RadioGroup` = option count. `Spacer` and `RawCanvas` have no preference.
- **Composites answer from children.** `Panel` = child at the inner width + border + padding (no preference if the child has none). `HBox` = the tallest child, each measured at the width the horizontal solve assigns it. `VBox` = the sum of child contributions: `Fixed(n)` counts `n`; `Fit` and `Fill` count the child's preference; `Percentage` counts nothing (a percentage of an intrinsic total is circular); `Bounded` clamps its inner contribution and falls back to its lower bound. A `VBox` with no contributing child has no preference.

### Resolving `Fit`

A vertical container replaces `Fit` (bare, or inside `Bounded`) with `Fixed(child.preferredHeight(width))` before calling `LayoutEngine`. The engine stays a pure solver over constraints and never touches a component.

A `Fit` that reaches the engine unresolved is solved as `Fill`. That covers two cases deliberately:

- **A child with no preference.** Falling back to `Fill` keeps the child visible. Resolving to `0` would make it silently vanish — the worse failure.
- **The horizontal axis.** `Constraint` cannot know its axis at construction, so rejecting horizontal `Fit` would need a runtime error at layout time. Treating it as `Fill` is predictable and documented.

## Alternatives Considered

### A. A separate `Measurable` trait

**Rejected.** Containers would need `case m: Measurable =>` type tests to ask a child for its height, and every consumer widget would choose between two shapes of the contract. Every earlier Layer 6 addition to `Component` (`handleEvent`, `focusable`, `childLayouts`) went in as a defaulted method for the same reason: the tree walk treats all nodes uniformly.

### B. `Fit` resolved inside `LayoutEngine`

**Rejected.** The engine would have to receive components or measurement callbacks, coupling Layer 3 to Layer 4. Substitution in the container keeps the dependency pointing downward.

### C. Preferred width alongside preferred height

**Deferred.** No consumer needs it yet. Adding `preferredWidth` later is a non-breaking defaulted method.

## Consequences

### Positive

- `ScrollView` can measure any tree built from library components.
- Forms can be written without hand-counted `Fixed` heights: `Fit -> TextInput` takes the field's natural three rows, border included, and stays right if the border or padding changes.
- Wrapped text slots in by overriding one method.

### Negative

- **Re-measurement cost.** A nested `VBox` asks its children during its own `preferredHeight` and again during `childLayouts`, so measurement grows with the square of nesting depth. Negligible at form scale (about 50 rows, three levels deep). Memoisation is deferred until a consumer measures a cost.
- **`Fill` children share, they do not each get their own preference.** A `VBox` laid out at its preferred height leaves a `Fill` residual equal to the sum of its `Fill` children's preferences, split equally. Use `Fit` where each child needs its own height.

### Neutral

- `Text` now truncates and aligns by column width (`Widths.truncate` / `Widths.stringWidth`) rather than `String.length`. This was a latent bug the viewport's CJK clipping specs exposed.

## References

- `src/main/scala/component/Component.scala` (`preferredHeight`)
- `src/main/scala/component/Container.scala` (`resolveFit`, `verticalContribution`, `HBox` / `VBox` overrides)
- `src/main/scala/layout/Constraint.scala` (`Fit`), `src/main/scala/layout/LayoutEngine.scala` (`fitAsFill`)
- `src/test/scala/component/PreferredHeightSpec.scala`
