package de.lardtom.propwash.ui;

import de.lardtom.propwash.ui.LayoutAudit.Box;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.LayoutAudit.Kind;
import de.lardtom.propwash.ui.LayoutAudit.Text;
import de.lardtom.propwash.ui.LayoutAudit.Widget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutAuditTest {
    private static final Box SCREEN = Box.of(0, 0, 427, 240);
    private static final Box VIEW = Box.of(100, 30, 300, 180);

    private static Text text(String value, int x, int y, int width, Box clip) {
        return new Text(value, Box.of(x, y, width, 8), clip, 1);
    }

    private static List<Kind> kinds(List<Finding> findings) {
        return findings.stream().map(Finding::kind).toList();
    }

    @Test
    void cleanLayoutHasNoFindings() {
        List<Text> texts = List.of(text("Titel", 110, 40, 40, VIEW), text("Zeile", 110, 50, 60, VIEW), text("Fuß", 10, 225, 30, null));
        List<Widget> widgets = List.of(new Widget("Knopf", Box.of(300, 220, 80, 14)), new Widget("Regler", Box.of(110, 60, 150, 14)));
        assertTrue(LayoutAudit.statics(SCREEN, texts, widgets).isEmpty());
        assertTrue(LayoutAudit.scrollRegions(texts).isEmpty());
    }

    @Test
    void textOutsideTheScreenIsReported() {
        List<Finding> findings = LayoutAudit.statics(SCREEN, List.of(text("zu weit rechts", 400, 10, 60, null)), List.of());
        assertEquals(List.of(Kind.OFFSCREEN), kinds(findings));
        List<Finding> widget = LayoutAudit.statics(SCREEN, List.of(), List.of(new Widget("Knopf", Box.of(380, 230, 60, 14))));
        assertEquals(List.of(Kind.OFFSCREEN), kinds(widget));
    }

    @Test
    void oneGlyphRowOfSlackIsNotAClip() {
        Text shadow = new Text("Schatten", new Box(110, 201, 150, 211), VIEW, 1);
        assertFalse(shadow.below());
        Text cut = new Text("Halbe Zeile", new Box(110, 204, 150, 213), VIEW, 1);
        assertTrue(cut.below());
    }

    @Test
    void glyphBoundsMayOverhangTheAdvanceWidthByTwoPixels() {
        Text rightAligned = new Text("1500", new Box(376, 45, 402, 53), VIEW, 1);
        assertFalse(rightAligned.sideways());
        assertTrue(LayoutAudit.statics(SCREEN, List.of(rightAligned), List.of()).isEmpty());
        Text cut = new Text("1500", new Box(378, 45, 404, 53), VIEW, 1);
        assertTrue(cut.sideways());
        Text label = text("Name", 110, 60, 40, null);
        Text value = text("Wert", 148, 60, 30, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(label, value), List.of()).isEmpty());
        Text touching = text("Wert", 147, 60, 30, null);
        assertEquals(List.of(Kind.TEXT_OVERLAP), kinds(LayoutAudit.statics(SCREEN, List.of(label, touching), List.of())));
    }

    @Test
    void everyClipIsProbedEvenWhenTheFoldFallsBetweenRows() {
        Box list = Box.of(10, 30, 80, 160);
        List<Text> texts = List.of(text("Zeile 1", 110, 40, 60, VIEW), text("Zeile 9", 110, 168, 60, VIEW), text("Tab", 14, 40, 30, list),
                text("Fuß", 10, 225, 30, null), text("leer", 0, 0, 10, Box.of(5, 5, 0, 0)));
        assertTrue(LayoutAudit.scrollRegions(texts).isEmpty());
        assertEquals(List.of(VIEW, list), LayoutAudit.clipRegions(texts));
        List<Text> folded = List.of(text("Tab", 14, 40, 30, list), text("halb", 110, 206, 60, VIEW));
        assertEquals(List.of(VIEW, list), LayoutAudit.clipRegions(folded));
    }

    @Test
    void clippedBottomIsOnlyAllowedWhileMoreContentFollows() {
        Text half = text("Status wird mitten im Satz", 110, 206, 120, VIEW);
        List<Text> texts = List.of(text("oben", 110, 40, 30, VIEW), half);
        assertEquals(List.of(VIEW), LayoutAudit.scrollRegions(texts));
        assertTrue(LayoutAudit.statics(SCREEN, texts, List.of()).isEmpty());
        assertTrue(LayoutAudit.vertical(texts, VIEW.centerX(), VIEW.centerY(), true, false).isEmpty());
        List<Finding> atEnd = LayoutAudit.vertical(texts, VIEW.centerX(), VIEW.centerY(), true, true);
        assertEquals(List.of(Kind.CLIPPED), kinds(atEnd));
        assertTrue(atEnd.getFirst().detail().startsWith("unten"));
    }

    @Test
    void textBelowTheViewIsHiddenAtTheEndAndAboveAtTheStart() {
        Text below = text("ganz unten", 110, 230, 60, VIEW);
        Text above = text("ganz oben", 110, 10, 60, VIEW);
        assertNull(below.visible());
        assertEquals(List.of(Kind.HIDDEN), kinds(LayoutAudit.vertical(List.of(below), 200, 100, false, true)));
        assertTrue(LayoutAudit.vertical(List.of(below), 200, 100, true, false).isEmpty());
        assertEquals(List.of(Kind.HIDDEN), kinds(LayoutAudit.vertical(List.of(above), 200, 100, true, false)));
        assertTrue(LayoutAudit.vertical(List.of(above), 200, 100, false, true).isEmpty());
    }

    @Test
    void otherRegionsAreNotJudgedAtThisPoint() {
        Box other = Box.of(10, 30, 80, 100);
        Text elsewhere = text("Liste", 12, 126, 40, other);
        assertTrue(LayoutAudit.vertical(List.of(elsewhere), VIEW.centerX(), VIEW.centerY(), true, true).isEmpty());
        assertEquals(1, LayoutAudit.vertical(List.of(elsewhere), other.centerX(), other.centerY(), true, true).size());
    }

    @Test
    void sidewaysClipIsAlwaysReportedAndAShortStripCountsAsMarquee() {
        Text wide = text("ragt rechts über die Ansicht hinaus", 350, 40, 120, VIEW);
        assertEquals(List.of(Kind.CLIPPED), kinds(LayoutAudit.statics(SCREEN, List.of(wide), List.of())));
        Box strip = Box.of(110, 39, 80, 11);
        Text marquee = text("Laufschrift in einem Knopf", 100, 40, 140, strip);
        assertEquals(List.of(Kind.MARQUEE), kinds(LayoutAudit.statics(SCREEN, List.of(marquee), List.of())));
        Text beside = text("links daneben", 0, 40, 60, VIEW);
        assertEquals(List.of(Kind.HIDDEN), kinds(LayoutAudit.statics(SCREEN, List.of(beside), List.of())));
    }

    @Test
    void overlappingTextsAreReportedButNotOutlinesOrOtherLayers() {
        Text a = text("Erste Zeile", 110, 40, 80, null);
        Text b = text("Zweite Zeile", 150, 44, 80, null);
        assertEquals(List.of(Kind.TEXT_OVERLAP), kinds(LayoutAudit.statics(SCREEN, List.of(a, b), List.of())));
        Text outline = text("Erste Zeile", 111, 41, 80, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(a, outline), List.of()).isEmpty());
        Text tooltip = new Text("Tooltip", Box.of(150, 44, 80, 8), null, 2);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(a, tooltip), List.of()).isEmpty());
        Text nextLine = text("Nächste Zeile", 110, 49, 80, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(a, nextLine), List.of()).isEmpty());
    }

    @Test
    void clippedAwayPartsDoNotCollide() {
        Text shown = text("sichtbar", 110, 200, 60, VIEW);
        Text hidden = text("darunter verdeckt", 110, 215, 60, VIEW);
        Text footer = text("Fußzeile", 110, 216, 60, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(shown, hidden, footer), List.of()).isEmpty());
    }

    @Test
    void widgetsMayNestButNotCross() {
        Widget panel = new Widget("Feld", Box.of(100, 40, 200, 100));
        Widget inner = new Widget("Knopf", Box.of(110, 50, 60, 14));
        Widget crossing = new Widget("Regler", Box.of(280, 60, 60, 14));
        assertTrue(LayoutAudit.statics(SCREEN, List.of(), List.of(panel, inner)).isEmpty());
        assertEquals(List.of(Kind.WIDGET_OVERLAP), kinds(LayoutAudit.statics(SCREEN, List.of(), List.of(panel, crossing))));
        Widget touching = new Widget("Nachbar", Box.of(300, 40, 50, 14));
        assertTrue(LayoutAudit.statics(SCREEN, List.of(), List.of(panel, touching)).isEmpty());
    }

    @Test
    void textMayLieInsideAWidgetButNotStickOut() {
        Widget button = new Widget("Knopf", Box.of(110, 50, 60, 14));
        Text label = text("Speichern", 114, 53, 50, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(label), List.of(button)).isEmpty());
        Text sticking = text("Speichern und schließen", 114, 53, 110, null);
        assertEquals(List.of(Kind.TEXT_ON_WIDGET), kinds(LayoutAudit.statics(SCREEN, List.of(sticking), List.of(button))));
        Text beside = text("Beschriftung", 30, 53, 78, null);
        assertTrue(LayoutAudit.statics(SCREEN, List.of(beside), List.of(button)).isEmpty());
    }

    @Test
    void cutTextNamesBothVersions() {
        Finding finding = LayoutAudit.cut("Spieleinstellung – gilt für alle Drohnen", "Spieleinstellung – gilt…");
        assertEquals(Kind.CUT, finding.kind());
        assertTrue(finding.describe().contains("gilt für alle Drohnen"));
        assertTrue(finding.describe().contains("gilt…"));
    }

    @Test
    void boxGeometry() {
        Box box = Box.of(10, 20, 30, 40);
        assertEquals(40, box.right());
        assertEquals(60, box.bottom());
        assertEquals(25, box.centerX());
        assertEquals(40, box.centerY());
        assertTrue(box.containsPoint(10, 20));
        assertFalse(box.containsPoint(40, 20));
        assertNull(box.intersection(Box.of(40, 20, 10, 10)));
        assertEquals(new Box(30, 50, 40, 60), box.intersection(Box.of(30, 50, 100, 100)));
        assertTrue(new Box(5, 5, 5, 9).empty());
        assertTrue(box.contains(new Box(9, 20, 41, 60), 1));
        assertFalse(box.contains(new Box(8, 20, 40, 60), 1));
    }

    @Test
    void textUnderALaterFillIsCovered() {
        List<Text> texts = List.of(text("Gas 1500", 220, 204, 60, null), text("Weiter", 330, 220, 40, null));
        LayoutAudit.Cover footer = new LayoutAudit.Cover(Box.of(0, 208, 427, 32), 1);
        List<Finding> findings = LayoutAudit.covered(texts, List.of(footer));
        assertEquals(List.of(Kind.COVERED), kinds(findings));
        assertEquals("Gas 1500", findings.getFirst().subject());
        assertTrue(LayoutAudit.covered(texts, List.of(new LayoutAudit.Cover(Box.of(0, 208, 427, 32), 0))).isEmpty());
        assertEquals(List.of(Kind.COVERED), kinds(LayoutAudit.covered(texts, List.of(new LayoutAudit.Cover(Box.of(0, 210, 427, 30), 1)))));
        assertTrue(LayoutAudit.covered(texts, List.of(new LayoutAudit.Cover(Box.of(0, 211, 427, 29), 1))).isEmpty());
        assertTrue(LayoutAudit.covered(texts, List.of(new LayoutAudit.Cover(Box.of(277, 200, 3, 20), 1))).isEmpty());
        assertTrue(LayoutAudit.covered(texts, List.of(new LayoutAudit.Cover(Box.of(0, 208, 427, 4), 1))).isEmpty());
    }

    @Test
    void redrawnAndClippedTextIsNotCovered() {
        List<Text> redrawn = List.of(text("Titel", 110, 40, 40, null), text("Titel", 110, 40, 40, null));
        assertTrue(LayoutAudit.covered(redrawn, List.of(new LayoutAudit.Cover(Box.of(100, 30, 200, 30), 1))).isEmpty());
        List<Text> clipped = List.of(text("Zeile", 110, 220, 60, VIEW));
        assertTrue(LayoutAudit.covered(clipped, List.of(new LayoutAudit.Cover(Box.of(100, 195, 200, 40), 1))).isEmpty());
    }

    @Test
    void tooltipHasToStayOnScreenWithItsFrame() {
        Box screen = Box.of(0, 0, 320, 240);
        assertNull(LayoutAudit.tooltip(screen, "Max. Scharf-Winkel", Box.of(21, 5, 166, 149)));
        assertNull(LayoutAudit.tooltip(screen, "Rand", Box.of(4, 4, 312, 232)));
        Finding above = LayoutAudit.tooltip(screen, "Max. Scharf-Winkel", Box.of(21, -15, 166, 149));
        assertNotNull(above);
        assertEquals(Kind.TOOLTIP, above.kind());
        assertEquals("Max. Scharf-Winkel", above.subject());
        assertTrue(above.detail().startsWith("ragt aus dem Bildschirm"), above.detail());
        Finding wide = LayoutAudit.tooltip(screen, "Taste Y", Box.of(4, 46, 379, 9));
        assertNotNull(wide);
        assertTrue(wide.detail().startsWith("ist breiter als der Bildschirm"), wide.detail());
        Finding tall = LayoutAudit.tooltip(screen, "Lang", Box.of(40, 5, 170, 260));
        assertNotNull(tall);
        assertTrue(tall.detail().startsWith("ist höher als der Bildschirm"), tall.detail());
        assertNotNull(LayoutAudit.tooltip(screen, "Rahmen", Box.of(102, 190, 219, 41)));
    }

    @Test
    void deadScrollbarNamesTrackAndThumb() {
        Finding finding = LayoutAudit.deadScrollbar(Box.of(305, 110, 3, 61), Box.of(305, 110, 3, 12), "lässt sich nicht ziehen");
        assertEquals(Kind.SCROLLBAR, finding.kind());
        assertTrue(finding.subject().contains("305,110 3x61"), finding.subject());
        assertTrue(finding.detail().contains("305,110 3x12"), finding.detail());
    }
}
