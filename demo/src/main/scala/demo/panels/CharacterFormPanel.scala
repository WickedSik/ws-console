package io.github.wickedsik.wsconsole
package demo.panels

import ansi.{BgColor, FgColor}
import app.Panel as AppPanel
import buffer.{Attribute, Background, BoxStyle, CellStyle, Foreground, Span, StyledLine}
import component.*
import glyph.Graphemes
import layout.Constraint

import zio.{UIO, ZIO}

/**
 * The "Create Character" page of a web character editor, rebuilt from
 * library components: the series target of the form scrolls.
 *
 * Three titled sections hold every field of the reference page. The page
 * is taller than any terminal, so the [[component.Form]] scrolls
 * (`Ctrl+PgUp` / `Ctrl+PgDn`, and Tab brings each control into view).
 * Image previews are [[component.RectPlaceholder]]s, there being no image
 * in a terminal, and the icons of the reference are dropped.
 *
 * Every counter is host state: a [[component.Label]] footer the field's
 * `onChange` updates.
 */
object CharacterFormPanel:

  private val muted = CellStyle(attributes = Set(Attribute.Dim))

  private val titleStyle = CellStyle(fg = Foreground.Named(FgColor.BrightWhite), attributes = Set(Attribute.Bold))

  private val controlStyle = CellStyle(fg = Foreground.Named(FgColor.White))

  /** `error` role: a destructive action. */
  private val dangerStyle = CellStyle(fg = Foreground.Named(FgColor.Red))

  /** A grey file-picker button. */
  private val pickerStyle =
    CellStyle(fg = Foreground.Named(FgColor.BrightWhite), bg = Background.Named(BgColor.BrightBlack))

  private val previewStyle = CellStyle(bg = Background.Named(BgColor.BrightBlack))

  private val tagOptions: Seq[String] = Seq(
    "Assistant",
    "Fantasy",
    "Science fiction",
    "Romance",
    "Horror",
    "Mystery",
    "Comedy",
    "Adventure",
    "Historical",
    "Slice of life"
  )

  /** Most tags a character takes. */
  val MaxTags: Int = 6

  /** `Characters: n/max`, counted in graphemes like `maxLength`. */
  private def count(value: String, max: Int): String = s"Characters: ${Graphemes.count(value)}/$max"

  /** A field and its counter footer, when it has one. */
  final private case class Counted[C <: Component](input: C, counter: Option[Label])

  /** A single-line field capped at `max` with its counter footer. */
  private def counted(value: String, max: Int): UIO[Counted[TextInput]] =
    for
      counter <- Label.make(count(value, max), muted)
      input <- TextInput.make(
                 value = value,
                 maxLength = Some(max),
                 style = controlStyle,
                 onChange = v => ZIO.succeed(counter.set(count(v, max)))
               )
    yield Counted(input, Some(counter))

  /** A text area, capped at `max` with its counter footer when the reference shows one. */
  private def area(value: String, rows: Int, max: Option[Int]): UIO[Counted[TextArea]] =
    for
      counter <- ZIO.foreach(max)(m => Label.make(count(value, m), muted))
      input <- TextArea.make(
                 value = value,
                 rows = rows,
                 maxLength = max,
                 style = controlStyle,
                 onChange = v => ZIO.succeed(counter.zip(max).foreach((c, m) => c.set(count(v, m))))
               )
    yield Counted(input, counter)

  /** An image preview: a rectangle `width` by `height` at the top left of its column. */
  private def preview(width: Int, height: Int): Component =
    HBox(
      Constraint.Fixed(width) -> VBox(Constraint.Fixed(height) -> RectPlaceholder(previewStyle)),
      Constraint.Fill         -> Spacer
    )

  /** A component at a fixed width, centred in its row. */
  private def centred(c: Component, width: Int): Component =
    HBox(Constraint.Fill -> Spacer, Constraint.Fixed(width) -> c, Constraint.Fill -> Spacer)

  /**
   * Text starting on a button's text row, so it lines up beside buttons;
   * wraps when the row is narrow.
   */
  private def besideButton(text: String, style: CellStyle = CellStyle.Empty): Component =
    VBox(
      Constraint.Fixed(1) -> Spacer,
      Constraint.Fit      -> WrappedText(StyledLine(Vector(Span(text))), style)
    )

  def make: UIO[AppPanel] =
    for
      generate <- Button.make("Generate Character", ZIO.unit, style = controlStyle)
      clear    <- Button.make("Clear", ZIO.unit, style = controlStyle)

      aiImage      <- Button.make("AI Generated Image", ZIO.unit, style = controlStyle)
      displayName  <- counted("", 100)
      introduction <- counted("A knowledgeable and helpful AI assistant", 80)
      rating       <- RadioGroup.make(Seq("Safe", "Mature"), layout = RadioGroup.Layout.Horizontal())
      tagsLeft     <- Label.make(s"Tags remaining: $MaxTags", muted)
      tags <- TagInput.make(
                tagOptions,
                style = controlStyle,
                maxVisible = 6,
                maxSelected = Some(MaxTags),
                onChange = chosen => ZIO.succeed(tagsLeft.set(s"Tags remaining: ${MaxTags - chosen.size}"))
              )
      creatorsNote <- area("", 2, Some(2000))

      name <- counted("HammerAI", 100)
      personality <-
        area("{{char}} is a knowledgeable and friendly AI who fulfills any request.", 4, Some(5000))
      scenario <-
        area("{{char}} is a virtual assistant who lives on {{user}}'s computer.", 2, Some(2200))
      dialogues <-
        area(
          "#{{user}}: Hey {{char}}. Who are you?\n#{{char}}: I am {{char}}, your AI assistant. How can I help you?",
          3,
          None
        )
      addDialogue  <- Button.make("+ Add", ZIO.unit, style = controlStyle)
      firstMessage <- area("Hello {{user}}. How can I help you today?", 3, Some(2200))
      addAlternate <- Button.make("+ Add Alternate First Message", ZIO.unit, style = controlStyle)
      addEntry     <- Button.make("+ Add Entry", ZIO.unit, style = controlStyle)
      deleteAll    <- Button.make("Delete all", ZIO.unit, style = dangerStyle)
      chooseFile   <- Button.make("Choose File", ZIO.unit, style = pickerStyle)

      systemPrompt <- area(
                        "You are {{char}}. Write the next reply from {{char}} in this never-ending conversation " +
                          "between {{char}} and {{user}}. Gestures and other non-verbal actions are written " +
                          "between asterisks (for example, *waves hello* or *moves closer*).",
                        // Four rows: the whole prompt shows at 80 columns, so it opens at its start.
                        4,
                        None
                      )
      sliders <- ZIO.foreach(SliderDemoPanel.settings)(s => SliderDemoPanel.control(s).map(s -> _))
      saveLocation <- RadioGroup.make(
                        Seq("Cloud", "Local"),
                        selected = 1,
                        layout = RadioGroup.Layout.Horizontal(gap = 4),
                        style = controlStyle
                      )
      saveLocally <- Button.make("Save Character Locally", ZIO.unit, style = muted, enabled = false)

      form <- Form.make(
                Seq(
                  Form.Item.Row(
                    HBox(
                      Constraint.Fill      -> besideButton("Create Character", titleStyle),
                      Constraint.Fixed(22) -> generate,
                      Constraint.Fixed(1)  -> Spacer,
                      Constraint.Fixed(9)  -> clear,
                      Constraint.Fixed(27) -> VBox(
                        Constraint.Fixed(1) -> Spacer,
                        Constraint.Fixed(1) -> Text("Mistral-Nemo Instruct 12B", muted, Alignment.Right)
                      )
                    )
                  ),
                  Form.Item.Section(
                    "Step 1: Add Display Info (this will not influence the character)",
                    Seq(
                      Form.Item.Columns(
                        Seq(
                          Form.Item.Field(
                            "Avatar",
                            preview(10, 5),
                            help = Some(
                              "Upload a PNG, JPG, GIF, or WEBP image, or a short silent MP4 clip. Width and height " +
                                "must be between 512 and 4200 pixels. Images must be under 15 MB; MP4s are muted " +
                                "and must be under 10 MB."
                            ),
                            required = true,
                            layout = FieldLayout.Beside
                          ),
                          Form.Item.Field(
                            "Background Image",
                            preview(10, 5),
                            help = Some(
                              "Optionally upload a PNG, JPG, GIF, or WEBP image up to 2500x2500 pixels and under 15 MB."
                            ),
                            layout = FieldLayout.Beside
                          )
                        )
                      ),
                      Form.Item.Field(
                        "Gallery",
                        Panel(
                          border = BoxStyle.Dashed,
                          style = muted,
                          child = VBox(
                            Constraint.Fixed(1) -> Spacer,
                            Constraint.Fit -> WrappedText(
                              StyledLine(Vector(Span("Drag & drop up to 10 images here, or click to upload."))),
                              muted,
                              Alignment.Center
                            ),
                            Constraint.Fixed(1) -> Spacer
                          )
                        ),
                        help = Some(
                          "Optionally upload up to 10 images. Each must be a PNG, JPG, GIF, or WEBP image up to " +
                            "4200x4200 pixels and under 15 MB."
                        ),
                        layout = FieldLayout.Beside
                      ),
                      Form.Item.Row(aiImage),
                      Form.Item.Field(
                        "Display Name",
                        displayName.input,
                        help = Some("The character's display name."),
                        footer = displayName.counter
                      ),
                      Form.Item.Field(
                        "Introduction",
                        introduction.input,
                        help = Some(
                          "This will be displayed in the UI and not in the prompt, so it won't influence the character."
                        ),
                        footer = introduction.counter,
                        required = true
                      ),
                      Form.Item.Field(
                        "Rating",
                        rating,
                        help = Some(
                          "Safe characters are safe for all ages. Mature characters may contain adult themes but " +
                            "must still follow the Terms of Service."
                        ),
                        required = true
                      ),
                      Form.Item.Field("Tags", tags, footer = Some(tagsLeft)),
                      Form.Item.Field(
                        "Creator's Note",
                        creatorsNote.input,
                        help = Some(
                          "Use this to tell others about best-practices, recommendations, or anything else you think " +
                            "is important as people chat with your character."
                        ),
                        footer = creatorsNote.counter
                      )
                    )
                  ),
                  Form.Item.Section(
                    "Step 2: Build Your Character",
                    Seq(
                      Form.Item.Field(
                        "Name",
                        name.input,
                        help = Some("The character's real name. Can include both first and last names."),
                        footer = name.counter,
                        required = true
                      ),
                      Form.Item.Field(
                        "Personality",
                        personality.input,
                        help = Some("Describe the character's persona here."),
                        footer = personality.counter,
                        required = true
                      ),
                      Form.Item.Field(
                        "Scenario",
                        scenario.input,
                        help = Some("The circumstances and context of the conversation."),
                        footer = scenario.counter,
                        required = true
                      ),
                      Form.Item.Field(
                        "Example Dialogues",
                        dialogues.input,
                        help = Some(
                          "Set the stage with a short conversation snippet. Do not delete the hashtags. Add the " +
                            "User message first, then Character."
                        ),
                        aside = Some(Text("Show Visual View", align = Alignment.Right))
                      ),
                      Form.Item.Row(centred(addDialogue, 9)),
                      Form.Item.Field(
                        "First Message",
                        firstMessage.input,
                        help = Some(
                          "This is the first message from your character. Longer first messages are better because " +
                            "they encourage the character to give longer responses."
                        ),
                        footer = firstMessage.counter,
                        required = true
                      ),
                      Form.Item.Field(
                        "Alternate First Messages",
                        addAlternate,
                        help = Some("Optionally, add alternate first messages.")
                      ),
                      Form.Item.Field(
                        "Lorebook",
                        VBox(
                          Constraint.Fit -> HBox(
                            Constraint.Fill      -> addEntry,
                            Constraint.Fixed(1)  -> Spacer,
                            Constraint.Fixed(18) -> deleteAll
                          ),
                          Constraint.Fit -> HBox(
                            Constraint.Fill -> besideButton(
                              "Import Lorebook (i.e. a SillyTavern lorebook from characterhub.org):"
                            ),
                            Constraint.Fixed(13) -> chooseFile,
                            Constraint.Fixed(1)  -> Spacer,
                            Constraint.Fixed(15) -> besideButton("No file chosen")
                          )
                        ),
                        help = Some(
                          "Information that is dynamically inserted into the model prompt. For a given lore item, if " +
                            "the key (case insensitive) appears in the recent chat history, the corresponding value " +
                            "will be included in the prompt when sending the message to the model."
                        )
                      )
                    ),
                    subtitle = Some(
                      "It is best to use '{{char}}' to refer to the character and '{{user}}' for the user - we will " +
                        "replace these magic strings with the character name and user name."
                    )
                  ),
                  Form.Item.Section(
                    "Step 3: Configure LLM Settings",
                    Seq(
                      Form.Item.Field(
                        "System Prompt",
                        systemPrompt.input,
                        help = Some("The base instructions for the model."),
                        required = true
                      )
                    ) ++ sliders.map((setting, band) =>
                      Form.Item.Field(setting.label, band, layout = FieldLayout.Inline)
                    )
                  ),
                  Form.Item.Field("Save Location (?)", saveLocation, layout = FieldLayout.Inline),
                  Form.Item.Row(HBox(Constraint.Fixed(26) -> saveLocally, Constraint.Fill -> Spacer))
                )
              )
    yield AppPanel.of(form)
