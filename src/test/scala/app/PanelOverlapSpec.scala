package io.github.wickedsik.wsconsole
package app

import buffer.BoxStyle
import component.{Alignment, Text, Panel as CPanel}
import geometry.Rect

import zio.{Scope, ZIO, durationInt}
import zio.test.*

import testkit.CaptureTerminal
import testkit.FrameHarness
import testkit.GridAssertions.{assertChar, assertGrid}

/**
 * Overlapping-panel semantics of [[PanelHost]] driven through the real
 * diff → flush → swap pipeline (via [[FrameHarness]]). Complements the
 * lifecycle-focused [[PanelHostSpec]] with cell-grid assertions on the
 * drawn frame.
 *
 * Bodies use distinct border styles (Single for the lower, Double for the
 * upper) and a labelled title plus a `Text` child that fills the inner
 * width, so overlap regions are unambiguous in the glyph grid.
 */
object PanelOverlapSpec extends ZIOSpecDefault:

  /** Bordered opaque panel body — title on the top edge, `label` filling
    * the first inner row so overlap regions are legible. */
  private def body(label: Char, title: String, border: BoxStyle, width: Int): CPanel =
    val innerWidth = math.max(0, width - 2)
    CPanel(
      child  = Text(label.toString * innerWidth, align = Alignment.Left),
      title  = Some(title),
      border = border
    )

  /** Push each panel via the harness's Frame, so any lifecycle write
    * (`Panel.clearBounds` on unload) lands in the buffer that renders. */
  private def pushAll(host: PanelHost, h: FrameHarness, panels: Panel*) =
    ZIO.foreachDiscard(panels)(p =>
      host.push(p).provide(CaptureTerminal.layer(), h.frameLayer)
    )

  private def pop(host: PanelHost, h: FrameHarness) =
    host.pop.provide(CaptureTerminal.layer(), h.frameLayer)

  def spec: Spec[TestEnvironment & Scope, Any] = suite("PanelHost overlap semantics")(

    // ===== 1. Fully overlapping (identical bounds) =====

    test("identical-bounds overlap: only the top panel is visible") {
      val bounds = Rect(0, 0, 10, 3)
      val a = Panel.of(body('A', "A", BoxStyle.Single, 10), bounds)
      val b = Panel.of(body('B', "B", BoxStyle.Double, 10), bounds)
      for
        h    <- FrameHarness.make(10, 3)
        host <- PanelHost.make()
        _    <- pushAll(host, h, a, b)
        _    <- h.run(host.root)
      yield
        val expected =
          """╔═B══════╗
            |║BBBBBBBB║
            |╚════════╝""".stripMargin
        assertGrid(h.drawnBuffer, expected)
    },

    // ===== 2. Partial overlap (offset bounds) =====

    test("partial overlap: intersection shows top; disjoint zones each show their own panel") {
      // A at (0,0), size 10x3.  B at (5,1), size 10x3.  Overlap = (5,1,5,2).
      val a = Panel.of(body('A', "A", BoxStyle.Single, 10), Rect(0, 0, 10, 3))
      val b = Panel.of(body('B', "B", BoxStyle.Double, 10), Rect(5, 1, 10, 3))
      for
        h    <- FrameHarness.make(15, 4)
        host <- PanelHost.make()
        _    <- pushAll(host, h, a, b)
        _    <- h.run(host.root)
      yield
        // Row 1: A's `│AAAAAAAA│` (cols 0..9) overdrawn from col 5 by B's top
        //        border → `│AAAA╔═B══════╗`.
        // Row 2: A's bottom border (cols 0..9) overdrawn from col 5 by B's
        //        interior (verticals at col 5, col 14; `BBBBBBBB` between).
        val expected =
          """┌─A──────┐.....
            |│AAAA╔═B══════╗
            |└────║BBBBBBBB║
            |.....╚════════╝""".stripMargin
        assertGrid(h.drawnBuffer, expected)
    },

    // ===== 3. Contained (smaller top strictly inside larger bottom) =====

    test("contained overlap: A's border ring survives the smaller B rendered inside") {
      val a = Panel.of(body('A', "A", BoxStyle.Single, 15), Rect(0, 0, 15, 5))
      val b = Panel.of(body('B', "B", BoxStyle.Double, 7),  Rect(4, 1, 7,  3))
      for
        h    <- FrameHarness.make(15, 5)
        host <- PanelHost.make()
        _    <- pushAll(host, h, a, b)
        _    <- h.run(host.root)
      yield
        val expected =
          """┌─A───────────┐
            |│AAA╔═B═══╗AAA│
            |│...║BBBBB║...│
            |│...╚═════╝...│
            |└─────────────┘""".stripMargin
        assertGrid(h.drawnBuffer, expected) &&
        // A's four corners survive B's presence.
        assertChar(h.drawnBuffer,  0, 0, '┌') &&
        assertChar(h.drawnBuffer, 14, 0, '┐') &&
        assertChar(h.drawnBuffer,  0, 4, '└') &&
        assertChar(h.drawnBuffer, 14, 4, '┘')
    },

    // ===== 4. Non-opaque top — host pre-fills top's bounds =====

    test("non-opaque top: cells the top's root does not write are empty, not the lower panel") {
      // A's inner row is `AAAAAAAA`. B's bounds cover it entirely, but
      // B's root is a `Text` writing only three cells. `PanelHost.root`
      // pre-fills B's bounds with `Cell.Empty` before rendering B's
      // root, so unwritten cells are opaquely empty — A does not bleed.
      val a = Panel.of(body('A', "A", BoxStyle.Single, 10), Rect(0, 0, 10, 3))
      val b = Panel.of(Text("XYZ"),                         Rect(1, 1, 8,  1))
      for
        h    <- FrameHarness.make(10, 3)
        host <- PanelHost.make()
        _    <- pushAll(host, h, a, b)
        _    <- h.run(host.root)
      yield
        val expected =
          """┌─A──────┐
            |│XYZ.....│
            |└────────┘""".stripMargin
        assertGrid(h.drawnBuffer, expected)
    },

    // ===== 5. Post-pop reveal — the drawn frame after pop shows A alone =====

    test("post-pop reveal: frame after push shows B; frame after pop shows A alone") {
      val a = Panel.of(body('A', "A", BoxStyle.Single, 10), Rect(0, 0, 10, 3))
      val b = Panel.of(body('B', "B", BoxStyle.Double, 10), Rect(0, 0, 10, 3))
      for
        h    <- FrameHarness.make(10, 3)
        host <- PanelHost.make()
        _    <- pushAll(host, h, a, b)
        _    <- h.run(host.root)   // frame 1: A then B → shows B
        // Capture the intermediate assertion NOW — the `drawnBuffer` reference
        // is one of two ScreenBuffers the manager cycles between, so its cells
        // will be overwritten by the next render's swap.
        frameB = assertGrid(
                   h.drawnBuffer,
                   """╔═B══════╗
                     |║BBBBBBBB║
                     |╚════════╝""".stripMargin
                 )
        _    <- pop(host, h)
        _    <- h.run(host.root)   // frame 2: A alone
      yield
        val expectedA =
          """┌─A──────┐
            |│AAAAAAAA│
            |└────────┘""".stripMargin
        frameB && assertGrid(h.drawnBuffer, expectedA)
    }

  ) @@ TestAspect.timeout(10.seconds)
