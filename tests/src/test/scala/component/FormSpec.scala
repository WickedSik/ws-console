package io.github.wickedsik.wsconsole
package component

import ansi.FgColor
import buffer.{Attribute, CellStyle, Foreground, ScreenBuffer}
import geometry.Rect
import layout.Constraint
import render.LayoutManager
import testkit.RenderHarness.renderToBuffer

import zio.*
import zio.test.*

object FormSpec extends ZIOSpecDefault:

  private val style = FormStyle()

  /** Row `y` of `buf` as one char per cell, blank for an empty cell. */
  private def row(buf: ScreenBuffer, y: Int): String =
    (0 until buf.width).map(x => buf.get(x, y).map(_.char).getOrElse(' ')).mkString

  private def resolve(c: Component, w: Int, h: Int) = LayoutManager.default.resolve(c, Rect(0, 0, w, h))

  /** Help text that wraps to two lines at width 10 and fits one line at 40. */
  private val twoLineHelp = "aaaa bbbb cccc dddd"

  def spec: Spec[TestEnvironment & Scope, Any] = suite("Form")(
    suite("stacked field")(
      test("preferred height is label, help, control and footer, at several widths") {
        for
          input   <- TextInput.make()
          counter <- Label.make("0/100")
        yield
          val bare = Form.content(Seq(Form.Item.Field("Name", input)))
          val full = Form.content(
            Seq(Form.Item.Field("Name", input, help = Some(twoLineHelp), footer = Some(counter)))
          )
          assertTrue(
            // Label 1 + TextInput 3.
            bare.preferredHeight(40).contains(4),
            // Label 1 + help 1 + TextInput 3 + footer 1.
            full.preferredHeight(40).contains(6),
            // The help wraps to two lines.
            full.preferredHeight(10).contains(7)
          )
      },
      test("draws label, help, control and footer in that order") {
        for
          input   <- TextInput.make(value = "HammerAI")
          counter <- Label.make("Characters: 8/100")
        yield
          val form = Form.content(Seq(Form.Item.Field("Name", input, help = Some("The real name."), footer = Some(counter))))
          val buf = renderToBuffer(30, 6)(form)
          assertTrue(
            row(buf, 0).startsWith("Name "),
            row(buf, 1).startsWith("The real name."),
            row(buf, 3).startsWith("│HammerAI"),
            row(buf, 5).startsWith("Characters: 8/100"),
            buf.get(0, 1).exists(_.style.attributes.contains(Attribute.Dim))
          )
      },
      test("the required marker's style lands on the marker cell only") {
        for input <- TextInput.make()
        yield
          val buf = renderToBuffer(20, 4)(Form.content(Seq(Form.Item.Field("Name", input, required = true))))
          val red = Foreground.Named(FgColor.Red)
          assertTrue(
            row(buf, 0).startsWith("Name* "),
            (0 to 3).forall(x => buf.get(x, 0).exists(c => c.style.fg != red && c.style.attributes(Attribute.Bold))),
            buf.get(4, 0).exists(_.style.fg == red),
            buf.get(5, 0).exists(_.style.fg != red)
          )
      },
      test("an aside shares the label row, on its right half") {
        for input <- TextArea.make()
        yield
          val aside = Text("Show Visual View", align = Alignment.Right)
          val form = Form.content(Seq(Form.Item.Field("Example Dialogues", input, aside = Some(aside))))
          assertTrue(row(renderToBuffer(40, 6)(form), 0) == "Example Dialogues       Show Visual View")
      }
    ),
    suite("inline and beside fields")(
      test("inline controls line up across labels of different lengths") {
        for
          a <- TextInput.make()
          b <- TextInput.make()
        yield
          val form = Form.content(
            Seq(
              Form.Item.Field("Top K", a, layout = FieldLayout.Inline),
              Form.Item.Field("Repetition Penalty (?)", b, layout = FieldLayout.Inline)
            )
          )
          val rects = resolve(form, 60, 10).rects
          val column = style.labelWidth + Form.ColumnGap
          assertTrue(rects(a.id).x == column, rects(b.id).x == column, rects(a.id).width == 60 - column)
      },
      test("an inline label sits on the control's middle row") {
        for input <- TextInput.make(value = "0,8")
        yield
          val buf = renderToBuffer(40, 3)(Form.content(Seq(Form.Item.Field("Temperature", input, layout = FieldLayout.Inline))))
          assertTrue(
            row(buf, 0).take(24).isBlank,
            row(buf, 1).startsWith("Temperature"),
            // The field's text row is the same row.
            row(buf, 1).contains("│0,8")
          )
      },
      test("a beside control starts at the top of the label column, past the label width") {
        val preview = RectPlaceholder(CellStyle(attributes = Set(Attribute.Reverse)))
        val control = VBox(Constraint.Fixed(4) -> preview)
        val form = Form.content(
          Seq(Form.Item.Field("Avatar", control, help = Some(twoLineHelp), required = true, layout = FieldLayout.Beside))
        )
        val rects = resolve(form, 40, 6).rects
        assertTrue(
          rects(preview.id) == Rect(style.labelWidth + Form.ColumnGap, 0, 40 - style.labelWidth - Form.ColumnGap, 4),
          form.preferredHeight(40).contains(4)
        )
      }
    ),
    suite("sections and spacing")(
      test("a section puts its title on the top border and its subtitle first, in the help style") {
        for input <- TextInput.make()
        yield
          val form = Form.content(
            Seq(Form.Item.Section("Step 2", Seq(Form.Item.Field("Name", input)), subtitle = Some("Use {{char}}.")))
          )
          val buf = renderToBuffer(30, 8)(form)
          assertTrue(
            row(buf, 0).startsWith("┌─ Step 2 ─"),
            row(buf, 1).startsWith("│ Use {{char}}."),
            buf.get(2, 1).exists(_.style.attributes.contains(Attribute.Dim)),
            row(buf, 2).startsWith("│ Name"),
            // Subtitle 1 + label 1 + TextInput 3 + border 2.
            form.preferredHeight(30).contains(7),
            row(buf, 6).startsWith("└")
          )
      },
      test("items stack gap rows apart") {
        val form = Form.content(Seq(Form.Item.Row(Text("a")), Form.Item.Row(Text("b"))), FormStyle(gap = 2))
        val buf = renderToBuffer(5, 4)(form)
        assertTrue(row(buf, 0).startsWith("a"), row(buf, 1).isBlank, row(buf, 2).isBlank, row(buf, 3).startsWith("b"))
      },
      test("columns share the width, two cells apart") {
        for
          a <- TextInput.make()
          b <- TextInput.make()
        yield
          val form = Form.content(Seq(Form.Item.Columns(Seq(Form.Item.Field("A", a), Form.Item.Field("B", b)))))
          val rects = resolve(form, 42, 4).rects
          assertTrue(rects(a.id) == Rect(0, 1, 20, 3), rects(b.id) == Rect(22, 1, 20, 3))
      },
      test("Tab order is reading order") {
        for
          name  <- TextInput.make()
          left  <- TextInput.make()
          right <- TextInput.make()
          notes <- TextArea.make()
          save  <- Button.make("Save", ZIO.unit)
        yield
          val form = Form.content(
            Seq(
              Form.Item.Section(
                "One",
                Seq(
                  Form.Item.Field("Name", name),
                  Form.Item.Columns(Seq(Form.Item.Field("Left", left), Form.Item.Field("Right", right)))
                )
              ),
              Form.Item.Section("Two", Seq(Form.Item.Field("Notes", notes))),
              Form.Item.Row(save)
            )
          )
          val h = form.preferredHeight(60).getOrElse(0)
          assertTrue(resolve(form, 60, h).focusOrder.ids == Vector(name.id, left.id, right.id, notes.id, save.id))
      },
      test("preferred height equals rendered height") {
        for
          name <- TextInput.make()
          bio  <- TextArea.make(rows = 4)
        yield
          val form = Form.content(
            Seq(
              Form.Item.Section(
                "Card",
                Seq(Form.Item.Field("Name", name, help = Some(twoLineHelp)), Form.Item.Field("Bio", bio)),
                subtitle = Some(twoLineHelp)
              ),
              Form.Item.Row(Text("end"))
            )
          )
          val w = 12
          val h = form.preferredHeight(w).getOrElse(0)
          val buf = renderToBuffer(w, h + 2)(form)
          assertTrue(row(buf, h - 1).startsWith("end"), row(buf, h).isBlank, row(buf, h + 1).isBlank)
      }
    ),
    test("make wraps the form in a ScrollView") {
      for view <- Form.make(Seq(Form.Item.Row(Text("x"))))
      yield assertTrue(view.isInstanceOf[ScrollView])
    }
  )
