# Changelog

All notable changes to ws-console will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/WickedSik/ws-console/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/WickedSik/ws-console/releases/tag/v0.1.0
