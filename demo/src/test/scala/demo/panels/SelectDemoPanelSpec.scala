package io.github.wickedsik.wsconsole
package demo.panels

import buffer.ScreenBuffer
import component.{Component, FocusSnapshot, RenderContext, Select, TagInput}
import event.KeyEvent.{CharKey, SpecialKey}
import event.{EventResult, SpecialKeyCode}
import geometry.Rect
import render.LayoutManager
import testkit.FrameHarness
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

/**
 * [[SelectDemoPanel]] at 120 columns: three selects and a tag field, the
 * country combobox filtering and scrolling inside its popup, the theme
 * list opening upward, the tag field reporting its remaining chips.
 */
object SelectDemoPanelSpec extends ZIOSpecDefault:

  private val W = 120
  private val H = 21

  /** Rows of `buf` as plain text (wide-glyph continuation cells contribute nothing). */
  private def rows(buf: ScreenBuffer): Vector[String] =
    (0 until buf.height).toVector.map(y => (0 until buf.width).flatMap(x => buf.get(x, y).map(_.text)).mkString)

  private def focused(c: Component): RenderContext = RenderContext(FocusSnapshot(Some(c.id)))

  private def selects(root: Component): Vector[Select] =
    LayoutManager.default.resolve(root, Rect(0, 0, W, H)).order.collect { case s: Select => s }

  private def tagField(root: Component): TagInput =
    LayoutManager.default.resolve(root, Rect(0, 0, W, H)).order.collect { case t: TagInput => t }.head

  def spec: Spec[TestEnvironment & Scope, Any] = suite("SelectDemoPanel")(
    test("three selects and a tag field, all in the focus cycle, in reading order") {
      for panel <- SelectDemoPanel.make
      yield
        val layout = LayoutManager.default.resolve(panel.root, Rect(0, 0, W, H))
        val found = selects(panel.root)
        val tags = tagField(panel.root)
        assertTrue(
          found.size == 3,
          layout.focusOrder.ids == Vector(found(0).id, found(1).id, tags.id, found(2).id)
        )
    },
    test("renders labels, the chosen model, the country placeholder and the chosen theme") {
      for panel <- SelectDemoPanel.make
      yield
        val text = rows(renderToBuffer(W, H)(panel.root))
        assertTrue(
          text.exists(r => r.contains("Model") && r.contains("Sonnet")),
          text.exists(r => r.contains("Country") && r.contains("Type to filter")),
          text.exists(r => r.contains("Theme") && r.contains("System"))
        )
    },
    test("the theme list opens upward from the bottom edge") {
      for
        panel <- SelectDemoPanel.make
        theme = selects(panel.root).last
        _ = theme.handleEvent(SpecialKey(SpecialKeyCode.Enter, Set.empty), focused(theme))
        h      <- FrameHarness.make(W, H)
        layout <- h.renderTree(panel.root, focused(theme))
      yield
        val text = rows(h.drawnBuffer)
        assertTrue(
          layout.popups.map(_._2) == Vector(Rect(SelectDemoPanel.LabelWidth, 12, SelectDemoPanel.FieldWidth, 6)),
          text(13).contains("System"),
          text(16).contains("High contrast")
        )
    },
    test("the country list scrolls to its last option") {
      for
        panel <- SelectDemoPanel.make
        country = selects(panel.root)(1)
        _ = Seq(SpecialKeyCode.Enter, SpecialKeyCode.End)
              .foreach(code => country.handleEvent(SpecialKey(code, Set.empty), focused(country)))
        h      <- FrameHarness.make(W, H)
        layout <- h.renderTree(panel.root, focused(country))
      yield
        val text = rows(h.drawnBuffer)
        assertTrue(
          layout.popups.map(_._2) == Vector(Rect(SelectDemoPanel.LabelWidth, 11, SelectDemoPanel.FieldWidth, 8)),
          text(17).contains("Uruguay"),
          text(12).contains("Russia (Россия)")
        )
    },
    test("typing into the country combobox narrows its list") {
      for
        panel <- SelectDemoPanel.make
        country = selects(panel.root)(1)
        _ = "land".foreach(c => country.handleEvent(CharKey(c, Set.empty), focused(country)))
        h      <- FrameHarness.make(W, H)
        layout <- h.renderTree(panel.root, focused(country))
      yield
        val text = rows(h.drawnBuffer)
        assertTrue(
          country.matches.map(country.options) == Vector("Finland", "Ireland", "Netherlands", "New Zealand", "Poland"),
          layout.popups.map(_._2) == Vector(Rect(SelectDemoPanel.LabelWidth, 11, SelectDemoPanel.FieldWidth, 7)),
          text(9).contains("land"),
          text(12).contains("Finland"),
          text(16).contains("Poland")
        )
    },
    test("the tag field shows its seeded chips and the host's remaining count follows it") {
      for
        panel <- SelectDemoPanel.make
        tags = tagField(panel.root)
        before = rows(renderToBuffer(W, H)(panel.root))
        // Open, add the second tag, close: the list stays open after adding, over the line below.
        results = Seq(SpecialKeyCode.Enter, SpecialKeyCode.Down, SpecialKeyCode.Enter, SpecialKeyCode.Escape)
                    .map(code => tags.handleEvent(SpecialKey(code, Set.empty), focused(tags)))
        h <- FrameHarness.make(W, H)
        // The count is host state: run the onChange the add returned, as the render loop would.
        _ <- ZIO.foreachDiscard(results.collect { case EventResult.Perform(effect) => effect })(
               _.provideLayer(h.frameLayer)
             )
        _ <- h.renderTree(panel.root, focused(tags))
      yield
        val after = rows(h.drawnBuffer)
        assertTrue(
          before(12).contains(" Fantasy   Horror "),
          before(14).contains("Tags remaining: 4"),
          tags.selected == Vector(0, 3, 1),
          after(14).contains("Tags remaining: 3")
        )
    }
  )
