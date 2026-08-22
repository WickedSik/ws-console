# Scala 3 Evolved — Patterns Applicable to ws-console

If you're working on ws-console and wondering which Scala 3 idioms are
worth adopting here, this is the shortlist. The scalaevolved.github.io
catalogue lists 53 Scala 2 → 3 tweaks. Against this codebase (Scala
3.3.6 LTS, ZIO-native TUI library, already indent-syntax and
enum-friendly), most of them do not improve the code enough to earn
the churn.

**Evaluation criterion.** The test is "does adopting this make
ws-console better?" — not "does the modern idiom exist?" A pattern
survives only if it removes real duplication, catches a bug the code
actually hits, or reads meaningfully clearer at a site that exists.
Items that only pass because they're new are moved to *Skipped* with
the reason.

After that filter, **8 items** are worth acting on, **3** are worth
noting for later, and the rest are called out in *Skipped and why*.

Source catalogue: <https://scalaevolved.github.io> (raw data at
`data/snippets.json` in the [source
repo](https://github.com/scalaevolved/scalaevolved.github.io)).

Every "Where in ws-console" cite is a real `file:line`. Every
before/after sketch is adapted from that file, not the generic
scalaevolved example.

---

## Adopt

These pay for themselves at sites that already exist in the tree.

### Enums for ADTs (site #1)

Replace `sealed trait` + `case object` / `case class` with a single
`enum` declaration.

```scala
// Before — layout/Constraint.scala:21-31
sealed trait Constraint
object Constraint:
  final case class Fixed(size: Int) extends Constraint
  final case class Percentage(percent: Int) extends Constraint
  case object Fill extends Constraint
  final case class Bounded(min: Option[Int], max: Option[Int], inner: Constraint) extends Constraint
```

```scala
// After
enum Constraint:
  case Fixed(size: Int)
  case Percentage(percent: Int)
  case Fill
  case Bounded(min: Option[Int], max: Option[Int], inner: Constraint)
```

**Where in ws-console.** `layout/Constraint.scala`,
`event/EventResult.scala`, `event/EventParser.scala` (`ParserState`),
`terminal/RawInput.scala`, `buffer/CellStyle.scala` (`Foreground`,
`Background`), `buffer/BoxStyle.scala`,
`component/ProgressBarStyle.scala`, `ansi/Color.scala` (`AnsiColor`),
`event/Event.scala` (`Event`, `KeyEvent`, `MouseEvent`).

**Verdict.** Adopt. Removes the `final case class ... extends X`
noise on a large surface, gives derived `values`/`valueOf`/`ordinal`
for free, and enables `derives` for future typeclass work.

**Caveats.** `require`-blocks and init-time throws (Constraint's
`Bounded` rejects `Bounded(Bounded(…))`) work in `enum` case bodies
via `case Bounded(…): require(…)`, but the syntax is fussier.
Migration is not a straight swap when a case needs an init body.

### Enums with parameters (site #11)

Cases carry a shared parameter block, removing a `def`-per-case
override.

```scala
// Sketch of an enum-ified Foreground (currently sealed trait, CellStyle.scala:34)
enum Foreground(val sgr: Sgr):
  case Inherit                                 extends Foreground(Sgr.Empty)
  case Named(color: FgColor)                   extends Foreground(color.sgr)
  case Indexed(n: Int)                         extends Foreground(Sgr.fg256(n))
  case Rgb(r: Int, g: Int, b: Int)             extends Foreground(Sgr.fgRgb(r, g, b))
```

**Where in ws-console.** `Foreground` / `Background` (each case
currently defines the same `def sgr`) and `BoxStyle` (each case
forwards to a `BoxDrawing` value). The pattern is already used at
`ansi/Color.scala:12` (`FgColor(val code: Int)`) and
`buffer/CellStyle.scala:15` (`Attribute(val sgr: Sgr)`).

**Verdict.** Adopt where the pattern already exists in duplicated
`def`s. Direct removal of hand-written accessors.

**Caveats.** Init-time per-case validation needs
`case Indexed(n: Int) extends Foreground(Sgr.fg256(n)): requireByte(n, "n")`
— readable, but denser than a case-class body.

### Pattern bindings in `for` (site #34)

Destructure directly in the generator instead of `map { case … => … }`
or a `while` + tuple accessors.

```scala
// Any Seq[(Component, Rect)] iteration
for (child, rect) <- childLayouts(area) do
  child.render(rect, canvas, ctx)

// vs today's Container.scala:44-48 while-with-tuple-destructure
```

**Where in ws-console.** `component/Container.scala:44`, and anywhere
`for … yield` uses positional `_._1` / `_._2` access.

**Verdict.** Adopt at `Container.render`. The `while` loop was
written for micro-optimisation; the comprehension reads one line
cleaner and the container hot path is one render per frame — closure
cost is invisible.

**Caveats.** Leave the event parser's inner loops alone; those run
per byte.

### Drop `using DummyImplicit` from `Container` (site #2, applied)

The `DummyImplicit` at `component/Container.scala:65,83` is a Scala 2
overload-disambiguation hack. Both overloads already carry
`@targetName`, which does the same job at compile time.

```scala
// Before
def apply(children: Component*)(using DummyImplicit): HBox = ...

// After (subject to sbt compile confirming)
def apply(children: Component*): HBox = ...
```

**Verdict.** Adopt. Remove the parameter, run `sbt compile`. If it
succeeds, the parameter is dead.

**Caveats.** If `sbt compile` fails, the target names alone don't
disambiguate at every call site — restore the parameter and move on.

### Trailing-colon lambdas (site #46)

Already partially adopted — `buffer/CellStyle.scala:106`:

```scala
val withAttributes = Attribute.values.foldLeft(Sgr.Empty): (acc, attribute) =>
  if attributes.contains(attribute) then acc ++ attribute.sgr else acc
```

**Where in ws-console.** A skim of `buffer/*.scala` and `render/*.scala`
turns up a handful of multi-line `.foldLeft(z) { (a, x) => … }` or
`.map { x => … }` blocks that still use braces.

**Verdict.** Adopt when touching the surrounding code, for
consistency. Not worth a dedicated sweep.

**Caveats.** Single-expression `.map(_.value)` stays brace-free
either way. A colon-lambda nested inside an indent-bodied outer
expression can visually collide — check readability, not brace count.

### Inline methods (site #10)

Compile-time expansion for small pure helpers. Already used at
`buffer/CellStyle.scala:27`:

```scala
private inline def requireByte(value: Int, name: String): Unit =
  require(value >= 0 && value <= 255, s"$name must be 0-255, got $value")
```

**Where in ws-console.** Candidates: byte-range validators (currently
duplicated across `ansi/`, `buffer/`), and small pure helpers in
`geometry/Rect.scala` (`isEmpty`, `contains`) called from the render
path.

**Verdict.** Adopt for validators and small render-path helpers.
Skip for anything that would inline more than ~5 lines.

**Caveats.** `inline` expands code size at every call site — one
declaration line becomes N call-site lines in bytecode. `inline`
bodies also can't recurse (use `inline if` and non-inline recursive
peers).

### Creator applications — no `new` (site #14)

Universal apply methods let you drop `new` for any class with a
suitable constructor.

```scala
// Container.scala uses `new HBox(...)` internally today
def apply(items: (Constraint, Component)*): HBox = new HBox(items.toSeq)

// After
def apply(items: (Constraint, Component)*): HBox = HBox(items.toSeq)
```

**Where in ws-console.** `component/Container.scala:56,61,66,74,79,84`
and a handful of `new IOException(...)` in `app/`. Java constructors
still need `new` (`new AtomicLong(0L)` at `ComponentId.scala:21`
stays).

**Verdict.** Adopt opportunistically. Not confusing either way, but
consistency reads cleaner.

**Caveats.** Doesn't work on Java classes. Grep before assuming.

### Opaque type aliases (site #8)

Zero-cost, type-safe wrappers over primitives. Existing pattern at
`component/ComponentId.scala:18`:

```scala
opaque type ComponentId = Long
object ComponentId:
  def fresh(): ComponentId = counter.incrementAndGet()
  extension (id: ComponentId) def value: Long = id
```

**Where in ws-console.** Candidates where the same primitive appears
in many public signatures and a mix-up has burned you at least once:
`RgbChannel` (currently `Int` guarded by `requireByte`), `Cols` /
`Rows` on `TerminalSize`. `PanelId` and `Sgr` parameter codes are
weaker candidates — used in one or two places each.

**Verdict.** Adopt selectively — only where the primitive appears
across layers and unit-mix bugs are plausible. Do not wrap every
`Int`.

**Caveats.** Overuse turns the codebase into a type-tetris puzzle.
Each new opaque type adds a small tax on every conversion at the
boundary.

---

## Consider, don't rush

Real but modest — take them when you're already in the file.

### Boundary / break for early returns (site #40)

Structured non-local return without a `var found = false` flag.

```scala
import scala.util.boundary, boundary.break

def firstFocusable(components: Seq[Component]): Option[Component] =
  boundary:
    components.foreach: c =>
      if c.focusable then break(Some(c))
    None
```

**Where in ws-console.** `render/FocusManager`, `render/FocusOrder`.

**Verdict.** Consider. Readability call — a small named `def` with
an early `return`, or a `.find(...)`, is often as clear. Reach for
`boundary` when the exit condition sits mid-loop and a named helper
would over-decompose.

**Caveats.** `boundary` / `break` allocates a small `Label`. Use for
readability, not performance.

### Named tuples (site #42) *— Scala 3.7+, not on LTS*

Tuples with named fields. Case-class ergonomics with no declaration.

```scala
type Placement = (component: Component, rect: Rect)

def childLayouts(area: Rect): Seq[Placement] = ...
placements.foreach(p => p.component.render(p.rect, canvas, ctx))
```

**Where in ws-console.** `component/Container.scala:33-40` reads
positional `_._1` / `_._2` today.

**Verdict.** Not now — Scala 3.3.6 LTS doesn't have them. File under
"when the LTS bumps." Even then, weigh against a plain `case class` at
the same call sites; a two-field named tuple is a case class with less
documentation.

### Extension methods (site #3)

Already used at `layout/LayoutEngine.scala:217` and
`component/ComponentId.scala:29`. The suggestion elsewhere is
`grid.at(x, y)` reading better than `GridAssertions.at(grid, x, y)`
in `testkit/`.

**Verdict.** Consider for `testkit/` and for helpers whose receiver is
an external type (`Chunk[Byte]`, `Char`). For types you own,
extensions read the same as methods — no reason to move.

**Caveats.** Extensions on types you own gain nothing over normal
methods and split the definition across files.

---

## Skipped and why

Items rejected on review. Grouped by the reason they fail the
"does this improve ws-console?" test.

### Rejected on review

- **#13 Multiversal equality (`derives CanEqual`).** The earlier draft
  suggested this would catch cross-ADT comparisons like
  `Foreground.Inherit == Background.Inherit`. On review: `Foreground`
  and `Background` are the same concept at bottom (a color assignment
  targeting one channel or the other). Comparing them isn't obviously
  a bug — and forbidding it at compile time rules out legitimate
  operations. The cell-diff path compares like-with-like already;
  there's no cross-ADT comparison bug this code actually hits. Skip
  unless a real incident argues otherwise.

- **#4 Union types at declarations.** Pattern-matched call sites read
  worse against a bare `A | B | C` than against a named `enum`, and
  docstrings need somewhere to live. The stronger use of unions is at
  *call sites* (a paint parameter that accepts
  `FgColor | Int | (Int, Int, Int)`), not declarations. That use is
  opportunistic, not a doc-worthy pattern.

- **#15 Context functions.** `RenderContext ?=> Unit` would remove one
  parameter from every `Component.render` override. Migration cost is
  high; benefit per call site is one parameter. Adds an implicit
  receiver readers must hold in their head. Skip.

- **#12 Export clauses.** Most facade surface here is explicit
  trait/companion pairing. `export` from many sources risks name
  collisions and turns navigation into a hunt. Skip until a package
  actually wants to re-surface a whole namespace.

- **#27 groupMap / groupMapReduce.** No existing site — the earlier
  draft speculated about a future fan-out in `EventDispatcher`.
  Speculative recommendations don't earn their spot.

- **#29 Pipe / tap chaining.** `.pipe` / `.tap` on plain values is a
  style choice with no unique win here. ZIO already provides `.tap`
  where it matters. Skip.

- **#2 `given` / `using` (typeclass shape).** Zero real sites in
  production sources today. The `DummyImplicit` cleanup is the one
  actionable item, called out in *Adopt* above. Adopt `given` when a
  typeclass lands, not before.

### Rejected as not applicable

- **#6 Top-level definitions** — package-per-file convention here.
- **#7 Optional braces**, **#33 for-comprehension indent** — house
  style already.
- **#16 Type lambdas**, **#17 Polymorphic function types**, **#18
  Dependent function types** — no site in ws-console calls for them.
- **#19 Explicit implicit conversions** — no `implicit conversion`
  sites.
- **#20 Improved structural types** — no reflective/structural
  typing.
- **#21 LazyList replaces Stream** — no `Stream` usage.
- **#22 XML literals** — irrelevant.
- **#23 ArraySeq**, **#24 Improved views**, **#28 tapEach** —
  ZIO-native code doesn't touch these paths meaningfully.
- **#25 Future.transform**, **#26 Using**, **#41 Using multiple
  resources** — ZIO owns resource and async lifecycle;
  `ZIO.acquireRelease` / `ZIO.scoped` are the canonical shapes (see
  `app/Application.scala:141-158`).
- **#30 Automatic typeclass derivation**, **#31 summon**, **#32
  typeclass syntax with extensions** — no typeclasses shipped;
  revisit if any land.
- **#35 Try with pattern matching**, **#36 Either for typed errors**,
  **#37 Option instead of null**, **#38 Flatten nested Options**,
  **#39 Accumulating validation errors** — ZIO absorbs all of these.
- **#44 Capture checking**, **#45 Explicit nulls** — experimental,
  incompatible with 3.3.6 LTS ergonomics.
- **#47 Infix type operators** — already ubiquitous
  (`ZIO[Terminal & Frame, IOException, Unit]`).
- **#48 Quotes and splices** — no macros ship today.
- **#49 Transparent inline**, **#50 Compiletime operations** —
  overkill without a real macro API.
- **#51 @main annotation** — `Main.scala` uses `ZIOAppDefault`.
- **#52 Thread safety annotations** — noted; nothing on the hot path
  currently annotated, and `@unshared` is not a Scala 3 stdlib
  annotation, only convention.
- **#53 Improved type inference** — automatic, no code change
  required.

---

## Fit summary

Ordered by effort × payoff:

1. **`sealed trait` → `enum`** for the ADTs listed. Largest surface,
   smallest risk, biggest boilerplate cut.
2. **Enum parameter blocks** for `Foreground` / `Background` /
   `BoxStyle`. Direct removal of duplicated `def sgr`.
3. **Drop `using DummyImplicit`** from `Container`. One-line diff, one
   `sbt compile` to confirm.
4. **`for` with tuple destructure** at `Container.render`. Local, one
   file.
5. **Trailing-colon lambdas** when touching braced multi-line
   closures.
6. **`inline`** on byte-range validators and hot-path helpers.
7. **Creator applications** — drop `new` opportunistically.
8. **Opaque types** for `RgbChannel` / `Cols` / `Rows` — only if a
   unit-mix bug shows up.

Everything else is either "consider" or explicitly skipped.
Cognitive burden is the enemy — a shortlist that flags weak items is
more valuable than one that lists every modern idiom.
