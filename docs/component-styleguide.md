# Component Style Guide

Reference material for the ws-console component layer. This document is the authority on how visual styling is expressed across components.

This guide defines the styling vocabulary and how each component expresses it in terms of the style primitives below. It describes meaning and expression — the semantics every component carries — rather than any single binding mechanism.

## Scope

Covers the **renderable components** in `component/` and the styling primitives in `buffer/` and `ansi/` that back them. Supporting infrastructure (`Component`, `RenderContext`, `ComponentId`) is referenced only where it touches styling.

> [!info] Input handling is covered by the [event handling guide](./event-handling.md). Interaction states appear here as appearances only — what `focused` *looks* like, never how focus arrives or what activation sets in motion.

---

## 1. Style Primitives

The atomic unit of appearance is `CellStyle`:

```scala
final case class CellStyle(
  fg:         Foreground     = Foreground.Inherit,
  bg:         Background     = Background.Inherit,
  attributes: Set[Attribute] = Set.empty
)
```

### Paint

`Foreground` and `Background` each have four grades, in ascending fidelity:

| Grade     | Constructor                   | Range / source                                                                                        |
|-----------|-------------------------------|-------------------------------------------------------------------------------------------------------|
| `Inherit` | `Foreground.Inherit`          | Component inherits color from its parent element, the terminal if there is no component above itself. |
| `Named`   | `Foreground.Named(FgColor.X)` | 16 ANSI colors (8 standard + 8 bright)                                                                |
| `Indexed` | `Foreground.Indexed(n)`       | 256-color palette, `n` in 0–255                                                                       |
| `Rgb`     | `Foreground.Rgb(r, g, b)`     | 24-bit truecolor, each 0–255                                                                          |

`Inherit` is the default.

### Attributes

`Set[Attribute]` carries zero or more rendition flags:

`Bold`, `Dim`, `Italic`, `Underline`, `Blink`, `Reverse`, `Strikethrough`.

Attributes compose freely (e.g. `Set(Bold, Underline)`).

### Borders

`BoxStyle` is the border glyph set used by `Panel` and `Canvas.drawBox`:

- `BoxStyle.Single` — single-line box drawing
- `BoxStyle.Double` — double-line box drawing
- `BoxStyle.Borderless` — no border glyphs; the framed area equals the content, so a component's size is `content` rather than `content + 2`

### Spacing

`Insets` is the per-edge spacing primitive — the geometry of padding, measured in cells:

```scala
final case class Insets(top: Int, right: Int, bottom: Int, left: Int)

object Insets:
  val zero: Insets        = Insets(0, 0, 0, 0)
  def all(n: Int): Insets = Insets(n, n, n, n)
  def symmetric(horizontal: Int, vertical: Int): Insets =
    Insets(vertical, horizontal, vertical, horizontal)
```

A component's `padding` insets its content *within* the area layout assigns it — it never changes the component's own size, which stays layout-owned. Padding composes with the border: the content region is `area.inner(border).inner(padding)`. Where the area cannot fit border, padding, and content together, content truncates.

### Component Specific Styles

> For custom styles, check the constructors of each of the `*Style` entities

`ProgressBarStyle` is the style used by `ProgressBar`:

- `ProgressBarStyle.Fill`  — 8 sub-cell eighths filling a cell smoothly (`▏▎▍▌▋▊▉█`)
- `ProgressBarStyle.Shade`  — shaded fill (`░▒▓█`), softened leading edge
- `ProgressBarStyle.Segmented` — discrete unicode pips ( `□` / `■` ), best used for known quantities

`SpinnerStyle` is the style used by `Spinner`:

- `SpinnerStyle.Braille` — `⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏` 10 unicode frames showing dots moving in a circle on the same cell
- `SpinnerStyle.Line` — `-\|/` 4 frames showing a rotating line
- `SpinnerStyle.Circle` — `◜◠◝◞◡◟` 6 frames showing a spinning ring

---

## 2. The Style Vocabulary

Styling intent decomposes into **two orthogonal axes** that compose: a **semantic role** (what the content *means*) and an **interaction state** (what the widget is *doing*). A focused primary button is the `accent` role combined with the `focused` state — not a third bespoke style.

The expressions below are the shared definitions every component draws on, so the same meanings render consistently across the library.

### 2.1 Semantic Roles

*What the content means.* Roles are about hue and emphasis, independent of any interaction.

> **TODO:** make sure the roles are configurable colors and expressions -> `Set(Attribute | Color)`

| Role       | Meaning                                | Expression                                   |
| ---------- | -------------------------------------- | -------------------------------------------- |
| `default`  | Normal body content                    | `Inherit` fg, no attributes                  |
| `muted`    | De-emphasized / secondary content      | `Dim`, or `Named(BrightBlack)`               |
| `emphasis` | Stronger than default, same hue        | `Bold`                                       |
| `accent`   | Interactive affordance / draws the eye | `Named(BrightCyan)`                          |
| `error`    | Failure, destructive, blocking         | `Named(Red)`                                 |
| `success`  | Confirmation, completion               | `Named(Green)`                               |
| `warning`  | Caution, recoverable problem           | `Named(Yellow)`                              |
| `info`     | Neutral informational note             | `Named(Blue)` / `Named(BrightBlue)`          |
| `code`     | Literal / monospace-semantic text      | `Named(Cyan)` (the terminal is already mono) |

### 2.2 Interaction States

*What the widget is doing.* States modulate a role's base appearance; they apply only to components that participate in focus or input.

> **TODO:** The state can be chosen by the user to change/overruled -> if a button is selected or focussed, how does it look?

| State      | Meaning                                    | Modulation                                 |
| ---------- | ------------------------------------------ | ------------------------------------------ |
| `normal`   | Resting; the role's base appearance        | role as-is                                 |
| `focused`  | Holds keyboard focus                       | brighter fg + `Bold`                       |
| `disabled` | Present but non-interactive; ignores input | `Dim` + `muted`                            |
| `active`   | Momentary, during activation (press)       | `Reverse`, or a `bg` fill                  |
| `selected` | Chosen within a set (list item, checkbox)  | `bg` fill or `Reverse`; or an accent glyph |

For example, a focusable button reads as `Dim` + `Named(BrightBlack)` at rest and `Bold` + `Named(BrightCyan)` when focused — the `focused` state lifting intensity over the role's base hue.

### 2.3 Composition Rule

When a role and a state both want to set the same property, **the role owns hue, the state owns intensity and attributes**. An `accent` button that is `focused` keeps its `accent` hue (role) and adds `Bold` (state). This keeps the meaning legible while still signaling interaction.

---

## 3. Components That Carry Style

These components own visual style and express the vocabulary above. The remaining components are structural — they arrange or clear and draw no paint of their own (see §4).

> [!info] Several describe activation, toggling, or a text change as **a discrete signal the host observes**. That phrase fixes a requirement, not a mechanism: the host learns what happened, and the widget stores no shared state of its own. How the signal travels is the event handling guide's business. It is not a flag the host polls.

### `Text`

```scala
Text(
	 content: String,
	 style: CellStyle = CellStyle.Empty,
	 align: Alignment = Alignment.Left
)
Text(line: StyledLine, align: Alignment)  // styled spans: Text(Spans("Name", Span("*", required)))
```

- **Styling**: a `CellStyle` per span, plus `Alignment` (`Left` / `Center` / `Right`) for the line as a whole. The one-string form is a single span.
- **Roles**: expresses any semantic role via its `style`.
- **States**: none of its own — it is not focusable. A styled `Text` inside an interactive parent reflects state through the `CellStyle` the parent hands it.
- **Default**: `CellStyle.Empty` (= `default` role), left-aligned.
- **Constraint**: one line. Carries any number of styled spans, truncates by terminal columns on overflow, and does not wrap. Multi-line, word-wrapped text is `WrappedText`.

### `WrappedText`

```scala
WrappedText(
	 line: StyledLine,
	 style: CellStyle = CellStyle.Empty,
	 align: Alignment = Alignment.Left,
	 maxLines: Option[Int] = None
)
```

- **Styling**: a base `CellStyle` with each span's style laid over it: a hue the span names wins, an inherited hue falls through to the base, and attributes add up. `Alignment` applies to each wrapped line on its own.
- **Roles**: expresses any role through the base style; a span within it can carry a second role, such as the `error` hue on a required marker.
- **States**: none of its own — it is not focusable.
- **Default**: `CellStyle.Empty` (= `default` role), left-aligned, no line cap.
- **Wrapping**: breaks on whitespace, measures in terminal columns, and never splits or straddles a wide glyph. `\n` starts a new line. A span continues across a break in its own style.
- **Overflow**: with `maxLines`, the last kept line ends in `…`. The preferred height is the wrapped line count at the given width, capped by `maxLines`, so the text grows taller as its column narrows.

### `Panel`

```scala
Panel(
  child:   Component      = Spacer,
  title:   Option[String] = None,
  border:  BoxStyle       = BoxStyle.Single,
  style:   CellStyle      = CellStyle.Empty,
  padding: Insets         = Insets.zero,
  // ... rest of properties
)
```

- **Styling**: `border` (the `BoxStyle` glyph set) and a single `style` applied to the fill, the border glyphs, **and** the title.
- **Roles**: the one `style` carries a role for the whole frame.
- **States**: none of its own; a `Panel` carries a single role across its frame.
- **Padding**: `padding` insets the child within the frame.
- **Default**: `Single` border, `CellStyle.Empty` fill, no title, no padding.
- **Note**: the opaque fill means a `Panel` owns *every* cell in its bounds. Stacking panels relies on this — content beneath does not leak through.
- **Constraint**: the title shares the frame's `style`; there is no separate title style.

### `Button`

```scala
Button(
  label:   String,
  style:   CellStyle = CellStyle.Empty,
  border:  BoxStyle  = BoxStyle.Single,
  padding: Insets    = Insets.zero,
  enabled: Boolean   = true,
  // ... rest of properties
)
```

Activateable control with a single-line label.

- **Styling**: a centered `label`, wrapped in a `border` frame (`BoxStyle.Borderless` draws none).
- **Roles**: `accent` for a primary button, `default` for a secondary one — carried by `style`.
- **States**: `normal`, `focused`, `active` (a momentary frame on activation), `disabled`. The `style` argument carries the role; the button derives each state's appearance from it by modulating intensity and attributes per §2.3, reading the live focus from `RenderContext` rather than storing it.
- **Padding**: `padding` insets the label within the frame — the label is placed in `area.inner(border).inner(padding)`, then centered.
- **Default**: `default` role, `Single` border, no padding, enabled.
- **Behavior**: focusable. `Enter` or `Space` activates it while focused; `enabled = false` renders the `disabled` state and refuses activation. Activation is a discrete signal the host observes — the button carries no side effect and stores no shared state.
- **Constraint**: single-line label; truncates to fit the inner width. `BoxStyle.Borderless` collapses the frame to the label's own height.

### `TextInput`

```scala
TextInput(
  value:       String,
  placeholder: String    = "",
  style:       CellStyle = CellStyle.Empty,
  border:      BoxStyle  = BoxStyle.Single,
  padding:     Insets    = Insets.zero,
  enabled:     Boolean   = true,
  maxLength:   Option[Int] = None,
  // ... rest of properties
)
```

Single-line editable field.

- **Styling**: the field's `value` text (or `placeholder` when empty), a caret when focused, and a `border` frame. `BoxStyle.Borderless` draws an unframed field.
- **Roles**: `default` for entered content, `muted` for the `placeholder`, `code` for monospace fields — carried by `style`.
- **States**: `normal`, `focused` (caret shown), `disabled`. The `style` argument carries the role; the field derives each interaction state from it per §2.3, reading focus from `RenderContext` rather than storing it.
- **Padding**: `padding` insets the field content within the frame — text sits in `area.inner(border).inner(padding)`.
- **Default**: empty placeholder, `default` role, `Single` border, no padding, enabled.
- **Behavior**: focusable. While focused, printable keys insert at the caret, `Backspace`/`Delete` remove, and `←`/`→`/`Home`/`End` move the caret; each text change is a discrete signal the host observes. The host seeds `value`; the field owns the edit buffer and caret from then on and reports every change through `onChange`.
- **Constraint**: single line. Content wider than the field scrolls horizontally to keep the caret visible.
- **Length limit**: `maxLength` caps the value in grapheme clusters, counted by `Graphemes.count`: a surrogate pair, a letter with its combining marks, and an emoji ZWJ sequence each count once. Input that would grow the value past the limit is refused and consumed, so the key never reaches an application shortcut; a paste, which arrives key by key, stops at the limit. A seeded `value` over the limit is shown whole and can only shrink. A host building a "Characters: n/max" counter counts with `Graphemes.count`, so its number agrees with what the field refuses.

### `TextArea`

```scala
TextArea(
  value:       String       = "",
  placeholder: String       = "",
  rows:        Int          = 3,
  onChange:    String => ZIO[Frame, IOException, Unit],
  style:       CellStyle    = CellStyle.Empty,
  border:      BoxStyle     = BoxStyle.Single,
  sides:       Sides        = Sides.all,
  padding:     Insets       = Insets.zero,
  enabled:     Boolean      = true,
  grip:        Option[Char] = None,
  maxLength:   Option[Int]  = None
)
```

Multi-line editable field with soft wrap.

- **Styling**: the field's `value` (or `placeholder` when empty), soft-wrapped at the inner width; a caret when focused; a `border` frame; an optional decorative `grip` glyph (`TextArea.GripGlyph`, `◢`) in the bottom-right corner when both the bottom and right sides are drawn.
- **Roles**: `default` for entered content, `muted` for the `placeholder` — carried by `style`.
- **States**: `normal`, `focused` (caret shown), `disabled`, derived per §2.3, reading focus from `RenderContext`.
- **Padding**: `padding` insets the content within the frame, as for `TextInput`.
- **Default**: empty, three rows, `default` role, `Single` border, no padding, enabled, no grip.
- **Behavior**: focusable. Printable keys insert at the caret and `Enter` inserts a line break; `Backspace`/`Delete` remove a grapheme, joining lines at the edges. `←`/`→` step a grapheme and cross line ends; `↑`/`↓` move by visual line, keeping the column, and bubble on the first / last line; `Home`/`End` go to the edges of the visual line; `PgUp`/`PgDn` move by `rows`; `Alt+←`/`Alt+→` move by word. `Tab`, `Ctrl+Home` and `Ctrl+End` always bubble. The placeholder shows whenever the field is empty, wrapped; when focused the caret overlays its first cell. The host seeds `value`; the field owns the edit buffer and reports each change through `onChange`.
- **Constraint**: `rows` visible lines with internal vertical scroll that follows the caret; preferred height is `rows` plus border and padding. The last inner column is reserved for the caret, so text never reflows on focus. Soft breaks never change `value`.
- **Length limit**: `maxLength` caps the value in grapheme clusters exactly as for `TextInput`; a line break counts as one.

### `Checkbox`

```scala
Checkbox(
  label:   String,
  checked: Boolean          = false,
  marks:   (String, String) = ("☑", "☐"),
  style:   CellStyle        = CellStyle.Empty,
  enabled: Boolean          = true,
  // ... rest of properties
)
```

Bistable toggle pairing a check mark with a label.

- **Styling**: a check `mark` followed by a `label`. `marks` is the `(checked, unchecked)` glyph pair, chosen by the consumer.
- **Roles**: `default` for the `label` (carried by `style`); `accent` for the `mark` when checked, `muted` when unchecked.
- **States**: `normal`, `focused`, `disabled`, plus `selected` as the bistable checked/unchecked axis. The `style` argument carries the label's role; the checkbox derives each interaction state's appearance from it per §2.3, reading focus from `RenderContext` rather than storing it.
- **Default**: unchecked, `☑`/`☐` marks, `default` role, enabled.
- **Behavior**: focusable. `Space` toggles it while focused; `enabled = false` renders the `disabled` state and refuses toggling. The toggle is a discrete signal the host observes — the host supplies the `checked` value, so the checkbox stores no shared state of its own.
- **Layout**: renders single- or multi-line; a label that exceeds the available width wraps beneath the mark with a hanging indent that keeps continuation lines aligned to the label's start.

### `RadioGroup`

```scala
RadioGroup(
  options:  Seq[String],
  selected: Int              = 0,
  marks:    (String, String) = ("●", "○"),
  style:    CellStyle        = CellStyle.Empty,
  enabled:  Boolean          = true,
  layout:   Layout           = Layout.Vertical,
  // ... rest of properties
)
```

A set of mutually exclusive options, exactly one selected.

- **Styling**: a list of options, each an option `mark` followed by its label, placed by `layout`. `marks` is the `(selected, unselected)` glyph pair, chosen by the consumer.
- **Roles**: `default` for the option labels (carried by `style`); `accent` for the selected option's `mark`.
- **States**: `normal`, `focused` (the group is a single focus stop), `disabled`, plus `selected` marking the chosen option. The `style` argument carries the labels' role; the group derives each interaction state from it per §2.3, reading focus from `RenderContext` rather than storing it.
- **Default**: first option selected, `●`/`○` marks, `default` role, enabled, vertical.
- **Behavior**: focusable as one tab stop. While focused, the arrow keys along the layout's axis move the selection: `↑`/`↓` in a vertical group, `←`/`→` in a horizontal one; `Home`/`End` jump to the first and last option. The cross-axis arrows are never consumed and bubble to the parent, so a group never changes anything outside itself, and a horizontal group inside a `ScrollView` leaves vertical scrolling alone. There is no wrap-around at the ends. A selection change is a discrete signal the host observes — the host supplies `selected`, so the group stores no shared state of its own. `enabled = false` renders the `disabled` state and refuses selection.
- **Layout**: `Layout.Vertical` places one option per line; each label wraps beneath its mark with a hanging indent, like `Checkbox`. `Layout.Horizontal(gap)` places every option on one row, `gap` cells apart (default 2); the option reaching the right edge truncates by cells, never splitting a wide grapheme, and the options after it are not drawn. Preferred height is the option count when vertical and one row when horizontal. A "none selected" entry, if wanted, is added by the consumer as an explicit option.

### `ProgressBar`

```scala
ProgressBar(
  progress: Double,
  bar:      ProgressBarStyle = ProgressBarStyle.Fill,
  style:    CellStyle        = CellStyle.Empty,
  // ... rest of properties
)
```

A horizontal bar showing determinate progress from `0.0` to `1.0`.

- **Styling**: a filled portion and an unfilled track. `bar` (a `ProgressBarStyle`) selects the rendering; the default `ProgressBarStyle.Fill` draws block-element glyphs with sub-cell precision — the fill boundary uses partial block glyphs (eighths: `▏▎▍▌▋▊▉█`) so progress advances smoothly *within* a cell, not one whole cell at a time.
- **Roles**: the fill carries a role via `style` (`accent`, `info`, or `success` are typical); the track renders `muted`.
- **States**: none — `ProgressBar` is a display component, not focusable, and carries no interaction state, like `Text`.
- **Default**: `ProgressBarStyle.Fill`, `default` role; callers typically supply `accent` or `success`.
- **Behavior**: determinate only — `progress` is host-supplied and clamped to `0.0..1.0`. The bar stores no state of its own.
- **Layout**: single row, spanning the full width of its assigned area. A percentage caption is not built in; compose a `Text` beside the bar in an `HBox`.

### `Spinner`

```scala
Spinner(
  frames: SpinnerStyle = SpinnerStyle.Braille,
  style:  CellStyle    = CellStyle.Empty,
  // ... rest of properties
)
```

An indeterminate activity indicator — "work is happening, duration unknown."

- **Styling**: a single animated glyph. `frames` (a `SpinnerStyle` carrying the animation sequence) supplies the cycle. The default `SpinnerStyle.Braille` is the 10-step braille dot cycle; consumers supply their own via `SpinnerStyle(customFrames)`.
- **Roles**: carries a role via `style` (`accent` or `info` are typical).
- **States**: none — `Spinner` is a display component, not focusable, and carries no interaction state, like `Text` and `ProgressBar`.
- **Default**: `SpinnerStyle.Braille`, `default` role.
- **Behavior**: the spinner derives its frame index from the wall-clock timestamp the framework samples once per frame and exposes on `RenderContext`, cycling through `frames` at a fixed interval. Because the index is a function of real time, the animation runs at a constant rate independent of render cadence — a faster render loop does not spin it faster. `render` stays pure: it reads the timestamp from `ctx` and mutates nothing, and the spinner stores no state of its own.
- **Layout**: occupies a single cell. A caption ("Loading…") is not built in; compose a `Text` beside it in an `HBox`, like `ProgressBar`.

---

## 4. Structural Components (No Intrinsic Style)

These draw no paint. They are listed for completeness; the style guide governs them only insofar as they *carry* styled children unchanged.

### `Component`

The base contract every renderable type extends — both the styled components of §3 and the structural ones below. It carries no style and paints nothing of its own; it defines how a component draws into a `Rect` on a `Canvas` and how the render pipeline walks it. Rendering is synchronous and pure: no effects, no shared internal state, no I/O. A component receives a read-only `RenderContext` snapshot at render and event time and queries it for framework-owned state — the current focus, and the wall-clock timestamp sampled once per frame (the source animated components like `Spinner` read) — rather than caching that state in its own fields. Sampling that timestamp is the framework's effect, performed once in the effectful render loop; the component only reads the value, which is what keeps `render` itself pure.

The trait's defaults are what place a component in the tree:

- **`childLayouts(area)`** — *empty marks a leaf*: the component draws only itself and the pipeline recurses no further. *Non-empty marks a composite*: it returns the `(child, rect)` pairs the pipeline recurses into. The pairs must mirror what `render` actually draws — a child skipped or zero-sized in one must be skipped or zero-sized in the other. Pure geometry; it reads no `RenderContext`, so layout never depends on focus or frame state.
- **`preferredHeight(width)`** — `None` means no preference: the component takes whatever height its parent assigns. A component with a natural height returns it, measured at the given width (wrapped text grows taller as it narrows). Leaves answer from their content and chrome — a bordered `TextInput` wants three rows — and composites answer from their children. Pure geometry, like `childLayouts`.
- **`focusable`** — `false` excludes the component from the focus cycle; `true` opts it in. A focusable component in a collapsed (zero-size) layout branch is excluded for that frame.
- **`handleEvent`** — returning `Ignored` bubbles the event to the parent; an interactive component overrides to consume it, guarding on `ctx.focus.isFocused(id)`.
- **`id`** — a stable, framework-assigned identity used by layout and dispatch.

A component may leave cells in `area` unwritten — the framework owns blank cells, and every visible cell is (re)drawn each frame it should appear.

### Other Structural Components

- **`HBox` / `VBox`** — split their area across children by `Constraint`. Pure geometry; draw nothing themselves. In a `VBox`, `Constraint.Fit` sizes a child to its `preferredHeight`; a child with no preference, or `Fit` in an `HBox`, behaves as `Fill`. A `VBox` prefers the sum of its children's heights, an `HBox` the tallest child's.
- **`ScrollView`** — a vertical viewport onto a child taller than the area it is given. The child is laid out at its preferred height (never shorter than the viewport) and rows outside the viewport are clipped, borders cut rather than shifted. The rightmost column always holds a scrollbar — a dim track and a thumb sized to the visible fraction, spanning the whole track when the content fits — so content width never changes as content grows past the viewport. Its only paint is that column: `style` carries the thumb's role, `trackStyle` the track's, and the thumb takes `focused` (`Bold`) when the view itself holds focus. Keys left unused by the focused descendant scroll it, and every scroll key requires `Ctrl`: `Ctrl+PgUp` / `Ctrl+PgDn` by a page, `Ctrl+Up` / `Ctrl+Down` by a row, `Ctrl+Home` / `Ctrl+End` to the ends. The bare keys always stay with the focused control; a key that cannot move the view bubbles on, so the innermost view that can scroll consumes it. Moving focus to a control outside the viewport scrolls it into view on the next frame. Not a focus stop by default; opt in for read-only content such as a log pane. Vertical only — there is no horizontal scrolling.
- **`Spacer`** — occupies a layout slot and renders nothing. Padding, separators, reserved cells.
- **`RawCanvas`** — hands a callback a clipped sub-canvas. It has **no style of its own**; any appearance it produces belongs to the consumer's callback. This is the one component the style guide can only *advise*, not *govern* — a `RawCanvas` callback can paint anything, including styling that bypasses the vocabulary above. Reach for purpose-built components for repeated patterns; use `RawCanvas` only where structure adds no value.

---

## 5. Component Roadmap

Further components extend the same two-axis vocabulary and are specified as they land:

- `ValidatableTextInput` — `TextInput` plus a host-supplied validity result; reintroduces the `invalid` state (`error` hue on the border)
- `Select` / `Dropdown`
- `Toggle`
- `List` / `Table`
- `StatusBar`
