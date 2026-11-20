# ADR-011: Retire RenderOptimizer

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-11-20  
**Deciders**: ws-console core  
**Supersedes**: ADR-009 L6 Q2 (`docs/adr/adr-009-pre-adr-decisions.md`)  
**Source**: doctrine audit of 2026-11-20, decision D5

---

## Context

ADR-009 L6 Q2 records that the `RenderOptimizer` trait exists "so a consumer can swap in selective dirty-region tracking". It ships two implementations: `alwaysDirty`, the default, and `regionTracking`, a skeleton that accumulates rects in a `Ref`.

The 2026-11-20 audit found two problems:

1. **Nothing consults it.** Outside its own file and its own spec, no source in the repository names `RenderOptimizer` or calls `shouldRedraw`. `RenderPipeline`, `RenderLoop` and `Application` neither take an optimizer nor ask one anything. A consumer who swaps in `regionTracking` changes nothing.
2. **It competes with ADR-003.** ADR-003 ratifies the invalidation model: invalidation sources feed a coalescing accumulator that holds an `Option[Rect]` bounding region (Q1). `regionTracking` models the same concept as a `Chunk[Rect]` of "dirty" rects, under the vocabulary ADR-003 rejects. Two designs for one concept, and only one of them is ratified.

## Decision

Delete `render/RenderOptimizer.scala` and `RenderOptimizerSpec`. ADR-009 L6 Q2 is superseded.

When selective re-rendering is needed, it is built as ADR-003's accumulator, under the invalidation vocabulary, and wired into the pipeline in the same change that introduces it.

## Alternatives Considered

### A. Keep the trait as an extension point and rename it to invalidation terms

Rejected. An extension point the pipeline never calls has no reach, and the rename would fix the `Chunk[Rect]` model before ADR-003's accumulator settles the shape.

### B. Wire `RenderOptimizer` into the pipeline

Rejected. It would ship a second region model beside the ratified one.

## Consequences

### Positive

- One region model remains: ADR-003's.
- The "dirty" vocabulary leaves the tree, which makes ADR-003's vocabulary claim true.

### Negative

- Breaking for any consumer that names `RenderOptimizer`. Since nothing calls it, no behaviour changes. Ships in 0.3.0.

## References

- `docs/adr/adr-003-invalidation-source-taxonomy.md` (Q1, Terminology)
- `docs/adr/adr-009-pre-adr-decisions.md` (L6 Q2)
- `.claude/commissar-audits/2026-11-20-repository.md` (D5)
