# Writing Idiomatic Scala 3 (3.3 LTS → 3.9 LTS → 3.10 Next): A Guidance Document for Developers and AI Assistants

**Bottom line:** Idiomatic Scala 3 in late 2026 is "lean Scala." Use the least powerful feature that solves the problem. Use immutable data and pure functions by default. Model the domain with `enum`s, case classes and opaque types. Return errors as values. Write only Scala 3 syntax (no Scala 2 `implicit`, no `A with B`, no `_` wildcards). Treat the effect system (Cats Effect, ZIO, or direct-style Ox) as a team-level architectural decision, not a per-file stylistic one. Martin Odersky's "Lean Scala" manifesto (his 11 April 2024 blog post, which says "Lean Scala should jump ahead and drop all Scala 2 legacy constructs"), Li Haoyi's "Principle of Least Power", and the Scala 3.9/3.10 release notes all point in the same direction.

## TL;DR

- **Default to simple, Scala-3-only code.** In his "Lean Scala" post (April 2024), Odersky defines lean code as "simple and understandable… as concise as possible without losing clarity. It avoids lingo, over-abstraction, and obscure features." His manifesto asks for the Principle of Least Power, immutability "without being dogmatic," Scala 3 constructs only, the core language over DSLs, and direct style. Scala 3.10 (12 Oct 2026) now *rejects* legacy syntax such as `A with B`, so emitting Scala 2 idioms is no longer just bad style. In some cases it does not compile.
- **Make illegal states unrepresentable and errors explicit.** Use `enum`/sealed ADTs for "one of", case classes for "all of", opaque types plus smart constructors for validated primitives, and `Option`/`Either` (or an effect's typed error channel) for expected failures. Reserve exceptions for defects. Use `boundary`/`break` instead of `return`.
- **Pick tooling and an effect stack deliberately, and let the compiler enforce discipline.** Target 3.9 LTS for libraries and 3.10 (Next) for applications. Enable `-deprecation -feature -Wunused:all -Wvalue-discard -Wnonunit-statement` (or `-Wall`) plus `-Werror` in CI. Choose Cats Effect 3.7.x, ZIO 2.1.x, or Ox 1.x per team. The community genuinely disagrees on effect systems and on braces vs. indentation, so standardize locally and enforce with scalafmt.

---

## 1. Where Scala 3 stands (November 2026)

| Line | Current version | Role |
|---|---|---|
| Scala 3.3 LTS | 3.3.9 (28 Oct 2026)\[1\] | Previous LTS; maintained for one year after 3.9.0\[2\] (the 3.3.9 announcement lists end of service as "2027 Sep") |
| Scala 3.9 LTS | 3.9.0 (3 Sep 2026)\[3\] | New LTS; "recommended baseline for library authors and conservative production users"; maintained at least three years\[2\] (end of service "2029 Sep or later" per the 3.3.9 announcement's LTS table) |
| Scala 3.10 Next | 3.10.0 (12 Oct 2026)\[4\] | First Next release after 3.9; starts removing legacy Scala 2 syntax; 3.11 expected ~March/April 2027\[4\] |

**What this means in practice:**
- **Applications:** use the latest Next (3.10.x). It is source-breaking only for deprecated syntax and stays binary compatible with earlier Scala 3 libraries.\[4\]
- **Libraries:** publish against 3.9 LTS. Artifacts built with 3.9 cannot be consumed by 3.3 projects.\[2\]
- **JDK:** Scala 3.8+ requires JDK 17.\[2\]
- **Ecosystem gotcha:** The Scala 3.10 release post says ZIO users must upgrade to ZIO 2.1.26+, zio-blocks 0.0.54, izumi-reflect 3.0.10 and neotype 0.7.2 before moving to 3.10. The reason is that stronger constant folding during inlining broke some macros, and 3.10 also "checks that a macro expansion has the type the macro claimed."
- **Scala 2 cross-building:** cross-compilation with Scala 2 is not supported on 3.10. According to the 3.10 release post, "it is expected that cross-compiled libraries will remain on Scala 3.9 LTS indefinitely."

**Features stabilized since 3.0 that change "idiomatic" code:**
- **3.3:** `boundary`/`break`; linting (`-Wunused`, `-Wvalue-discard`)\[5\]\[6\]
- **3.4:** Legacy syntax warnings (`_` wildcards, `private[this]`, `var x = _`, `with` as a type operator, `xs: _*`, trailing `_` eta-expansion); refutable `for` patterns need `case`\[2\]
- **3.6:** New context-bound and `given` syntax (SIP-64); clause interleaving\[2\]
- **3.7:** Named tuples (SIP-58) are stable. Given resolution now picks the *most general* instance. Passing explicit arguments to `implicit` parameters warns unless you write `using`. `-Wunused` was rewritten with far fewer false positives.\[2\]
- **3.8:** JDK 17. `.runtimeChecked` replaces `: @unchecked`. "Better fors" (SIP-62) is on by default. The standard library is compiled with Scala 3.\[2\]
- **3.9:** `into` for opt-in implicit conversions is stable (SIP-71). Deprecation warning for inaccessible companion implicits.\[2\]
- **3.10:**
  - Removed: `A with B` (now an error), `this` at top level, inaccessible companion givens.\[4\]
  - Stable: SIP-67, so pattern matching under `strictEquality` no longer needs `CanEqual`.\[4\]
  - Preview (`-preview`): `@unroll`, relaxed lambda syntax, referenceable package objects.\[4\]

**Still experimental (do not use in production code by default):**
- Capture checking and separation checking. Scala 3.8 shipped a capture-annotated standard library, but the feature remains opt-in and evolving.\[7\]\[8\]
- Explicit nulls (`-Yexplicit-nulls`).\[9\]
- Dedented string literals and specialized traits (3.10).\[4\]

---

## 2. The Scala mentality

### 2.1 Principle of Least Power

**Rule:** Given a choice of solutions, pick the least powerful one that solves the problem. Odersky's own example: "implicit conversions or macros are very powerful constructs. One should use them only if simpler alternatives are not sufficient."\[10\]

```scala
// Prefer: a plain function parameter
def retry[A](times: Int)(op: () => A): A = ...

// Avoid: a type-class + given + extension machinery for a one-off need
trait Retryable[F[_]]:
  extension [A](fa: F[A]) def retry(times: Int): F[A]
```

**Why:** Li Haoyi's argument is that "when programming in Scala, Complexity is your Enemy."\[11\] Less power means a reader can predict what a value or function *cannot* do.\[12\] Scala's static types also make it cheap to upgrade to a more powerful abstraction *later*, when it is actually needed: "In the Scala programming language, you should not fear refactoring."\[11\] The escalation ladder is:
1. Plain values
2. Functions
3. Case classes/enums
4. Traits with implementations
5. Type classes
6. Givens/context functions
7. Inline/match types
8. Macros

### 2.2 Expression-oriented, immutable by default

**Rule:** Every construct (`if`, `match`, `try`, blocks) is an expression. Bind results with `val` instead of mutating a `var`.

```scala
// Prefer
val shipping =
  if order.total >= Money(100) then Money.zero
  else Money(5)

// Avoid
var shipping = Money(5)
if order.total >= Money(100) then shipping = Money.zero
```

**Why:** The `val` version has exactly one assignment, so the reader never has to trace control flow to learn its value. Li Haoyi: "If something can be straightforwardly expressed in an immutable style, do it."\[13\] Odersky's phrasing matters too: immutable first "without being dogmatic."\[10\] A local `var` inside a tight loop whose mutation never escapes the method is acceptable. Odersky: "allow effects if they are well contained and described."\[10\]

### 2.3 Fusion of OO and FP

**Rule:**
- Use OO for *modules* (traits as service interfaces, classes with constructor dependencies).
- Use FP for *data and logic* (immutable ADTs, pure functions, pattern matching).
- Don't force everything into one paradigm.

```scala
// Module: OO
trait OrderRepository:
  def find(id: OrderId): Option[Order]
  def save(order: Order): Unit

// Logic: FP, pure, trivially testable
object Pricing:
  def total(lines: List[OrderLine]): Money =
    lines.foldLeft(Money.zero)((acc, l) => acc + l.price * l.qty)
```

**Why:** Traits and classes give Scala a strong module system with encapsulation and substitution. Immutable data and pure functions give local reasoning. Using classes for data (mutable entities with setters, as in Doctrine/Symfony) or type-class encodings for every service both fight the language.

### 2.4 Direct style is the current direction, and also a debate

Odersky's lean-Scala list says Scala "should focus on direct style. Monadic effect systems shine in some areas but they are also a kind of DSL, which creates specialized eco-systems and dialects."\[10\]

This is the language designer's position, not a community consensus. Typelevel and ZIO users argue that monadic effects provide guarantees (referential transparency, programs-as-values, safe cancellation) that direct style does not yet match.\[14\]\[15\] Section 7 presents both positions.

### 2.5 Types as a design tool, but not type-level wizardry

**Rule:**
- Encode *domain invariants* in types: opaque types, ADTs, non-empty collections.
- Don't encode *everything*: no match types, HKT towers or Shapeless-style generic programming in application code unless a library forces it.

**Why:** Types that mirror the domain catch real bugs and document intent. Types that mirror category theory or compile-time computation impose cognitive cost on every reader and slow compilation. Scala 3.10 now meters this cost explicitly: deep recursive type work spends compiler "fuel" (`-Xmax-fuel`, default 550) and fails with `Recursion limit exceeded`.\[4\]

---

## 3. Scala 3 language features: when and how

### 3.1 Enums and ADTs

**Rule:** Use `enum` for closed sets of alternatives, both simple enumerations and data-carrying ADTs. Use `sealed trait` + `case class` when cases need their own methods, separate files, or different supertypes.

```scala
enum PaymentStatus derives CanEqual:
  case Pending, Authorized, Captured, Refunded

enum Payment:
  case Card(last4: String, expiry: YearMonth)
  case BankTransfer(iban: Iban)
  case Voucher(code: VoucherCode, amount: Money)

def describe(p: Payment): String = p match
  case Payment.Card(last4, _)       => s"Card ending $last4"
  case Payment.BankTransfer(iban)   => s"Transfer from ${iban.masked}"
  case Payment.Voucher(code, amt)   => s"Voucher $code ($amt)"
```

**Why:**
- The compiler checks exhaustiveness. Add a case and every non-exhaustive `match` warns, which `-Werror` turns into an error. This is the single biggest safety win over PHP's string/int constants or class hierarchies.
- `enum` replaces Scala 2's `sealed abstract class` + `case object` boilerplate and `scala.Enumeration`.

**Avoid:** `scala.Enumeration`, stringly typed statuses, and `Boolean` flags that combine into impossible states.

### 3.2 Opaque types (zero-cost newtypes)

**Rule:** Wrap primitives that carry domain meaning (IDs, emails, amounts) in opaque types with a validating constructor.

```scala
object domain:
  opaque type Email = String
  object Email:
    private val Pattern = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$".r
    def parse(raw: String): Either[String, Email] =
      if Pattern.matches(raw) then Right(raw.toLowerCase)
      else Left(s"invalid email: $raw")
    extension (e: Email) def value: String = e
```

**Why:**
- Outside the defining scope, `Email` and `String` are distinct types. You cannot pass a `UserId` where an `OrderId` is expected, and the only way to create an `Email` is `Email.parse`.\[16\]\[17\]
- At runtime it *is* a `String`: no boxing, unlike case-class wrappers or Scala 2 `AnyVal` classes.\[18\]
- Keep smart constructors pure. Uniqueness checks requiring I/O belong in the service layer.\[19\]

**Avoid:** `type Email = String`. A transparent alias provides no safety.

**Caveat:** Opaque types don't auto-derive codecs or `apply`/`unapply`. That is a long-standing community complaint.\[18\] Libraries such as neotype fill the gap. Pick one approach per codebase.

### 3.3 Extension methods

**Rule:** Use `extension` to add methods to types you don't own, or to keep data types small. Never use implicit classes.

```scala
extension (s: String)
  def toSlug: String = s.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-")
```

**Why:**
- The intent is explicit, and there is no hidden wrapper allocation.
- Scala 3.10's standard library is converting its own `Rich*` implicit classes into extension methods.\[4\]
- The Scala 3.10 release notes (citing scala/scala3 issue #26851) warn: "If your library defines -> through an implicit class, Predef.-> may now be chosen instead… A -> defined as an extension method still takes precedence." Implicit classes are therefore actively hazardous.

### 3.4 `given` / `using` (contextual abstractions)

**Rule:**
- Use `given`/`using` for exactly two things: **type-class instances** and **genuinely ambient context** (e.g., an execution context, a transaction, a capability).
- Pass ordinary dependencies (repositories, clients, config values) explicitly via constructors.

```scala
trait Show[A]:
  def show(a: A): String

object Show:
  given Show[Int] = _.toString
  given [A: Show] => Show[List[A]] =             // 3.6+ syntax
    xs => xs.map(summon[Show[A]].show).mkString("[", ", ", "]")

def log[A: Show](a: A): Unit = println(summon[Show[A]].show(a))
```

**Why:**
- The Scala 3 redesign "focuses on **intent** rather than **mechanism**." `given` provides, `using` requires, `extension` adds methods, and `Conversion` converts.\[20\]
- The reference is blunt: "Any book or courseware that mentions `implicit` is outdated."\[21\]
- Since 3.7, passing an explicit argument to an `implicit` parameter warns unless the call site says `using`. Since 3.8, standard-library calls also need `using` (e.g., `Array.empty(using ClassTag.Int)`).\[2\]

**Placement rule:**
- Put instances in the **companion object** of the type or the type class, so they are found without imports.
- Don't hide givens in `private` companions. Scala 3.10 no longer finds them.\[4\]

**Anti-pattern: givens as a service locator**
```scala
// Avoid
def placeOrder(cmd: PlaceOrder)(using repo: OrderRepo, mailer: Mailer, clock: Clock, cfg: Config): Order
```
Four `using` parameters hide the real dependency graph, make call sites unreadable, and break when two values of the same type exist (e.g., two `String` configs). Use constructor injection instead (Section 8).

### 3.5 Implicit conversions: almost never

**Rule:** Don't define implicit conversions. If an API must accept several input shapes, use overloads, a type class, or (since 3.9) the `into` modifier, which confines conversion to marked parameters.

```scala
// 3.9+: conversion allowed only where the API author opted in
into trait Html
def render(body: Html): String = ...
given Conversion[String, Html] = s => Html.text(s)
```

**Why:** Undisciplined conversions were the main reason Scala 2 code was "magic." Scala 3 requires `import scala.language.implicitConversions` at use sites.\[22\] `into` (SIP-71, stable in 3.9) lets an API author mark exactly which parameters accept conversions, "without asking all users to enable implicit conversions globally."\[2\]

### 3.6 Type-class derivation (`derives`)

**Rule:** Derive mechanical instances (JSON codecs, `CanEqual`, `Show`, `Eq`) with `derives`. Hand-write instances only when behavior is custom.\[23\]

```scala
case class Customer(id: CustomerId, name: String, email: Email)
  derives Codec.AsObject, CanEqual    // e.g. circe
```

**Why:** `derives` is visible on the type declaration, unlike Scala 2's invisible auto-derivation through imports. Readers and compile times both benefit.

### 3.7 Union and intersection types

**Rule:**
- Use unions (`A | B`) for ad-hoc "either of these" at boundaries, especially error types and nullable Java values.
- Use intersections (`A & B`) for capability/trait combination.
- Use `&` instead of `with`: `with` in types is a compile error in 3.10.\[4\]

```scala
enum ValidationError:
  case Blank(field: String)
  case TooLong(field: String, max: Int)
enum NotFound:
  case Customer(id: CustomerId)

def register(cmd: Register): Either[ValidationError | NotFound, Customer] = ...
```

**Why:** Unions let you compose error types from different modules without a shared super-ADT. **But** prefer a named `enum` for a domain's own errors: unions are anonymous, so they don't document intent or support companion methods.

### 3.8 Named tuples (stable since 3.7)

**Rule:** Use named tuples for *local, ephemeral* structured results (query projections, multi-value returns). Use case classes for anything that crosses a module boundary or carries invariants.

```scala
def stats(xs: List[Int]): (min: Int, max: Int, mean: Double) =
  (min = xs.min, max = xs.max, mean = xs.sum.toDouble / xs.size)

val s = stats(List(3, 1, 4))
println(s.max)
```

**Why:** `s.max` beats `s._2`, and you avoid declaring a throwaway case class. Named tuples have no methods, no companion and no nominal identity, so they are the wrong tool for domain types.

### 3.9 Other Scala 3 features: short verdicts

| Feature | Use it when | Avoid when |
|:---|:---|:---|
| Top-level definitions | Package-wide helpers, type aliases | You'd reference `this` (error in 3.10)\[4\] |
| `export` clauses | Composing an object's API from parts (facade/delegation) instead of inheritance | Re-exporting entire libraries |
| Trait parameters | A trait needs construction-time config | Deep trait-param hierarchies |
| Creator applications (`Foo(x)` without `new`) | Always: this is the Scala 3 default | n/a |
| `@main` methods | Scripts, small apps | n/a |
| Context functions (`A ?=> B`) | DSLs/capabilities (transactions, `Ox` scopes) | Ordinary business code |
| Match types | Library-level type computation | Application code (fuel limits, poor errors) |
| `inline` / macros | Performance-critical library code, compile-time validation | "Clever" app-level shortcuts; macros must type-check exactly in 3.10\[4\] |
| `CanEqual` / `strictEquality` | Codebases wanting equals type safety (pattern matching no longer needs instances since 3.10)\[4\] | Mixed teams new to Scala (opt-in friction) |
| Explicit nulls | Experimenting on Java-heavy edges | Production default (still opt-in; flexible types for Java interop)\[9\]|
| `.runtimeChecked` | Intentionally partial pattern bindings (3.8+)\[2\]| Replace with total matches where possible |


### 3.10 Syntax: braces vs. significant indentation (genuine disagreement)


**The facts:**
- Scala 3 supports both styles. The official docs and the Scala 3 Book default to the indentation style with `then`/`do`, and Odersky argues indentation is natural for developers from Python, Haskell and F#.\[24\]\[25\]
- A vocal part of the community disagrees. Alexandru Nedelcu (Typelevel/Monix author) wrote in "Scala 3 / No Indent" (alexn.org, 26 Oct 2025) that he is "switching back to braces." He says "I still find myself unable to efficiently copy/paste blocks of code with significant indentation" and notes that scalafmt "chokes on invalid syntax." His advice: "Take a stand and reject significant indentation." Some projects enforce braces with `-no-indent`.

**Recommendation:**
- Pick one style per repository and enforce it mechanically.
- For a new team, the official indentation style is fine. Use end markers (`end methodName`) for blocks longer than ~15 lines, and enforce with scalafmt (`runner.dialect = scala3`, `rewrite.scala3.convertToNewSyntax = true`, `rewrite.scala3.removeOptionalBraces = yes`).
- If the team is braces-first, use `-no-indent` plus scalafmt `removeOptionalBraces = false`.\[26\]
- **Always** use the new control syntax (`if … then`, `while … do`, `for … do/yield`) regardless.
- **Never mix styles** within a file.

---

## 4. Domain modeling

### 4.1 Make illegal states unrepresentable

```scala
// Avoid: 2^3 combinations, most invalid
case class Order(paid: Boolean, shipped: Boolean, trackingNo: Option[String])

// Prefer: only valid states exist
enum Order:
  case Draft(lines: NonEmptyList[OrderLine])
  case Paid(lines: NonEmptyList[OrderLine], payment: PaymentRef)
  case Shipped(lines: NonEmptyList[OrderLine], payment: PaymentRef, tracking: TrackingNo)
```

**Why:**
- The flag version allows `shipped = true, paid = false, trackingNo = None`, and every consumer must defend against it.
- In the enum version, a function taking `Order.Shipped` *cannot* receive an unshipped order, and `tracking` is not optional.
- State transitions become total functions: `def ship(o: Order.Paid, t: TrackingNo): Order.Shipped`.

### 4.2 Smart constructors: validate once, at the edge

```scala
final case class Quantity private (value: Int)
object Quantity:
  def from(n: Int): Either[String, Quantity] =
    Either.cond(n > 0 && n <= 1000, Quantity(n), s"quantity out of range: $n")
```

**Why:** Validated types let the core skip re-validation ("parse, don't validate").\[27\]

**Tip:** In Scala 3, a `private` constructor on a case class also makes the synthesized `apply` and `copy` private, closing the Scala 2 hole where `copy` bypassed validation. For primitives, prefer opaque types (3.2) because they are zero-cost.

### 4.3 Accumulating validation errors

For user-facing forms, use accumulating validation so the user sees *all* errors:
- `ValidatedNec` / `parMapN` in Cats
- `ZIO.validate` / `zio-prelude` `Validation` in ZIO
- or a simple `List[Error]` fold in plain Scala

Switch to fail-fast `Either` inside business logic.\[19\]

**Why:** Fail-fast at the HTTP boundary frustrates users. Accumulation inside dependent steps is meaningless, because step 2 needs step 1's result.

### 4.4 Case class vs. enum vs. class

- **Case class:** immutable data with structural equality ("all of these fields").
- **Enum / sealed trait:** "one of these alternatives."
- **Plain `class`:** things with identity or encapsulated state, i.e. services, resources and actors.

Don't put service dependencies inside case classes, and don't give case classes `var` fields.

---

## 5. Error handling

### 5.1 Decision table

| Situation | Use | Why |
|---|---|---|
| Value may be absent, absence isn't an error | `Option[A]` | No reason needed |
| Expected, recoverable business failure | `Either[DomainError, A]` (or effect's typed error) | Caller must handle; error is data |
| Wrapping a throwing Java/legacy API | `Try` / `Either.catchNonFatal` at the boundary, convert immediately | Contain exceptions where they originate |
| Bug, broken invariant, infrastructure failure | Exception (let it propagate) | Not meaningfully recoverable locally |
| Early exit from a loop/computation | `boundary`/`break` | Replaces deprecated non-local `return`\[28\] |

### 5.2 Either with for-comprehensions

```scala
enum CheckoutError:
  case EmptyCart
  case OutOfStock(sku: Sku)
  case PaymentDeclined(reason: String)

def checkout(cart: Cart): Either[CheckoutError, Receipt] =
  for
    lines   <- Either.cond(cart.lines.nonEmpty, cart.lines, CheckoutError.EmptyCart)
    _       <- inventory.reserve(lines)
    payment <- payments.charge(cart.total)
  yield Receipt(lines, payment)
```

**Why:**
- The signature tells callers exactly what can go wrong, and pattern matching on the `enum` is exhaustive.
- With exceptions, PHP/Symfony-style, the failure modes are invisible in the type, and forgetting a `catch` is silent.

### 5.3 `boundary` / `break` instead of `return`

```scala
import scala.util.boundary, boundary.break

def firstOverdue(invoices: List[Invoice], today: LocalDate): Option[Invoice] =
  boundary:
    for inv <- invoices do
      if inv.dueDate.isBefore(today) then break(Some(inv))
    None
```

**Why:**
- `return` inside a lambda is a *non-local return*, deprecated since 3.2. It is implemented by throwing an exception that "a catch-all exception handler can intercept."\[28\]
- `boundary`/`break` is explicit about scope, and "breaks to enclosing scopes in the same method can be rewritten to jumps."\[29\]

For simple cases like this one, prefer `invoices.find(_.dueDate.isBefore(today))`, which is the least-power solution.

### 5.4 Errors in effect systems

- **ZIO** separates *failures* (expected, typed `E`, created with `ZIO.fail`) from *defects* (unexpected `Throwable`, created with `ZIO.die`). `catchAll` "only can recover from *failures*."\[30\]\[31\]
- **Cats Effect `IO`** has a `Throwable` error channel. Typed domain errors are usually modeled as `IO[Either[E, A]]` or `EitherT`.
- **Ox** (direct style) recommends exceptions for defects and `Either` for application errors, with a `boundary`-based `.ok()` syntax for unwrapping `Either`s.

The principle holds across ecosystems: **expected errors are values; defects are exceptions.**

### 5.5 Anti-patterns

- `throw` inside pure domain logic: the callers' types lie.
- `Option` used for errors: the *reason* is lost.
- `Try` in domain signatures: it erases error types to `Throwable`.
- `.get` on `Option`/`Either`/`Try`: it reintroduces exceptions.
- Catching `Throwable`: it swallows `InterruptedException`, fatal errors and `boundary.Break`. Use `NonFatal`.

---

## 6. Functional programming practices

### 6.1 Purity and referential transparency

**Rule:** Domain functions should depend only on their inputs and return values without side effects. Push I/O, time and randomness to the edges and pass them in.

```scala
// Avoid: hidden dependency on the clock
def isExpired(token: Token): Boolean = token.expiresAt.isBefore(Instant.now())

// Prefer: deterministic, testable
def isExpired(token: Token, now: Instant): Boolean = token.expiresAt.isBefore(now)
```

**Why:** The pure version is testable without mocks, and you can replace a call with its result (referential transparency), which makes refactoring safe.

### 6.2 Collections

- Default to immutable `List`, `Vector`, `Map`, `Set`. Use `Vector`/`ArraySeq` for indexed access and `List` for head/tail recursion and prepend.
- Use combinators (`map`, `filter`, `collect`, `foldLeft`, `groupMapReduce`, `partition`) instead of index loops.
- For performance-critical inner loops, local mutable builders or `Array` are fine if they don't escape.

```scala
// Prefer
val revenueByCustomer: Map[CustomerId, Money] =
  orders.groupMapReduce(_.customerId)(_.total)(_ + _)
```

**Why:** It is one declarative line instead of a mutable map plus a loop, and it is easy to verify.

### 6.3 Pattern matching

- Match on ADTs exhaustively. Avoid a catch-all `case _` on sealed types, because it silently swallows newly added cases.
- Use guards sparingly. Use `.runtimeChecked` (3.8+) only where partiality is intentional and documented.\[2\]

### 6.4 Avoid `var`, `null`, `return`, `asInstanceOf`, `isInstanceOf`

| Avoid | Prefer | Why |
|---|---|---|
| `null` | `Option`, or `T \| Null` at Java edges | Null is invisible in types |
| `return` | Expressions, `boundary`/`break` | Non-local return semantics, deprecated |
| `var` (escaping) | `val`, folds, `Ref` in effect systems | Shared mutable state breaks reasoning and concurrency |
| `asInstanceOf` | Pattern matching / type classes | Unchecked casts defeat the type system |
| `Any`/`AnyRef` in APIs | Generics, ADTs | Loses all static information |

### 6.5 Recursion

Use `@tailrec` on self-recursive functions so the compiler *proves* stack safety. Usually, prefer a fold or a library combinator over hand-written recursion.

```scala
import scala.annotation.tailrec
@tailrec def gcd(a: Int, b: Int): Int = if b == 0 then a else gcd(b, a % b)
```

### 6.6 Discarded values are bugs

Enable `-Wvalue-discard` and `-Wnonunit-statement`.\[32\] In effect code, `IO.println("x"); doNext` silently drops the first effect.\[33\] The compiler catches this only if these flags are on. Gabriel Volpe called the missing lint a blocker for FP teams adopting Scala 3.\[34\] It arrived in 3.3.\[6\]

---

## 7. Effects and concurrency

### 7.1 The options in late 2026

| Stack | Style | Status | Strengths | Costs |
|---|---|---|---|---|
| **Cats Effect 3.7.x** (+ fs2, http4s, doobie) | Monadic `IO`, tagless-final optional | Mature; 3.7.1 is the sixty-first 3.x release, fully binary compatible across 3.x\[35\] | Referential transparency, rich ecosystem, Scala.js/Native | Learning curve; `Throwable` error channel |
| **ZIO 2.1.x** | Monadic `ZIO[R, E, A]` | Mature; 2.1.27 latest\[36\] | Typed errors, built-in DI (`ZLayer`), batteries included | Large API surface; own ecosystem |
| **Ox 1.x** (SoftwareMill) | Direct style on JDK 21+ virtual threads | 1.0 released; requires JDK 21+ and Scala 3\[37\] | Plain code, normal stack traces, structured concurrency, channels/flows\[38\]\[39\] | No programs-as-values; JVM only |
| **Kyo** | Algebraic effects | 1.0.0-RC7 (Sep 2026), pre-1.0\[40\] | Arbitrary effect channels, performance\[41\] | Not yet 1.0; fast-moving |
| **Gears** (EPFL) | Direct-style async, cross-platform | 0.3.1, self-described "experimental"\[42\] | Research direction for capabilities | Not for production |
| **`Future`** | Eager, callback-based | Standard library | No dependency | See 7.2 |

### 7.2 Why `Future` is a poor default

```scala
val f = Future(sendEmail(user))
Future.sequence(List(f, f))           // sends ONE email
Future.sequence(List(Future(sendEmail(user)), Future(sendEmail(user)))) // sends TWO
```

**Why:**
- `Future` is eager and memoized.\[43\] It "doesn't describe an execution, it executes." Extracting a `val` changes program behavior, which breaks refactoring.\[44\]
- `Future` has no cancellation, and retries require re-invoking a producer.\[45\]
- By contrast, Cats Effect `IO` "values are pure, immutable values and thus preserve referential transparency," and IO "is like a safer, lazy version of Future."\[43\]\[46\]\[47\]

Use `Future` only at the boundaries of libraries that require it (e.g., Play/Pekko), and convert immediately.

### 7.3 How to choose (a decision, not a neutral menu)

- **Team new to Scala, typical CRUD/integration backend, JDK 21+:** direct style (Ox, or plain code on virtual threads with sttp/tapir) plus `Either` errors. Closest to PHP-style imperative clarity and aligned with Odersky's lean-Scala direction.
- **Heavy concurrency, streaming, resource safety, cancellation; team willing to learn FP:** Cats Effect (if you value the Typelevel ecosystem and minimalism) or ZIO (if you want typed errors and built-in DI out of the box).
- **Never mix two effect systems in application code.** Interop layers exist, but two mental models double the review burden.

### 7.4 Principles that hold everywhere

1. Structured concurrency: child tasks never outlive their scope (`Ox` `supervised`, CE `Resource`/`Supervisor`, ZIO `Scope`).
2. Acquire/release resources with a bracket-style construct (`Resource`, `ZIO.acquireRelease`, `useInScope`/`Using`), never with manual `try/finally` scattered around.
3. Don't block compute pools. Use `IO.blocking`/`ZIO.attemptBlocking`, or virtual threads.
4. Keep effects at the edges; keep domain logic pure regardless of stack.
5. Make concurrency explicit (`par`, `parMapN`, `zipPar`) rather than accidental (as in eager `Future` creation).

---

## 8. Architecture and project structure

### 8.1 Functional core, imperative/effectful shell

```
modules/
  domain/       # pure: ADTs, opaque types, validation, business rules. No I/O deps.
  application/  # use cases: orchestrate domain + ports (traits)
  infra/        # adapters: DB, HTTP clients, Kafka, implementing ports
  http/         # endpoints (e.g. tapir), JSON codecs
  app/          # main, wiring, config
```

**Why:** The domain module compiles fast, tests without mocks, and can't accidentally depend on infrastructure, because the build graph forbids it. This maps directly onto the hexagonal/ports-and-adapters architecture familiar from Symfony.

### 8.2 Package organization

- Package by feature (`billing`, `orders`), not by layer (`models`, `services`), within each module.
- Put type-class instances in companions, use top-level definitions for package-wide aliases, and keep one public ADT per file.

### 8.3 Dependency injection

**Default: plain constructor injection, wired by hand in `main`.**

```scala
final class OrderService(repo: OrderRepository, payments: PaymentGateway, clock: Clock):
  def place(cmd: PlaceOrder): Either[CheckoutError, Order] = ...

@main def run(): Unit =
  val config   = AppConfig.load()
  val db       = Database(config.db)
  val repo     = PostgresOrderRepository(db)
  val payments = StripeGateway(config.stripe)
  val service  = OrderService(repo, payments, Clock.systemUTC())
  HttpServer(service).start()
```

**Why:** No container, no reflection, no annotations.\[48\] The wiring is ordinary code that the compiler checks and the IDE navigates. Coming from Symfony's container, this is the biggest mindset shift: **in Scala you rarely need a DI container.**

**Alternatives, in increasing power:**
- **MacWire** (`wire[T]`, Scala-3 `autowire[T](...)`): generates the same constructor calls at compile time ("zero-cost, compile-time, type-safe"). Useful when the graph gets large.\[49\]\[50\]
- **ZLayer** (ZIO): "a full solution to the dependency injection problem" that is effectful, resourceful, parallel and compile-time checked. Use it if you're on ZIO. ZIO notes that using it "is optional."\[48\]
- **Cats Effect `Resource` composition:** build the graph in a `for` over `Resource`s in `IOApp`.
- **`given`-based DI:** avoid for services (see 3.4). Reserve `using` for type classes and ambient context.

---

## 9. Tooling and quality

### 9.1 Build tools

- **sbt:** the default for multi-module production builds. sbt 2.x is arriving, and Scala CLI can now export to it.\[2\]
- **Scala CLI** (the `scala` command since 3.5): scripts, prototypes and single-module projects via `//> using` directives. Its `lts` tag now resolves to 3.9.\[2\]\[4\]
- **Mill** (Li Haoyi): Scala-code builds, fast and simple. Mill 1.1 adds YAML/single-file modes.\[51\] A good fit for teams who find sbt opaque.

### 9.2 Recommended compiler flags (application on 3.10)

```scala
scalacOptions ++= Seq(
  "-deprecation", "-feature", "-unchecked",
  "-Wunused:all",          // imports, privates, locals, params, implicits
  "-Wvalue-discard",       // discarded non-Unit values
  "-Wnonunit-statement",   // non-Unit expression in statement position
  "-Werror"                // CI: warnings fail the build (aka -Xfatal-warnings)
  // "-source:future"      // optional: opt in to upcoming rules early
  // "-Yexplicit-nulls"    // optional/experimental: only if team accepts friction
)
```

**Why:**
- Each flag catches a real bug class: dead code, dropped effects, deprecated syntax that will become an error.
- `-Wall` (3.5.2+, backported to 3.3.5+) is a shorthand.\[52\] Some projects use it instead of the individual flags.\[53\]
- Relax `-Wnonunit-statement` in tests, where assertions often return values.\[53\]\[54\]
- To run scalafix `RemoveUnused`, temporarily disable `-Werror` so warnings don't fail the build before scalafix runs.\[52\]

### 9.3 Migrating legacy code

Run the compiler rewrites in order, committing after each: `-source:3.4-migration -rewrite`, then 3.5, 3.6, 3.7, 3.8 and 3.9. They fix `_` wildcards, `with` → `&`, `xs: _*` → `xs*`, inserting `using`, and `: @unchecked` → `.runtimeChecked`, among others. The Scala 3.9 LTS announcement (3 Sep 2026) reports that "about 1,780 of nearly 2,000 projects build on Scala 3.9 with no or minimal changes, mostly thanks to the compiler's built-in rewrites," based on Open Community Build results.

### 9.4 scalafmt and scalafix

```hocon
# .scalafmt.conf
version = "3.x"                       # pin the exact version
runner.dialect = scala3
maxColumn = 120
rewrite.scala3.convertToNewSyntax = true
rewrite.scala3.removeOptionalBraces = yes   # or `false` for a braces team
rewrite.scala3.insertEndMarkerMinLines = 15
rewrite.rules = [RedundantBraces, RedundantParens, SortModifiers, Imports]
```

Use scalafix with `RemoveUnused` and `OrganizeImports` in CI. Format on save in Metals or IntelliJ.

### 9.5 Testing

- **munit:** minimal, fast, plain assertions. A good default for new Scala 3 projects and in line with "core language over DSLs."
- **ScalaTest:** feature-rich, many styles. Odersky cites its "fluid syntax" as an example of DSL-induced fragmentation,\[10\] so pick *one* style (e.g., `AnyFunSuite`).
- **weaver-test:** Cats Effect-native, parallel by default.
- **ZIO Test:** for ZIO stacks.
- **Property-based testing:** ScalaCheck (munit-scalacheck), and `zio-test` generators. Use it for pure domain functions: round-trips (`decode(encode(x)) == x`), invariants and smart constructors.

```scala
class QuantitySuite extends munit.ScalaCheckSuite:
  property("valid range always parses"):
    forAll(Gen.choose(1, 1000)) { n => Quantity.from(n).isRight }
```

### 9.6 Metals / IDE

Metals (VS Code, Neovim) and IntelliJ both support Scala 3 well. Since 3.5, best-effort compilation keeps IDE features working through compile errors.\[2\] Rely on inlay hints for inferred types and implicit arguments, which are invaluable for understanding given resolution.

---

## 10. Anti-patterns catalog

| Anti-pattern | Example | Why it's worse | Fix |
|---|---|---|---|
| Scala 2 syntax in Scala 3 | `implicit class`, `implicit val`, `A with B`, `List[_]`, `xs: _*`, `private[this]` | Deprecated since 3.4; `with` is an error in 3.10; signals stale code\[2\]\[4\] | `extension`, `given`, `A & B`, `List[?]`, `xs*`, `private` |
| Givens as service locator | `def f(using Repo, Mailer, Clock, Config)` | Hidden graph, ambiguity, unreadable call sites | Constructor injection |
| Over-abstraction | `F[_]: Monad: Logger: Clock` tagless final for a single-implementation CRUD app | Every reader pays for unused polymorphism | Concrete `IO`/`ZIO`/direct style; abstract only at real seams |
| Type-level wizardry in app code | Match types / Shapeless-style generic derivation for a DTO mapper | Slow compiles, opaque errors, fuel limits | Write the mapping by hand or use `derives` |
| Exceptions in pure code | `if qty < 0 then throw IllegalArgumentException()` | Invisible failure, caller can't see it in the type | Smart constructor returning `Either` |
| Primitive obsession | `def transfer(from: String, to: String, amount: BigDecimal)` | Arguments swappable, no validation | Opaque types `AccountId`, `Money` |
| Boolean/Option soup | `case class User(isAdmin: Boolean, isGuest: Boolean, …)` | Impossible states representable | `enum Role` |
| Catch-all on sealed types | `case _ => ...` | Defeats exhaustiveness checking | List all cases |
| Mixing paradigms poorly | `Future` + `IO` + blocking JDBC in one service | Three concurrency models, deadlocks, lost errors | One effect model; wrap blocking calls |
| Implicit conversions for convenience | `given Conversion[String, UserId]` | Silent type holes | Explicit constructor; `into` only for intentional APIs |
| DSL overuse | Custom symbolic operators (`|+|>`) in business code | Reader must learn a dialect | Named methods |
| `Option.get`, `.head`, `asInstanceOf` | `users.find(...).get` | Runtime exceptions | `match`, `fold`, `getOrElse`, `headOption` |

---

## 11. Guidance specifically for an AI coding assistant (Claude)

Odersky explicitly anticipated the problem: LLM-generated code "is more likely to use old Scala 2 patterns" because of training-set bias, so it "will have to be augmented by our own tooling."\[10\] These rules counter that bias.

**Generation defaults:**
1. Target the project's declared Scala version. Absent one, assume Scala 3.9 LTS / 3.10 semantics and JDK 17+.
2. Emit only Scala 3 syntax: `given`/`using`/`extension`/`enum`/`?`/`&`/`xs*`/`@main`/`if…then`/`for…do`, and creator applications without `new`.
3. Match the repository's brace/indentation style. If none exists, use indentation style with end markers for long blocks.
4. Always annotate return types of public methods. Inference is for locals.

**Design choices:**
5. Choose the least powerful construct: function > case class/enum > trait > type class > given > inline > macro. Justify every step up in a comment.
6. Model "one of" with `enum`, "all of" with case classes, and domain primitives with opaque types plus `Either`-returning constructors.
7. Return `Either[DomainError, A]` (or the project's effect error channel) for expected failures. Never `throw` in domain code. Never use `null`, `return`, `.get`, or `asInstanceOf` in new code.
8. Use constructor injection for services. Use `given`/`using` only for type-class instances and ambient context.
9. Detect and follow the project's effect system (imports of `cats.effect`, `zio`, `ox`). Never introduce a second one. Never introduce `Future` into an IO/ZIO codebase.

**Hygiene:**
10. Never leave discarded values, unused imports or unused parameters. The code must pass `-Wunused:all -Wvalue-discard -Wnonunit-statement -Werror`.
11. Don't use experimental features (capture checking, explicit nulls, preview SIPs) unless the build already enables them.
12. When uncertain whether an API exists in the targeted version, prefer the standard library and say so, rather than inventing a method.
13. Provide a test (munit or the project's framework) for each pure function added. Use a property-based test for parsers and smart constructors.

---

## 12. Paste-ready ruleset (CLAUDE.md / skill)

```markdown
# Scala 3 rules

## Version & syntax
- Scala 3 only (3.9 LTS for libraries, 3.10+ Next for apps). JDK 17+.
- NEVER emit Scala 2 idioms: no `implicit` (use given/using/extension/Conversion), no `A with B` (use `A & B`),
  no `_` type wildcard (use `?`), no `xs: _*` (use `xs*`), no `private[this]`, no `var x = _`,
  no `scala.Enumeration`, no `new` for case classes/classes with apply, no procedure syntax.
- Use `if … then`, `while … do`, `for … do/yield`. Follow repo's brace/indent style; never mix in a file.
- Explicit return types on all public members.

## Design
- Principle of Least Power: function > case class/enum > trait > type class > given > inline > macro.
- Immutable by default; `val` over `var`; local, non-escaping mutation only when measurably needed.
- "One of" → enum/sealed trait; "all of" → case class; primitives with meaning → opaque type + smart constructor.
- Make illegal states unrepresentable; prefer total functions over runtime checks.
- Exhaustive pattern matches; no `case _` on sealed types.
- Named tuples only for local results; case classes for anything crossing module boundaries.

## Errors
- Expected failures: Option (absence) / Either[DomainErrorEnum, A] / effect typed error.
- Exceptions only for defects; wrap throwing Java APIs at the boundary (Try/Either.catchNonFatal), convert immediately.
- No null, return, .get, .head on possibly-empty, asInstanceOf. Use boundary/break for early exit.
- Catch NonFatal, never Throwable.

## Contextual abstractions
- given/using only for type-class instances and ambient context. Services via constructor parameters.
- Instances live in companion objects (public). Use `derives` for mechanical instances.
- No implicit conversions; `into` only for deliberately designed APIs.

## Effects & architecture
- Detect project's effect system (cats-effect | zio | ox | plain). Never add a second one. No Future in IO/ZIO code.
- Pure domain core; I/O, time, randomness passed in or at the edges.
- Structured concurrency and bracket-style resource handling; never block compute pools.
- Constructor DI wired in main (MacWire/ZLayer/Resource only if already used).

## Quality
- Must compile cleanly with: -deprecation -feature -Wunused:all -Wvalue-discard -Wnonunit-statement -Werror.
- Format with project scalafmt (dialect scala3). No experimental features unless build enables them.
- Add munit/ScalaCheck (or project framework) tests for new pure logic; property tests for parsers/constructors.
- When unsure an API exists in the target version, use the standard library and say so.
```

---

## Caveats

- **Braces vs. indentation** and **monadic vs. direct style** are unresolved community debates. This guide recommends *local consistency*, not a universal winner. Odersky's lean-Scala list is explicitly a proposal: "No doubt that list needs to be discussed and refined."\[10\]
- **Fast-moving pieces:** Kyo is pre-1.0 (RC7)\[40\]\[55\] and Gears is experimental.\[42\] Capture checking, separation checking and explicit nulls remain experimental in 3.9/3.10, and names inside capture checking changed during 2026.\[8\] Don't build production code on them yet.
- **Version-specific details:** Some details (e.g., exact Cats Effect 3.7.1 and ZIO 2.1.27 release dates) come from release pages and mirrors whose dates were partially inferred. Verify versions with Scaladex before pinning.
- **Library-specific syntax:** Code examples use Scala 3.6+ given syntax and 3.7+ named tuples. On 3.3 LTS, use `given … with` and case classes instead. Library-specific examples (circe `Codec.AsObject`, munit-scalacheck `property`) assume those libraries' current Scala 3 APIs and should be checked against your versions.

## Sources

1. [All Available Versions](https://www.scala-lang.org/download/all.html)
2. [Scala 3.9 LTS released!](https://www.scala-lang.org/news/3.9/)
3. [Scala 3.9.0](https://www.scala-lang.org/download/3.9.0.html)
4. [Scala 3.10 released!](https://scala-lang.org/news/3.10/)
5. [Control Structures](https://docs.scala-lang.org/scala3/book/control-structures.html)
6. [Scala 3.3.0 released!](https://scala-lang.org/blog/2023/05/30/scala-3.3.0-released.html)
7. [Scala 3.8 released!](https://www.scala-lang.org/news/3.8/)
8. [Scala 3 Capture Checking, Explained Without the Type Theory — Scala Teams](https://www.scalateams.com/blog/scala-3-capture-checking-explained)
9. [Edit this page on GitHub](https://cs-214.epfl.ch/scala-3.7.2-docs/docs/reference/experimental/explicit-nulls.html)
10. [Lean Scala](https://odersky.github.io/blog/2024-04-11-post.html)
11. [Strategic Scala Style: Principle of Least Power](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower.html)
12. [Hands-on Scala: Foreword](https://www.handsonscala.com/foreword.html)
13. [Strategic Scala Style: Principle of Least Power](http://www.lihaoyi.com/post/StrategicScalaStylePrincipleofLeastPower)
14. [What don't you use with scala - Question - Scala Users](https://users.scala-lang.org/t/what-dont-you-use-with-scala/10388)
15. [Scala Space: "Professor Martin @odersky disa…" - softwaremill.social](https://softwaremill.social/@scala_space/112275594713372969)
16. [Flix Part 2: Smart Constructors \[software\] :: Prevent invalid data from entering your system :: quant developer, …](https://www.susanpotter.net/software/flix-part-2-smart-constructors/)
17. [This is a very simple example of the newly introduced Opaque Type Alias in Scala 3](https://github.com/yadavan88/opaque-type-sample)
18. [Improve opaque types - Language Design - Scala Contributors](https://contributors.scala-lang.org/t/improve-opaque-types/4786)
19. [Smart Constructors in Scala 3: Validate Once, Trust Everywhere - DEV Community](https://dev.to/aranadedoros/smart-constructors-in-scala-3-validate-once-trust-everywhere-96g)
20. [Contextual Abstractions](https://docs.scala-lang.org/scala3/book/ca-contextual-abstractions-intro.html)
21. [Contextual Abstractions](https://docs.scala-lang.org/scala3/reference/contextual/)
22. [Scala's Implicits: The Feature So Powerful It Had to Be Split in Two - Java Code Geeks](https://www.javacodegeeks.com/2026/01/scalas-implicits-the-feature-so-powerful-it-had-to-be-split-in-two.html)
23. [Type Class Derivation — Scala for Backend Engineering & Functional Programming](https://www.coddykit.com/courses/scala/type-class-derivation-7745466)
24. [Scala 3 Syntax Rewriting](https://docs.scala-lang.org/scala3/guides/migration/tooling-syntax-rewriting.html)
25. [github.com](https://github.com/scala/scala3/pull/7083)
26. [Scala 3 / No Indent - Alexandru Nedelcu](https://alexn.org/blog/2025/10/26/scala-3-no-indent/)
27. [Scala-Examples/opaque-types.md at main · janbodnar/Scala-Examples · GitHub](https://github.com/janbodnar/Scala-Examples/blob/main/opaque-types.md)
28. [Deprecated: Nonlocal Returns](https://docs.scala-lang.org/scala3/reference/dropped-features/nonlocal-returns.html)
29. [boundary](https://scala-lang.org/api/3.3_LTS/scala/util/boundary$.html)
30. [Three Types of Errors](https://zio.dev/reference/error-management/types/)
31. [Catching](https://zio.dev/reference/error-management/recovering/catching/)
32. [Functional Programming anti-patterns in Scala](https://scalajobs.com/blog/functional-programming-anti-patterns-in-scala)
33. [Compiler lint: catch discarded effects on Scala 3 and clear the 2.13 warning noise · Issue #580 · EtaCassiopeia/zio-openfeature · GitHub](https://github.com/EtaCassiopeia/zio-openfeature/issues/580)
34. [Scala 3: the missing compiler plugin - Gabriel Volpe](https://gvolpe.com/blog/scala3-missing-compiler-plugin/)
35. [typelevel/cats-effect v3.7.1 on GitHub](https://newreleases.io/project/github/typelevel/cats-effect/release/v3.7.1)
36. [Releases · zio/zio · GitHub](https://github.com/zio/zio/releases)
37. [Ox 0.x documentation - SoftwareMill](https://ox.softwaremill.com/v1.0.4/index.html)
38. [Scaladex - softwaremill / ox](https://index.scala-lang.org/softwaremill/ox)
39. [github.com](https://github.com/softwaremill/ox)
40. [Releases · getkyo/kyo · GitHub](https://github.com/getkyo/kyo/releases)
41. [Overview](https://getkyo.io/latest/)
42. [Scaladex - lampepfl / gears](https://index.scala-lang.org/lampepfl/gears)
43. [cats-effect - cats.effect.IO](https://typelevel.org/cats-effect/api/2.x/cats/effect/IO$.html)
44. [S. Derosiaux](https://www.sderosiaux.com/articles/2018/08/23/are-scala-futures-the-past/)
45. [Scala Futures and ExecutionContext: Eager Async, Blocking — Belgavi.AI Lab](https://aicassindra.com/blogs/scala/scala_futures_ec.html)
46. [IO · Cats Effect](https://typelevel.org/cats-effect/docs/2.x/datatypes/io)
47. [package effect](https://typelevel.org/cats-effect/api/3.x/cats/effect/index.html)
48. [Introduction to Dependency Injection in ZIO | ZIO](https://zio.dev/reference/di/)
49. [Autowire: Zero-Cost Dependency Injection](https://softwaremill.com/autowire-zero-cost-dependency-injection/)
50. [softwaremill/macwire](https://deepwiki.com/softwaremill/macwire)
51. [Scala Times, weekly Scala newsletter by SoftwareMill](https://scalatimes.com/de3b4c1a55)
52. [RemoveUnused · Scalafix](https://scalacenter.github.io/scalafix/docs/rules/RemoveUnused.html)
53. [Hold the code to discarded values, with -Wall on Scala 3 by eshu · Pull Request #171 · h8io/stages · GitHub](https://github.com/h8io/stages/pull/171)
54. [Use the standard compiler options, and fix every warning by rchillyard · Pull Request #31 · rchillyard/Gryphon · GitHub](https://github.com/rchillyard/Gryphon/pull/31)
55. [Comparing effect systems in Scala: Kyo, Gears, and Ox](https://virtuslab.com/blog/scala/comparing-effect-systems-in-scala-kyo-gears-and-ox)
