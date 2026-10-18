# ADR-008: Four-Module Build and Source Paths

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-10-18  
**Deciders**: ws-console core  
**Source task**: `.claude/tasks/library-packaging.md` (decision D2)  
**Implemented in**: commit `3a9ef22` (2026-10-08)  
**Affects**: the source paths cited by ADR-001 to ADR-006

---

## Context

Until 2026-10-08 the build was one sbt module. Everything shipped in one jar: the library, the demo application (`Main` and `demo.*`), and, through `Test / publishArtifact := true`, every spec as a `tests` classifier. A consumer that wanted the test harnesses (`FrameHarness`, `RenderHarness`, `CaptureTerminal`) had to depend on that classifier and add `zio-test` itself.

Publishing the library (`library-packaging.md`) needed the library jar to hold only the library, and the harnesses to ship as a dependency of their own. The split that delivered this moved every source file in the repository.

ADR-001 to ADR-006 were written before the split. Their references cite `src/main/scala/...` and `src/test/scala/...`, paths that no longer exist. ADRs record a decision at a moment and are not rewritten, so those citations need a rule for reading them.

## Decision

### Four modules

| Module | Contents | Published | Depends on |
|---|---|---|---|
| `core` | The library: `ansi`, `app`, `buffer`, `component`, `event`, `geometry`, `glyph`, `layout`, `render`, `terminal`, `unicode` | yes, `ws-console` | zio, zio-streams |
| `testkit` | `CaptureTerminal`, `AnsiGrid`, `GridAssertions`, `FrameHarness`, `RenderHarness` as main sources; their own specs as test sources | yes, `ws-console-testkit` | `core`; `zio-test` at compile scope |
| `tests` | Every spec for `core`, mirroring its packages | no | `core`, `testkit % Test` |
| `demo` | `Main`, `demo.*`, and the demo specs | no | `core`, `testkit % Test` |

The root project aggregates the four and is not published, so `sbt test` and `sbt publishLocal` at the root reach every module.

### Dependency direction

- `core` depends on no other module. A library type can never import the testkit or the demo; sbt enforces this at compile time.
- `testkit` depends on `core`. Its public API is built on `zio-test`, so `zio-test` is a compile dependency of the testkit. Consumers add the testkit `% Test`, which keeps `zio-test` off their runtime classpath.
- `tests` and `demo` depend on `core`, and on `testkit` for their specs only.

### Why `tests` is a module

The core specs use the testkit, and the testkit depends on `core`. Keeping the specs in `core` would make `core` depend on `testkit` for tests while `testkit` depends on `core`: a project cycle. sbt rejects it (verified 2026-10-08 in a scratch sbt 1.10.11 build: `StackOverflowError` during project evaluation). The specs therefore live in their own unpublished module.

### Build-wide checks span every module

A check over "all sources" discovers the modules instead of naming one tree. `ControlByteHygieneSpec` walks the `src` directory of every module under the build root and asserts that `core/src/main/scala/ansi/Csi.scala` is among the files scanned, so a check that silently scans nothing fails. `scripts/run-demo.sh` runs the `demo` module's runtime classpath.

### Reading paths in ADR-001 to ADR-006

The split moved files without renaming packages. A path cited by ADR-001 to ADR-006 maps to its current location by prefix:

| Cited as | Now at |
|---|---|
| `src/main/scala/<package>/...`, for every library package | `core/src/main/scala/<package>/...` |
| `src/main/scala/demo/...`, `src/main/scala/Main.scala` | `demo/src/main/scala/demo/...`, `demo/src/main/scala/Main.scala` |
| `src/test/scala/testkit/...` (the harnesses) | `testkit/src/main/scala/testkit/...` |
| `src/test/scala/testkit/...` (the harnesses' own specs) | `testkit/src/test/scala/testkit/...` |
| `src/test/scala/demo/...` | `demo/src/test/scala/demo/...` |
| `src/test/scala/<package>/...`, for every library package | `tests/src/test/scala/<package>/...` |

For example, ADR-005's `src/test/scala/component/PreferredHeightSpec.scala` is `tests/src/test/scala/component/PreferredHeightSpec.scala`, and ADR-006's `src/main/scala/render/RevealPass.scala` is `core/src/main/scala/render/RevealPass.scala`.

A cited line number (`Panel.scala:50`) refers to the file as it stood when that ADR was accepted. Read it against the history at the ADR's date, not against the current file.

### Citing paths from now on

ADRs written after the split cite paths from the repository root, module first (`core/src/main/scala/...`, `tests/src/test/scala/...`), as ADR-007 does. A later move that changes a path an accepted ADR cites is recorded the same way, in a new ADR, never by editing the ADR it affects. A move that no ADR cites needs no record here: `Graphemes` and `Widths` moved from `buffer` to `glyph` on 2026-10-09, and no ADR names them by path.

## Alternatives Considered

### A. Keep one module and publish everything

**Rejected.** The library jar would keep shipping the demo application, and consumers would keep resolving the harnesses through a `tests` classifier that carries every spec and leaves `zio-test` for them to add.

### B. Core specs in `core/src/test`, depending on the testkit

**Rejected.** A project cycle: `core` would depend on `testkit` for tests while `testkit` depends on `core`. sbt fails to load the build.

### C. Specs that do not use the testkit stay in `core/src/test`

**Rejected.** Where a spec lives would depend on one import. Specs gain and lose testkit imports as they change, so the split would drift with every edit.

### D. The testkit as part of `core`'s main sources

**Rejected.** The testkit's API is built on `zio-test`, which would become a runtime dependency of the library for every consumer, test or not.

### E. Rewrite the paths in ADR-001 to ADR-006

**Rejected.** An ADR records a decision at a moment; when reality moves, a new ADR records the move, as this one does. Six edited records would also no longer match their dates. One mapping, read once, keeps every earlier citation resolvable.

## Consequences

### Positive

- The published library jar holds only the library: no demo, no specs.
- Consumers depend on `ws-console-testkit % Test` to get the harnesses with `zio-test`, nothing more.
- Module boundaries are enforced by the build: no library code can reach the testkit or the demo.
- Every citation in ADR-001 to ADR-006 still resolves, through the table above.

### Negative

- **Specs live away from their sources.** A spec for `core/src/main/scala/component/Select.scala` is `tests/src/test/scala/component/SelectSpec.scala`. The mirrored package layout keeps them findable.
- **Module-qualified commands.** Running one spec names its module: `sbt "tests/testOnly *SelectSpec"`.
- **Two path conventions in the ADR set.** ADR-001 to ADR-006 cite pre-split paths and need the mapping above; ADR-007 onwards cite current ones.

### Neutral

- Package names did not change. Only file locations moved, so imports and fully qualified names in ADRs, docs and code are unaffected.

## References

- `build.sbt` (the module definitions and their dependency comments)
- `.claude/tasks/library-packaging.md` (D2, the four-module build; Phase 2, the module split)
- `tests/src/test/scala/ansi/ControlByteHygieneSpec.scala` (module discovery)
- `scripts/run-demo.sh` (the `demo` module's classpath)
- Commit `3a9ef22`, "Split the build into core, testkit, tests and demo modules"
