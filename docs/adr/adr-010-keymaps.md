# ADR-010: Key Bindings as Data

**Status**: Accepted  
**Implementation**: Complete  
**Date**: 2026-11-19  
**Deciders**: ws-console core  
**Builds on**: ADR-009 (`docs/adr/adr-009-pre-adr-decisions.md`)  
**Source task**: idiomatic-Scala campaign after the 3.9.0 upgrade

---

## Context

Every interactive widget decodes keys in a pattern match inside `handleEvent`. The project rule is that matches on a sealed type are exhaustive, with no catch-all `case _`. The widgets cannot meet that rule as written:

- **Guards defeat exhaustiveness.** The compiler counts no guarded case toward exhaustiveness. A handler that checks modifiers in guards (`case SpecialKey(Left, m) if m.isEmpty`) is never exhaustive. That holds even when the guards together cover every modifier set: on Scala 3.9.0, the complementary arms `if m.isEmpty` and `if m.nonEmpty` still warn "It would fail on pattern case: Key(_, _)", and `-Werror` turns the warning into a build failure. So each handler ends in a catch-all, and twelve catch-alls across nine widgets and `OptionPopup` swallow whatever case a change adds.
- **Each widget invents its own modifier rules.** `Button` activates on `Ctrl+Enter`. `Checkbox` requires a bare `Space`. `Slider` and `RadioGroup` let `Ctrl` chords bubble but move on `Alt` and `Shift` arrows. `TextInput` lets `Ctrl` bubble only for `Home` and `End`. The styleguide describes bare keys, and the code reads modifiers four different ways.
- **Code is duplicated.** `TextInput`, `TextArea` and `OptionPopup` each define the same `isPrintable`. `Select` and `TagInput` carry near-identical open-list handlers.
- **Bindings cannot be changed.** An application that wants vim-style keys on a slider has to fork the widget.

## Decision

### Bindings are data

`event.Keymap[A]` maps an exact chord to an action:

```scala
final case class Keymap[A](bindings: Map[KeyEvent, A]):
  def lookup(key: KeyEvent): Option[A]
  def ++(other: Keymap[A]): Keymap[A]   // other wins a shared chord
```

A chord is a `KeyEvent` with its full modifier set. `KeyEvent`'s cases are case classes, so a chord needs no new type. `SpecialKey(Left, Set.empty)` and `SpecialKey(Left, Set(Ctrl))` are different chords: a binding matches its modifiers exactly or not at all.

### Handlers match a decoded input exhaustively

`event.KeyInput.decode(event, keymap)` classifies an event for one component:

```scala
enum KeyInput[+A]:
  case Bound(action: A)        // the keymap binds this chord
  case Text(char: Char)        // unbound CharKey without Ctrl/Alt, not a control char
  case Unbound(key: KeyEvent)  // any other key
  case NotKey                  // resize, mouse
```

A binding wins over text, so a keymap may claim a printable character. Each widget declares an `Action` enum and matches the decoded input with one arm per action, one per text case, one per unbound case and one for `NotKey`. All the arms are unguarded, so the match is exhaustive. Adding an action without handling it is a build error.

### Keymaps are public and replaceable

Each widget exposes a `DefaultKeymap` that holds the bindings `docs/component-styleguide.md` describes, and its `make` takes a `keymap` parameter. An application rebinds a key by extending the default:

```scala
Slider.DefaultKeymap ++ Keymap(
  CharKey('h', Set.empty) -> Slider.Action.Step(-1),
  CharKey('l', Set.empty) -> Slider.Action.Step(+1)
)
```

### Policy lives in the arms, not in guards

Rules about keys a widget does not bind are written explicitly against `Unbound` and `Text`. The open list of `Select` and `TagInput` consumes every key without `Ctrl` and lets `Ctrl` chords bubble. That rule is now one expression in the `Unbound` arm, written once in `OptionPopup.whileOpen`, which both widgets share.

The open list has its own public vocabulary, `ListAction`, with `ListAction.DefaultKeymap`. Each list widget takes it as `listKeymap`, next to the `keymap` for its closed field, and supplies only what `Confirm`, `DeleteBack` and typed text mean for its own selection.

Exactness makes some bindings explicit that a modifier-blind match covered without saying so. The parser delivers `Shift+Tab` as `SpecialKey(Tab, Set(Shift))`. An open list that bound only `Tab` would consume `Shift+Tab` as an unbound key and trap backward focus traversal, so the default list keymap binds both to `Leave`.

## Alternatives Considered

### A. List every case in the existing matches

Rejected. Exhaustiveness cannot be reached while modifiers are checked in guards, which is the proof in the Context. Matching modifier sets structurally is not possible either, because `Set` is not a sealed type.

### B. Keep catch-alls and exempt key handlers from the rule

This was the earlier rule: only the final `case _ => EventResult.Ignored` of `handleEvent` was exempt. Rejected by ruling on 2026-11-19. Every case is explicit.

### C. Private keymaps

Same exhaustiveness and exact chords, without the API surface. Rejected because rebinding keys is an ordinary need for the applications this library serves, and keeping keymaps private would force those applications to fork a widget.

## Consequences

### Positive

- Every key handler is exhaustive and has no catch-all. The compiler reports an action that has no handler.
- Each widget's bindings can be read in one place, and they match the styleguide.
- Applications can rebind keys without forking a widget.
- The three copies of `isPrintable` become one rule in `decode`.

### Negative

- **Exact chords change behaviour where a widget used to ignore modifiers:**
  - `Ctrl`/`Alt`+`Enter` or `Space` no longer activate a `Button`.
  - `Alt`/`Shift`+arrows bubble from `Slider` and `RadioGroup`.
  - `Ctrl+Left`/`Ctrl+Right` and modified `Backspace`/`Delete` bubble from text fields.
  - `Shift`/`Alt` with a navigation key, `Enter` or `Escape` is consumed without acting while a list is open.
  - A resize while a list is open bubbles, where the old catch-all consumed it.
- **Every widget's API grows** by an `Action` enum, a `DefaultKeymap` and a `make` parameter. The parameter has a default, so the change is source-compatible. It is not binary-compatible, and it ships in 0.3.0.

### Neutral

- Multi-key sequences remain out of scope (`docs/event-handling.md`, Deferred). A keymap binds single chords.
- When mouse events arrive, `decode` answers them with `NotKey` until a mouse binding design exists.

## References

- `core/src/main/scala/event/Keymap.scala` (`Keymap`, `KeyInput`)
- `core/src/main/scala/component/ListAction.scala`, `core/src/main/scala/component/OptionPopup.scala` (`whileOpen`)
- The `Action` enums and `DefaultKeymap`s of `Button`, `Checkbox`, `RadioGroup`, `Slider`, `ScrollView`, `TextInput`, `TextArea`, `Select` and `TagInput`
- `tests/src/test/scala/event/KeymapSpec.scala`, `tests/src/test/scala/event/KeyInputSpec.scala`
- `docs/component-styleguide.md` (default bindings per widget)
- `docs/event-handling.md` (handler contract)
