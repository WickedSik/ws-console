package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{BgColor, FgColor}
import buffer.{Attribute, Background, Cell, CellStyle, Foreground}
import component.{FocusSnapshot, RenderContext}
import event.KeyEvent.SpecialKey
import event.{EventResult, KeyModifier, SpecialKeyCode}
import geometry.Rect
import render.{EventDispatcher, FocusManager, FocusPolicy, LayoutManager}
import testkit.FrameHarness

import zio.*
import zio.test.*

/**
 * [[CharacterFormPanel]] at 80x24 and 120x40: paging from top to bottom
 * with `Ctrl+PgDn` shows every row of the reference page on some frame,
 * and Tab walks every control in reading order with the viewport
 * following.
 */
object CharacterFormPanelSpec extends ZIOSpecDefault:

  /** One drawn frame: its cells, row by row. */
  final private case class Shot(cells: Vector[Vector[Cell]]):
    val rows: Vector[String] = cells.map(_.map(_.text).mkString)

    /** The cell at column `x` of the first row containing `text`, `x` cells into the match. */
    def cellIn(text: String, x: Int = 0): Option[Cell] =
      rows.indexWhere(_.contains(text)) match
        case -1 => None
        case y  => cells(y).lift(rows(y).indexOf(text) + x)

  private def ctrl(code: SpecialKeyCode) = SpecialKey(code, Set(KeyModifier.Ctrl))

  /** Every frame from the top of the panel to the bottom, paging with `Ctrl+PgDn`. */
  private def pageThrough(w: Int, h: Int): ZIO[Any, Any, Vector[Shot]] =
    for
      panel <- CharacterFormPanel.make
      root = panel.root
      h0      <- FrameHarness.make(w, h)
      first   <- h0.renderTree(root, RenderContext.empty)
      fm      <- FocusManager.make
      _       <- fm.setOrder(first.focusOrder)
      _       <- fm.focusNext
      focused <- fm.focused
      ctx = RenderContext(FocusSnapshot(focused))
      dispatcher = EventDispatcher.make(fm)
      shots <- ZIO.iterate((Vector.empty[Shot], true))(_._2) { (acc, _) =>
                 for
                   h      <- FrameHarness.make(w, h)
                   layout <- h.renderTree(root, ctx)
                   shot = Shot((0 until h.drawnBuffer.height).toVector.map(y =>
                            (0 until w).toVector.map(x => h.drawnBuffer.get(x, y).getOrElse(Cell.Empty))
                          ))
                   result <- dispatcher.dispatch(ctrl(SpecialKeyCode.PgDn), layout, root, ctx)
                 yield (acc :+ shot, result != EventResult.Ignored && acc.size < 200)
               }
    yield shots._1

  private val red = Foreground.Named(FgColor.Red)

  private val requiredLabels =
    Seq("Avatar", "Introduction", "Rating", "Name", "Personality", "Scenario", "First Message", "System Prompt")

  private val counters = Seq(
    "Characters: 0/100",
    "Characters: 40/80",
    "Characters: 0/2000",
    "Characters: 8/100",
    "Characters: 69/5000",
    "Characters: 65/2200",
    "Characters: 41/2200"
  )

  private def everyRow(w: Int, h: Int) =
    test(s"every row of the reference appears while paging through at ${w}x$h") {
      for shots <- pageThrough(w, h)
      yield
        def seen(text: String): Boolean = shots.exists(_.rows.exists(_.contains(text)))
        def onOneRow(a: String, b: String): Boolean = shots.exists(_.rows.exists(r => r.contains(a) && r.contains(b)))
        def cell(text: String, x: Int = 0): Option[Cell] = shots.iterator.flatMap(_.cellIn(text, x)).nextOption()
        def styledCell(p: CellStyle => Boolean): Boolean = shots.exists(_.cells.exists(_.exists(c => p(c.style))))
        assertTrue(
          // 25: the page is taller than the terminal, so it pages.
          shots.size > 1,
          // 1-3: title, header buttons, model.
          seen("Create Character"),
          onOneRow("Generate Character", "Clear"),
          seen("Mistral-Nemo Instruct 12B"),
          // 4-5: three sections, titles on the border; the Step 2 subtitle.
          seen("─ Step 1: Add Display Info"),
          seen("─ Step 2: Build Your Character"),
          seen("─ Step 3: Configure LLM Settings"),
          seen("It is best to use '{{char}}'"),
          // 6: every required label carries a red marker, and only the marker is red.
          requiredLabels.forall(l => cell(s"$l*", l.length).exists(_.style.fg == red)),
          requiredLabels.forall(l => cell(s"$l*").exists(_.style.fg != red)),
          // 7-9: help text, the image previews, Avatar beside Background Image.
          seen("Upload a PNG, JPG, GIF,"),
          styledCell(_ == CellStyle(bg = Background.Named(BgColor.BrightBlack))),
          onOneRow("Avatar*", "Background Image"),
          // 10-11: the dashed drop zone and its caption, the AI image button.
          seen("┄"),
          seen("Drag & drop up to 10"),
          seen("AI Generated Image"),
          // 12-13: pre-filled single-line inputs and all seven counters.
          seen("A knowledgeable and helpful AI assistant"),
          seen("HammerAI"),
          counters.forall(seen),
          // 14-15: Safe / Mature on one row; the tag field and its remaining count.
          onOneRow("Safe", "Mature"),
          seen("Tags remaining: 6"),
          // 16-17: the text areas; Show Visual View on the Example Dialogues label row.
          seen("Creator's Note"),
          seen("{{char}} is a knowledgeable and friendly AI who fulfills any request."),
          seen("{{char}} is a virtual assistant who lives on {{user}}'s computer."),
          seen("#{{user}}: Hey {{char}}. Who are you?"),
          seen("Hello {{user}}. How can I help you today?"),
          seen("You are {{char}}."),
          onOneRow("Example Dialogues", "Show Visual View"),
          // 18-21: the buttons; Delete all in the danger role; the lorebook import row.
          seen("+ Add │"),
          seen("+ Add Alternate First Message"),
          onOneRow("+ Add Entry", "Delete all"),
          cell("Delete all").exists(_.style.fg == red),
          seen("Import Lorebook"),
          onOneRow("Choose File", "No file chosen"),
          // 22-24: four sliders with their defaults and boxes; Save Location; the muted save button.
          Seq("Temperature (?)", "Repetition Penalty (?)", "Top P (?)", "Top K (?)").forall(seen),
          seen("Default"),
          onOneRow("Temperature (?)", "0,8"),
          onOneRow("Save Location (?)", "Local"),
          cell("Save Character Locally").exists(_.style.attributes.contains(Attribute.Dim))
        )
    }

  private def tabWalk(w: Int, h: Int) =
    test(s"Tab visits every control in reading order and the viewport follows, at ${w}x$h") {
      for
        panel <- CharacterFormPanel.make
        root = panel.root
        // The whole page in view: the reading order of the controls.
        full = LayoutManager.default.resolve(root, Rect(0, 0, w, 400))
        order = full.focusOrder.entries
        fm <- FocusManager.make(FocusPolicy.DropOnRemoval)
        h0 <- FrameHarness.make(w, h)
        l0 <- h0.renderTree(root, RenderContext.empty)
        _  <- fm.setOrder(l0.focusOrder)
        visits <- ZIO.foreach(order.indices.toVector) { _ =>
                    for
                      _       <- fm.focusNext
                      focused <- fm.focused
                      h       <- FrameHarness.make(w, h)
                      layout  <- h.renderTree(root, RenderContext(FocusSnapshot(focused)), reveal = focused)
                      _       <- fm.setOrder(layout.focusOrder)
                    yield (focused, focused.flatMap(layout.rects.get))
                  }
      yield
        // Reading order: down the page by bottom edge, left to right along a band.
        val readingOrder = order.zip(order.drop(1)).forall { (a, b) =>
          val (aBottom, bBottom) = (a.area.y + a.area.height, b.area.y + b.area.height)
          bBottom > aBottom || (bBottom == aBottom && b.area.x > a.area.x)
        }
        assertTrue(
          order.size == 28,
          readingOrder,
          visits.map(_._1) == order.map(e => Some(e.id)),
          visits.forall((_, rect) => rect.exists(r => r.y >= 0 && r.y + r.height <= h))
        )
    }

  def spec: Spec[TestEnvironment & Scope, Any] = suite("CharacterFormPanel")(
    everyRow(80, 24),
    everyRow(120, 40),
    tabWalk(80, 24),
    tabWalk(120, 40)
  )
