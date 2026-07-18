# Known Issues

Latent issues that have been surfaced during diagnosis but are not currently scheduled for fix. Each entry records:

- **What:** the precise observable
- **Where:** the code site
- **Why we know:** how it was discovered
- **Why deferred:** what's keeping us from fixing it now
- **Trigger:** the condition under which the issue would become load-bearing

---

## KI-001 — `CellStyle.toAnsi` emits attributes in non-deterministic order

**What.** `CellStyle` holds its terminal attributes as a `Set[Attribute]`. `toAnsi` iterates this set with `foreach` to build the SGR escape sequence. Scala's `Set` does not guarantee iteration order, so two `CellStyle` instances that are `==` (structural set equality) may produce ANSI byte streams with attribute codes in different orders across instances or across JVM runs.

**Where.** `src/main/scala/buffer/CellStyle.scala` — the `toAnsi` method's iteration over `attributes: Set[Attribute]`.

**Why we know.** Surfaced during the diagnosis of `demo-focus-flicker` (see `.claude/tasks/demo-focus-flicker.md`). Both the Rogue Trader reconnaissance and the Tech-Magos hypothesis walk recorded this as a latent concern when ruling out H3 (style equality looseness).

**Why this is not currently a correctness bug.** The diff engine compares `Cell` instances by structural equality (case-class derivation over `(char, style)`). `CellStyle` equality is over `(fg, bg, attributes)` with set-equality on `attributes` — order-independent. Two `==` cells will never cause the diff to emit one and skip the other. The ANSI byte order only affects the *wire representation* of the emission, not whether the emission happens.

**Trigger conditions.** This becomes load-bearing if any of these emerge:

1. **Byte-level snapshot tests** that assert the exact ANSI sequence for a styled cell. Today no such tests exist; any future regression tests at the wire level must either canonicalise attribute order or relax the assertion.
2. **A terminal that reacts differently to attribute ordering.** Modern terminals process SGR parameters left-to-right and combine them; no current target is known to behave differently based on order. If a target emerges, the issue becomes a real bug.
3. **Performance work that interns or hash-keys ANSI strings.** A cache keyed on the full ANSI string would have a low hit rate because equivalent styles produce distinct keys.

**Why deferred.** No present consumer is affected. The fix (canonicalise iteration order — e.g., emit attributes in a fixed enum order) is small but touches a hot path, and we have no benchmark in place to confirm it stays neutral. Address when one of the trigger conditions emerges.

**Possible remediation when triggered.** Replace the `Set[Attribute].foreach` with an explicit ordered traversal over a canonical sequence — likely a constant `Vector[Attribute]` enumerating the supported attributes in their emit order, filtered by `attributes.contains`. Alternatively, switch `attributes` to a `BitSet`-equivalent (Scala enum ordinals) and iterate by ordinal.

---
