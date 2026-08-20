# Event Handling Guide

Reference material for the ws-console event layer and the application framework above it. This document is the authority on how an event reaches a component, what a component may say in response, and who acts on that answer.

## Scope

Covers the `Event` ADT and parser in `event/`, the dispatch and focus machinery in `render/`, and the consumer-facing event surface in `app/`. Rendering is covered by the component style guide; this guide touches it only where an event causes a frame.

---

## 1. The Event Surface

The typed representation of terminal input. Produced by `EventParser` from raw bytes and delivered as a `ZStream[Any, IOException, Event]`.

```scala
sealed trait Event

object Event:
  final case class Resize(width: Int, height: Int) extends Event

sealed trait KeyEvent extends Event

object KeyEvent:
  final case class CharKey(char: Char, modifiers: Set[KeyModifier])             extends KeyEvent
  final case class SpecialKey(key: SpecialKeyCode, modifiers: Set[KeyModifier]) extends KeyEvent

sealed trait MouseEvent extends Event
```

| Type             | Cases | Notes                                                     |
|------------------|-------|-----------------------------------------------------------|
| `KeyEvent`       | 2     | `CharKey`, `SpecialKey`                                   |
| `KeyModifier`    | 3     | `Ctrl`, `Alt`, `Shift`                                    |
| `SpecialKeyCode` | 26    | 4 arrows, 4 navigation, 6 editing, 12 function keys       |
| `Event.Resize`   | 1     | Emitted by the render loop's size poll, not by the parser |
| `MouseEvent`     | 0     | Reserved — cases arrive with mouse support (§9)           |

**Control bytes.** C0 bytes surface as `CharKey(letter, Set(Ctrl))` — byte `0x03` becomes `CharKey('c', Set(Ctrl))`. Four bytes are special-cased to `SpecialKey` instead: Tab, Enter, Backspace, Escape. In raw mode `Ctrl+C` is a parsed event, never a signal.

**Lone `ESC`.** After an `ESC` byte the driver reads with a 50 ms timeout to tell a lone Escape from an alt-prefixed key. On timeout the parser flushes `SpecialKey(Escape)`.

---

## 2. The Answer Channel

This is the center of the guide. Everything else follows from it.

A component that handles an event must be able to say what should happen next. `EventResult` is that vocabulary.

```scala
sealed trait EventResult

object EventResult:
  case object Ignored       extends EventResult
  case object Consumed      extends EventResult
  case object RequestRedraw extends EventResult

  final case class Perform(effect: ZIO[Frame, IOException, Unit]) extends EventResult
```

### 2.1 The Rule

The four cases partition by **scope of consequence** — how far outside itself the component's answer reaches.

| Return            | Scope                | Meaning                                    | Typical use                             |
|-------------------|----------------------|--------------------------------------------|-----------------------------------------|
| `Ignored`         | Nothing              | Not mine — offer it to my parent           | Any unmatched key                       |
| `Consumed`        | Propagation only     | Mine; nothing further happens              | Swallowing a key so a parent cannot act |
| `RequestRedraw`   | My own local state   | Mine; I changed myself and need repainting | Scroll offset, input cursor, selection  |
| `Perform(effect)` | The world outside me | Mine; the framework must run this          | Panel navigation, quit, focus change    |

Pick the narrowest case that is true. A component that only changed its own cells returns `RequestRedraw`, never `Perform(ZIO.unit)`.

`Ignored` is not inert. It is the answer the framework acts on when nobody else did — §6.3 turns an unclaimed key into a quit.

### 2.2 Why the effect is data, not execution

`Perform` carries an **unexecuted** `ZIO` value. Constructing a `ZIO` performs nothing — it is a description of work, not the work. The component never runs it; the framework does, after dispatch settles.

This is what keeps the terminal out of component hands. `Perform`'s payload is typed `ZIO[Frame, IOException, Unit]`, so a component can describe a frame-level effect and nothing wider. `Terminal` appears in no component signature, and a component executes nothing itself.

### 2.3 `Perform` implies a redraw

A `Perform` result always schedules a frame. There is no separate "perform without redrawing" case.

The near-universal case is that an effect changes something visible. Where it does not, Layer 2's diff absorbs the cost — an unchanged buffer emits no bytes. Paying for a rare no-op diff is cheaper than making every consumer reason about a redraw flag.

### 2.4 Bubbling is unaffected

`Perform` is a non-`Ignored` result, so it stops propagation under the standing rule: the dispatcher walks up the parent chain and returns the first result that is not `Ignored`.

A parent therefore cannot observe or wrap a child's `Perform`. If a parent needs to participate, the child must return `Ignored` and let the parent decide.

---

## 3. The Component Contract

```scala
def handleEvent(event: Event, ctx: RenderContext): EventResult = EventResult.Ignored
```

Defaults to `Ignored` — a component opts in to interactivity by overriding.

### 3.1 What a handler may and may not do

The contract is **synchronous, no ZIO environment, no I/O — local mutable state permitted**.

A component owns its transient state (animation phase, scroll offset, input cursor, selection index) and may change it in place while handling an event. What it may not do is perform I/O, touch the terminal, or execute an effect. The handler returns an answer; it does not carry it out.

`Perform` sits inside this contract rather than breaking it: building a `ZIO` value is neither I/O nor execution.

### 3.2 Focus guarding

A focusable component guards its handlers on the focus snapshot rather than caching focus locally. The `RenderContext` is captured once at the dispatch boundary and holds for the whole bubble walk, so focus cannot flip mid-dispatch.

A component that skips this guard handles the key wherever focus happens to be, which reads to the user as one widget stealing another's input.

### 3.3 Actions arrive by constructor

A component does not know *what* its action means — only *when* it fires. The action is supplied at construction and stored unexecuted.

```scala
final class ToolbarButton private (
  val label:  String,
  onActivate: ZIO[Frame, IOException, Unit]
) extends Component:

  override val focusable: Boolean = true

  override def handleEvent(event: Event, ctx: RenderContext): EventResult =
    if !ctx.focus.isFocused(id) then EventResult.Ignored
    else
      event match
        case SpecialKey(SpecialKeyCode.Enter, _) => EventResult.Perform(onActivate)
        case CharKey(' ', _)                     => EventResult.Perform(onActivate)
        case _                                   => EventResult.Ignored

object ToolbarButton:
  def make(label: String, onActivate: ZIO[Frame, IOException, Unit]): UIO[ToolbarButton] =
    ZIO.succeed(new ToolbarButton(label, onActivate))
```

Constructed with the meaning bound in:

```scala
quitBtn <- ToolbarButton.make("Quit", app.quit)
```

Detection and consequence live in one place. The alternative — a widget raising a flag that the application polls after every unmatched event — splits them across two files, and the polling cost grows with every widget added.

---

## 4. Components That Do Not Handle Events

Most components never handle an event, and that is the expected case. `handleEvent` defaults to `Ignored`, so a component is inert until it overrides. Reach for the override only when the component has both of the things below; anything less belongs to a parent or to the application.

**A component handles events when it owns a piece of interaction state.** A scroll offset, a caret position, a selection index — something the user moves and the component draws. If a component draws only what it is handed, it has nothing to change and no reason to see the event.

**A component handles events when it can name its own answer.** `Text`, `Spacer`, `ProgressBar`, and `Spinner` cannot: a key means nothing to them. `HBox` and `VBox` cannot either — they split geometry and hold no state a key could move. They stay inert and let events bubble past.

Three cases look like exceptions and are not:

- **A container that wants to react to a child's key.** It does not override `handleEvent` to intercept. The child returns `Ignored` for keys it does not own, and the event reaches the parent through the normal bubble walk.
- **A component that needs an effect run.** It does not run one. It returns `Perform` and the framework runs it — see §2.2.
- **`RawCanvas`.** The callback paints; it does not receive events. Interaction inside a raw canvas means building a real component.

The gain from staying inert is the same as the focus guard's: a component that never sees an event can never swallow one that belonged to something else.

---

## 5. Dispatch

### 5.1 Routing

| Event type   | Target                                                      |
|--------------|-------------------------------------------------------------|
| `KeyEvent`   | The focused component; the root when nothing holds focus     |
| `MouseEvent` | *Reserved* — the component whose rect contains the cursor    |
| `Resize`     | Not delivered to components; handled by the loop and the app |

From the target, the event bubbles up the parent chain, stopping at the first non-`Ignored` result.

### 5.2 Loop order

One event produces this sequence:

1. If `Resize` — reconstruct the buffer (`Frame.resize`) before anything observes the event
2. Snapshot focus into a `RenderContext`
3. Dispatch: walk from the focus target up the parent chain
4. **If the result is `Perform(effect)` — run the effect**
5. Call the application's `onEvent(event, result)`
6. Stop if `onEvent` returned `false`, or if the result was `Ignored` and the event is in `quitOn`
7. Redraw if the event was a `Resize`, or the result was `RequestRedraw` or `Perform`

Step 4 sits **before** step 5 so that the application callback observes a world in which the component's action has already taken place. A button that navigates has navigated by the time `onEvent` sees the event.

Step 6 sits **after** step 5 so that nothing is hidden from the consumer. Every event reaches `onEvent`, including the keys that end the loop — see §6.3.

### 5.3 Errors propagate

A `Perform` effect may fail with `IOException`. It is not caught.

An `IOException` from a panel swap or a state write is not recoverable at this layer, and `Application.run` terminates on unhandled errors by design. Swallowing it would hide real breakage inside a widget.

### 5.4 Fiber discipline

**The effect runs on the render-loop fiber.** This is not a preference. `Panel.onUnload` writes cells directly into the live canvas, and running that from another fiber races the render walk — no amount of stack-level locking fixes it.

The consequence must be stated plainly to consumers: **a slow `Perform` stalls input.** The loop processes one signal at a time. Work that cannot complete promptly forks explicitly inside the effect, and in doing so leaves the loop's ordering guarantee. That is the consumer's decision to make knowingly.

---

## 6. The Application Surface

### 6.1 `Application.run`

```scala
def run(
  root:    Component,
  onEvent: (Event, EventResult) => ZIO[Frame, IOException, Boolean] = continueForever
): ZIO[Terminal & Frame, IOException, Unit]
```

`run` keeps `Terminal` for itself — it performs the lifecycle acquisitions. The callback does not receive it.

`onEvent` sees every event, in every case, with the dispatcher's result in hand. Returning `false` stops the loop. One callback, one ordering, nothing absorbed ahead of it.

### 6.2 What belongs at the application level

With `Perform` available, most widget behavior lives in the widget. Three concerns legitimately remain central:

- **Global shortcuts** — keys that must work regardless of focus. These cannot live in a focused component by definition.
- **Quit** — `quitOn` ends the loop on an unclaimed `Ctrl+C`. It acts last, and only on `Ignored` — see §6.3.
- **Cross-cutting policy** — anything that must observe every event, such as logging or an inspector.

Anything else belongs in a component. If the application is matching on a key to act on a specific widget, that widget should be returning `Perform`.

### 6.3 How quit works

`quitOn` is a set of keys that end the loop. It defaults to `Ctrl+C` alone, following terminal convention — in raw mode that arrives as a parsed `CharKey('c', Set(Ctrl))` rather than a signal (§1), so something has to act on it or the application cannot be closed.
	
`quitOn` is the last thing to act on an event, and it acts on one condition:

> **`quitOn` fires only when the bubble walk returned `Ignored`.**

The framework's quit binding is a consumer of the answer channel like any other, and the narrowest one — it takes the key only when no component claimed it. `Ignored` already means "nobody wanted this"; quit is what happens next.

Three consequences follow, and they are the reason the rule is worth stating on its own.

**A focused component keeps its keys.** A dialog that answers `Ctrl+C` with `Perform(close)` cancels itself instead of killing the application, because `Perform` is not `Ignored`. A text field that binds `Ctrl+C` to copy keeps it the same way. Neither has to know that the key is reserved, and `quitOn` needs no exception list.

This matters most for the keys an application adds. A TUI that puts `q` in `quitOn` and later grows a text field would find the field unusable if `quitOn` ran first — one keystroke into "quit" and the program is gone. Under this rule the focused field answers `q` with `RequestRedraw` and keeps it, with no coordination between the two features.

**Nothing is hidden from `onEvent`.** Quit keys reach the consumer callback like every other event, because the decision to stop comes after it (§5.2, step 6). An event inspector displaying `Ctrl+C` needs no special access — it reads it from `onEvent`.

**Vetoing a quit is ordinary component work.** A non-`Ignored` result is the whole of it, and that is the mechanism §2 already describes. Events travel one path — dispatch, then the application — and quit sits at the end of it.

#### Taking quit over entirely

`quitOn` serves applications with no quit semantics of their own. Where quit means more than "stop now" — confirming unsaved work, tearing down a session — set `quitOn = Set.empty` and model it as a component action:

```scala
app <- Application.make(quitOn = Set.empty)
```

A root-level component then answers `Ctrl+C` with `Perform(showConfirmPrompt)`, and the prompt's confirm button answers with `Perform(app.quit)`. Quit becomes an ordinary action on the answer channel, with the same shape as every other.

Adding keys works the same way. An application that wants `q` to quit passes it in — `Application.make(quitOn = Set(CharKey('q', Set.empty), CharKey('c', Set(Ctrl))))` — and the rule above keeps a focused text field from losing the letter.

---

## 7. Focus

### 7.1 The focus cycle

The cycle is derived, not registered. Each frame's layout walk collects every component that is `focusable` **and** has a non-empty rect, in pre-order, and hands the list to `FocusManager`. What you can Tab to is exactly what is rendered, focusable, and visible.

| Concern                                  | Owner                                                               |
|------------------------------------------|---------------------------------------------------------------------|
| When focus advances                      | The application — it calls `focusNext` / `focusPrevious`            |
| Programmatic focus                       | The application — `focusManager.focus(id)`                          |
| What happens when the focused thing goes | `FocusPolicy` — `DropOnRemoval`, `MoveToFirstOnRemoval`, or `custom` |
| The traversal order itself               | The tree — fixed pre-order, depth-first                             |

When nothing holds focus, the root is the dispatch target, so the application is the handler of last resort. An application that wants a key answered no matter where focus sits puts that answer on the root component.

Acquiring focus is not an event concern. A modal that focuses its first field on appearing does so as part of becoming visible, and this guide has nothing to say about it beyond the consequence: once focused, the modal's components are on the dispatch path and everything in §2 through §5 applies unchanged.

### 7.2 Traversal order

Traversal follows the component tree in pre-order, depth-first. There is no `tabIndex`, no focus group, no directional navigation, no skip. The only lever a consumer has over traversal order is the shape of the tree.

> **TODO:** Decide whether traversal order becomes consumer-controllable, and by what mechanism. The application decides *when* focus advances; it does not decide *how*.

---

## 8. What Goes Wrong

Six mistakes account for most of the time lost in this layer.

**Returning `Consumed` after changing state.** The component updated its scroll offset and nothing repainted. `Consumed` stops propagation and schedules no frame. If you changed something you draw, the answer is `RequestRedraw`.

**Returning `Consumed` for keys you do not own.** A handler whose fallback arm is `Consumed` rather than `Ignored` swallows every key that reaches it, `Ctrl+C` among them, and the application loses its way out. The symptom is a program that will not quit. Match the keys you handle and return `Ignored` for the rest — see §2.1 and §6.3.

**Handling a key without guarding on focus.** The widget fires wherever focus happens to be, and the user sees one widget stealing another's input. Every handler on a focusable component checks `ctx.focus.isFocused(id)` first — see §3.2.

**Reaching for `Perform` when `RequestRedraw` is true.** `Perform(ZIO.unit)` says "the world outside me changed" when nothing did. It costs a scheduled frame and it misleads the next reader. Pick the narrowest case — see §2.1.

**Expecting a parent to see a child's `Perform`.** It cannot. The first non-`Ignored` result ends the walk. A parent that needs to participate requires the child to return `Ignored` — see §2.4.

**Doing slow work inside a `Perform`.** The effect runs on the render-loop fiber and input stalls behind it. Fork inside the effect if the work cannot finish promptly, and accept that you have left the loop's ordering guarantee — see §5.4.

---

## 9. Deferred

Named so the boundaries of this document are explicit. Each is specified when it lands.

- **`EventResult` naming** — *"consumed by whom?"* is a fair objection. The vocabulary is revisited as a whole rather than one case at a time.
- **Mouse** — the ADT slot and the dispatcher's routing arm are reserved. Needs a `Terminal` mouse-tracking mode and SGR decoding.
- **Modal focus containment** — a covered panel's focusables must leave the Tab cycle. `PanelHost` exposes every panel in the stack to the layout walk, so containment needs a mechanism before `FocusPolicy`'s modal restore case can hold.
- **Bracketed paste** and **focus in/out reporting**.
- **Key chords and sequences** — no multi-key state above the parser's ESC disambiguation.
- **Temporal events** — two shapes compete. A region declares a cadence and the renderer paces it, with no `Event` representation at all; or an `Event.Tick` rides this dispatch pipeline. They are incompatible and the choice is not made.
- **Pre-dispatch interception** — a hook between parse and `handleEvent`. Global shortcuts run after dispatch until it exists, and rely on no component having consumed the key first.
- **Custom application events** — nothing outside the terminal can enter the event stream.
