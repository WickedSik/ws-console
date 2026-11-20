# ADR-009: Decisions Ratified Before the ADR Series

**Status**: Accepted  
**Implementation**: Complete (records existing decisions)  
**Date**: 2026-10-18  
**Deciders**: ws-console core  
**Source**: `docs/reference/terminal-architecture.md`, retired 2026-10-18 (last present at commit `3240b3f`)

---

## Context

`docs/reference/terminal-architecture.md` was written in 2025 as the structure to build the framework around. As the layers shipped, its class diagrams and interface sketches stopped describing the code, and by October 2026 most of its structured content contradicted the tree. It was retired on 2026-10-18.

Before the ADR series existed, the layer task scrolls resolved a set of open questions, and the document was the only place their rulings were written down. They are still in force, all but one, and the code depends on them. This ADR records them so they outlive the document. Each keeps its original identifier.

## Decisions

### L5 Q1/Q2: Byte-faithful key encoding (2026-05-10)

The parser surfaces every byte-level distinction the terminal exposes, and collapses two keys onto one event only when the byte stream forces it:

| Byte | Event | Note |
|---|---|---|
| `0x09` | `SpecialKey(Tab)` | Tab and `Ctrl+I` share this byte: collapse forced |
| `0x0D` | `SpecialKey(Enter)` | Enter in raw mode |
| `0x0A` | `CharKey('j', Set(Ctrl))` | `Ctrl+J`, kept distinct from Enter |
| `0x7F` | `SpecialKey(Backspace)` | Backspace |
| `0x08` | `CharKey('h', Set(Ctrl))` | `Ctrl+H`, kept distinct from Backspace |
| `0x1B` | `SpecialKey(Escape)` after 50 ms | Lone ESC; an alt-prefix sequence resolves within the window |

Consumers keep the freedom to bind `Ctrl+J` and `Ctrl+H` as hotkeys of their own. A consumer that wants "Enter is Enter" collapses the pairs at its own match site; that choice is local, not a global mode.

**Status:** in force. `event/EventParser.scala` (`idleByte`); the window is `event/TerminalEvents.LoneEscTimeout`. The `SimpleKey` / `KeyMatcher` helpers proposed alongside it are not built.

### L6 Q1: `buffer.Renderer` renamed to `buffer.Frame` (2026-05-10)

Layer 2's per-frame primitive is `Frame` (`Frame.run { canvas => ... }`), which frees the unqualified name `Renderer` for the Layer 6 orchestrator in package `render`. The two must not be conflated.

**Status:** in force.

### L6 Q2: `RenderOptimizer` ships `alwaysDirty`; region tracking is a skeleton (2026-05-10)

The `RenderOptimizer` trait exists so a consumer can swap in selective dirty-region tracking. The default, `alwaysDirty`, is correct for full-frame redraw, the only mode the pipeline uses. `regionTracking` is a skeleton, not a supported optimisation.

**Status:** superseded by ADR-011 (`docs/adr/adr-011-retire-render-optimizer.md`), which removed `RenderOptimizer`.

### L6 Q3: Single-handler dispatch with bubbling (2026-05-10)

`EventDispatcher` delivers an event to the focused component, then up its parent chain, and returns the first result that is not `Ignored`. `EventFilter` and `EventListener` are deferred until a real consumer needs several handlers per event; the composition rule for them is ratified then, not before.

**Status:** in force. Neither type exists.

### L6 Q4: Resize detection by polling (2026-05-10)

Ruled: `RenderLoop` probes `terminal.size` every 100 ms and synthesises `Event.Resize` on a change. `sun.misc.Signal` and JNA were rejected, so polling works on Windows Terminal with no reliance on JDK internals.

**Status:** **superseded in practice.** Since 2026-07-31 (`terminal/ResizeSignal.scala`) the loop installs a `SIGWINCH` handler through `sun.misc.Signal`, best-effort. With the handler in place, the 100 ms tick reads a flag and queries `terminal.size` only after a signal. Without it, the loop falls back to querying every second (`RenderLoop.ResizeFallbackPollInterval`). The objection Q4 raised still holds, which is why the handler is best-effort and polling remains the fallback; but the primary mechanism is now the one Q4 rejected. No ADR ratified that change. This record makes the code, not the 2026-05-10 ruling, the reference for resize detection.

### L7 Q3: `app.Panel` is a sibling of `Component`, not a subtype (recorded by 2026-08-02)

A panel owns a root component, a rule for the rect it renders into, and lifecycle hooks driven by `PanelHost`. Components render; panels live and die. Keeping `Panel` outside the `Component` hierarchy keeps region and lifecycle concerns out of the component model.

**Status:** in force. `app/Panel.scala`.

### Layer 7 superseded types (recorded by 2026-08-02)

The original Layer 7 design named an `EventLoop`, a `StateManager` and a `ResourceManager`. None is to be introduced:

- the loop is Layer 6's `render/RenderLoop`, which already produces frames and pumps events; a second loop would duplicate it;
- `State[S]` carries its own subscription and update surface; there is no separate coordinator;
- resource safety is `ZIO.acquireRelease` inside `ZIO.scoped`; a manager type would wrap what ZIO already guarantees.

**Status:** in force.

## Alternatives Considered

### A. Keep the architecture document for its decisions alone

**Rejected.** The decisions occupy a few paragraphs of a 2,268-line document whose other content contradicts the code. A reader cannot tell the ratified parts from the obsolete ones.

### B. Retire the document and leave the decisions in git history

**Rejected.** The code depends on these rulings today. A decision reachable only through `git log` is in practice not recorded.

### C. One ADR per decision

**Rejected.** They are already decided, several are one paragraph long, and none changes here. One record keeps them together with their shared origin; a future change to any of them gets its own ADR that supersedes the relevant section.

## Consequences

### Positive

- Every ruling the code relies on is in `docs/adr/`, the one place for ratified decisions.
- The gap between the Q4 ruling and the resize code is now recorded instead of hidden.

### Negative

- The original identifiers (L5 Q1/Q2, L6 Q1–Q4, L7 Q3) refer to task scrolls that no longer exist in the repository. They are kept so references in commit messages and older records still resolve to this ADR.

## References

- `core/src/main/scala/event/EventParser.scala`, `core/src/main/scala/event/TerminalEvents.scala`
- `core/src/main/scala/buffer/Frame.scala`, `core/src/main/scala/render/Renderer.scala`
- `core/src/main/scala/render/RenderOptimizer.scala`, `core/src/main/scala/render/EventDispatcher.scala`
- `core/src/main/scala/terminal/ResizeSignal.scala`, `core/src/main/scala/render/RenderLoop.scala`
- `core/src/main/scala/app/Panel.scala`, `core/src/main/scala/app/State.scala`, `core/src/main/scala/app/Application.scala`
- The retired document: `git show 3240b3f:docs/reference/terminal-architecture.md`
