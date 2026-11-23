# Changelog

All notable changes to ws-console will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.1] - 2026-11-23

### Fixed
- Scaladoc links to `Widths`, `Graphemes.count` and `Coloring.Uniform` resolve, and the `Label` example shows its `${...}` interpolation. The published API docs had broken links in `StyledLine`, `TextWrap`, `TextEdit`, `TextInput`, `TextArea` and `Coloring`.

## [0.3.0] - 2026-11-23

### Added
- Key bindings as data (ADR-010): `Keymap` maps exact key chords to actions, and `KeyInput.decode` classifies an event as a bound action, text, an unbound key or a non-key event.
- Every interactive component (`Button`, `Checkbox`, `RadioGroup`, `Slider`, `ScrollView`, `TextInput`, `TextArea`, `Select`, `TagInput`) declares an `Action` enum and a public `DefaultKeymap`, and `make` takes a `keymap` to replace it. `Select` and `TagInput` also take a `listKeymap` of `ListAction`s for their open option list.
- `BooleanMarks`: the glyph pair of a two-state control, with the column `width` it reserves.
- `TerminalSize.from(rows, cols)` returns `None` unless both are positive.
- `LayoutResult.selfAndAncestors` and `LayoutResult.ancestorsOf` walk a component's parent chain, innermost first.
- `Rect.nonEmpty`.
- `RenderHarness.renderToBuffer` takes an optional `seed` that draws into the canvas before the component renders (ws-console-testkit).

### Changed
- **Breaking:** Built with Scala 3.9.0 (was 3.3.6). Consumers need Scala 3.9 or later.
- **Breaking:** `EventResult`, `RawInput`, `ParserState` and `PanelHostError` are enums. Case names and patterns are unchanged, so source compiles as before; code compiled against 0.2.x must be recompiled.
- **Breaking:** Key chords match exactly, modifiers included. A key with a modifier the keymap does not bind now bubbles to the parent:
  - `Button`: Shift, Ctrl or Alt with Enter or Space no longer activates.
  - `Slider`, `RadioGroup`: Shift and Alt with an arrow key bubble instead of moving.
  - `TextInput`, `TextArea`: Ctrl or Shift with Left or Right, a modified Backspace or Delete, and `TextInput`'s Home or End with Shift or Alt bubble instead of editing.
  - `Select`, `TagInput`: with the list open, Shift or Alt with a navigation key, Enter or Escape is consumed without acting, and a resize bubbles. Shift+Tab leaves the list.
- **Breaking:** `HBox` and `VBox` take their `(Constraint, Component)` pairs as a repeated parameter; pass a sequence as `HBox(pairs*)`. Bare children, each given `Constraint.Fill`, go through `HBox.evenly` and `VBox.evenly`.
- **Breaking:** `FocusManager.focusNext`, `focusPrevious` and `clear` take no parameter list. Write `focusNext`, not `focusNext()`.
- **Breaking:** The `Templates` methods in `Cursor`, `Color` and `Scroll` are lowerCamel (`Cursor.Templates.up`, `Color.Templates.fg256`, `Scroll.Templates.setRegion`, and so on). `Application.continueForever` and `defaultQuitOn` are renamed `ContinueForever` and `DefaultQuitOn`.
- **Breaking:** `EventParser.parse` returns a named tuple `(state, events)` and `Braille.fit` returns `(cols, rows)`. Destructuring still compiles; `._1` and `._2` do not.
- **Breaking:** `TerminalSize` is final.
- **Breaking:** `Checkbox`, `RadioGroup` and `TagInput` take their marks as a `BooleanMarks(on, off)` instead of a named tuple. The label starts after the wider mark, so marks of unequal width no longer shift it when toggled.
- `Checkbox` truncates its label by terminal columns, so a wide-grapheme label no longer draws past its area.
- **Breaking:** `Panel.onMount`, `onUnload` and `onRemount`, and `PanelHost.push`, `pop` and `replace`, need only `Frame` instead of `Terminal & Frame` (ADR-004). A `PanelHost` call now fits a `Perform` payload without providing a `Terminal`. Overrides of the hooks drop `Terminal` from their declared type.
- **Breaking:** `PanelHost.pop` returns `Boolean`. On an empty stack it does nothing and returns `false` instead of failing.

### Removed
- **Breaking:** `PanelHostError`. `pop` no longer fails on an empty stack.
- **Breaking:** `RenderOptimizer`. Nothing in the pipeline consulted it (ADR-011).
- **Breaking:** `unicode.BoxDrawing`. `BoxStyle` carries the box glyphs.

### Fixed
- A terminal size of 0 reported by `tput` no longer kills the fiber at startup or on a resize poll. The size detection falls through to its next method as intended.
- Installing the `SIGWINCH` handler no longer swallows fatal errors such as `OutOfMemoryError`.

## [0.2.1] - 2026-10-18

### Added
- Dithering for `BrailleImage` and `Braille.encode`: `Dither.Ordered`, `Dither.FloydSteinberg` and `Dither.Atkinson` keep the tones of paintings and photos. `Dither.None`, the threshold cut, stays the default.
- Per-cell colour for `BrailleImage`: `Coloring.Source` paints each cell in its region's colour, in truecolor. `Coloring.Uniform`, one colour for the whole image, stays the default.
- `RasterizedBitmap` keeps each pixel's colour (`rgb`), and `Braille.colors` averages it per cell.

## [0.2.0] - 2026-10-18

### Added
- `ScrollView` component: a vertical viewport with a scrollbar for content taller than its area. Focused fields scroll into view automatically.
- `Constraint.Fit` and intrinsic height: components can report the height they need, and containers size them to it.
- Styled spans: one line of text can mix several styles.
- `WrappedText` component: word-wrapped text with optional line limit and ellipsis.
- `TextArea` component: multi-line text input with soft wrapping and internal scrolling.
- `maxLength` on `TextInput` and `TextArea`, counted in user-visible characters.
- `TextInput.set` to replace the field value from code.
- Horizontal layout for `RadioGroup`.
- `Slider` component: pick a number in a range, with an optional marked default value.
- Anchored popups: a component can show a box next to itself, drawn on top of everything else.
- `Select` component: a single-choice dropdown, with optional type-to-filter.
- `TagInput` component: a multi-choice field that shows the chosen options as chips.
- `Form`: describe a form as fields, sections and columns, and get a scrollable component tree back.
- `Label` component for text that changes at runtime, such as counters.
- `RectPlaceholder` component and `BoxStyle.Dashed`.
- `BrailleImage` component: renders a pixel image as Braille dots that fill the component's area.
- `raster` package for loading images and converting them to Braille.
- `FrameHarness.renderTree` in the testkit, to render a full frame including popups and focus scrolling.

### Changed
- Java 20 is now the minimum runtime, so emoji sequences and flags are kept together correctly.
- `Graphemes` and `Widths` moved to the new `glyph` package. `Graphemes` is now public.
- `RadioGroup` arrow keys follow its layout: Up/Down for vertical, Left/Right for horizontal. The other arrows pass through to the parent.
- `TextInput` and `RadioGroup` no longer consume Ctrl key combinations, so those reach the enclosing view.
- The published POM declares an early-semver version scheme, so build tools reject incompatible version mixes.

### Fixed
- Character widths now follow the Unicode East Asian Width data. Several symbols and emoji were measured with the wrong width.
- Flag emoji are measured as two columns wide.
- `TextInput` measures text in terminal columns, so wide characters no longer overflow its border.
- Editing in `TextInput` no longer splits emoji or accented characters.
- `RadioGroup` labels with wide characters no longer overflow their row.
- Panel titles with wide characters are truncated correctly.

## [0.1.0] - 2026-10-08

### Added
- ANSI escape sequence primitives with type-safe colors, styles and cursor control, and an immutable `AnsiBuilder`.
- `Terminal` service with capability detection and safe setup and teardown of the alternate screen, hidden cursor and raw mode.
- Double-buffered cell grid that sends only changed cells to the terminal.
- Scrollable regions backed by the terminal's own scroll support.
- Constraint-based layout engine with fixed, percentage, fill and bounded sizes.
- Component model with `HBox`, `VBox`, `Panel` and `Text`.
- Keyboard event parsing delivered as a ZIO stream.
- Render loop with focus management, event dispatch and terminal resize handling.
- `Application` and `PanelHost` for building full-screen apps with stacked panels and overlays.
- Interactive widgets: `Button`, `TextInput`, `Checkbox` and `RadioGroup`.
- Display widgets: `ProgressBar` and `Spinner`.
- `TextInput` word motion with Alt+Left/Right and a `reset()` method.
- Border styles with per-side visibility, plus padding through `Insets`.
- Unicode support for wide characters (CJK, emoji) and grapheme clusters, with `Widths.stringWidth` for measuring text in columns.
- Combined style escape sequences for smaller terminal output.
- `ws-console-testkit`: capture terminal output and assert on the rendered cell grid in tests.
- Published to Maven Central as `ws-console` and `ws-console-testkit`.
- Licensed under LGPL-3.0-or-later.

[Unreleased]: https://github.com/WickedSik/ws-console/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/WickedSik/ws-console/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/WickedSik/ws-console/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/WickedSik/ws-console/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/WickedSik/ws-console/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/WickedSik/ws-console/releases/tag/v0.1.0
