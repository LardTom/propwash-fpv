package de.lardtom.propwash.guide;

import de.lardtom.propwash.guide.layout.CalloutLayout;
import de.lardtom.propwash.guide.layout.LabelRect;
import de.lardtom.propwash.guide.scene.Cue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalloutLayoutTest {
    private static final LabelRect VIEW = new LabelRect(10.0F, 40.0F, 626.0F, 296.0F);
    private static final LabelRect AREA = new LabelRect(26.0F, 62.0F, 610.0F, 280.0F);
    private static final List<CalloutLayout.Size> SIZE = List.of(new CalloutLayout.Size(120.0F, 28.0F));

    private static CalloutLayout.Label label(String id, float x, float y, Cue.Side side) {
        return new CalloutLayout.Label(id, x, y, side, SIZE, null, null);
    }

    private static List<CalloutLayout.Placement> frame(CalloutLayout layout, float dt, CalloutLayout.Label... labels) {
        return layout.update(VIEW, AREA, dt, false, 1.0F, List.of(labels), List.of());
    }

    @Test
    void labelStaysWhileItsAnchorWanders() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect first = frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT)).getFirst().box();
        for (int i = 1; i <= 120; i++) {
            float x = 300.0F + 18.0F * (float) Math.sin(i * 0.11F);
            float y = 170.0F + 10.0F * (float) Math.cos(i * 0.07F);
            CalloutLayout.Placement placed = frame(layout, 1.0F, label("a", x, y, Cue.Side.RIGHT)).getFirst();
            assertEquals(first, placed.box(), "Kasten bewegt sich bei Bild " + i);
            assertEquals(x, placed.anchorX());
            assertEquals(y, placed.anchorY());
        }
        assertEquals(0L, layout.stats().moves());
    }

    @Test
    void labelGlidesInsteadOfJumpingWhenItsAnchorRunsAway() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect start = frame(layout, 1.0F, label("a", 150.0F, 170.0F, Cue.Side.RIGHT)).getFirst().box();
        float lastX = start.x0();
        float lastY = start.y0();
        float lastAlpha = 1.0F;
        float largest = 0.0F;
        int gliding = 0;
        for (int i = 1; i <= 80; i++) {
            float x = Math.min(450.0F, 150.0F + i * 15.0F);
            CalloutLayout.Placement placed = frame(layout, 1.0F, label("a", x, 170.0F, Cue.Side.RIGHT)).getFirst();
            float dx = placed.box().x0() - lastX;
            float dy = placed.box().y0() - lastY;
            if (placed.alpha() >= 0.999F && lastAlpha >= 0.999F) {
                largest = Math.max(largest, (float) Math.sqrt(dx * dx + dy * dy));
            }
            assertFalse(placed.alpha() >= 0.999F && placed.gliding() && placed.box().contains(placed.anchorX(), placed.anchorY(), 0.0F)
                    && placed.box().x0() != placed.from().x0() && placed.box().x0() != placed.target().x0(), "gleitet über den Anker bei " + i);
            lastX = placed.box().x0();
            lastY = placed.box().y0();
            lastAlpha = placed.alpha();
            if (placed.gliding()) {
                gliding++;
            }
            assertFalse(placed.target().contains(placed.anchorX(), placed.anchorY(), CalloutLayout.ANCHOR_CLEARANCE));
        }
        assertTrue(gliding >= CalloutLayout.GLIDE_MIN, "gleitet nur " + gliding + " Bilder");
        assertTrue(largest <= 30.0F, "Sprung von " + largest + " px in einem Tick");
        CalloutLayout.Placement end = frame(layout, 1.0F, label("a", 450.0F, 170.0F, Cue.Side.RIGHT)).getFirst();
        assertFalse(end.gliding());
        assertTrue(end.box().distance(450.0F, 170.0F) <= CalloutLayout.GAP + 1.0F, "Kasten bleibt fern vom Anker");
        assertTrue(layout.stats().moves() <= 4, "zu viele Umzüge: " + layout.stats().moves());
    }

    @Test
    void sideSwitchFadesInsteadOfSlidingAcrossTheAnchor() {
        CalloutLayout layout = new CalloutLayout();
        CalloutLayout.Label a = label("a", 300.0F, 170.0F, Cue.Side.RIGHT);
        CalloutLayout.Placement first = frame(layout, 1.0F, a).getFirst();
        assertEquals(Cue.Side.RIGHT, first.side());
        List<CalloutLayout.Obstacle> wall = List.of(new CalloutLayout.Obstacle("hint", new LabelRect(first.target().x0() - 4.0F, first.target().y0() - 30.0F,
                first.target().x1() + 4.0F, first.target().y1() + 30.0F), CalloutLayout.Kind.HINT));
        boolean faded = false;
        Cue.Side side = first.side();
        for (int i = 0; i < 40; i++) {
            CalloutLayout.Placement placed = layout.update(VIEW, AREA, 1.0F, false, 1.0F, List.of(a), wall).getFirst();
            side = placed.side();
            assertFalse(placed.box().contains(300.0F, 170.0F, 0.0F), "Kasten über dem Anker bei Bild " + i);
            boolean atStart = placed.box().x0() == first.box().x0();
            boolean atTarget = placed.box().x0() == placed.target().x0();
            assertTrue(atStart || atTarget, "Kasten zwischen den Seiten bei Bild " + i + ": " + placed.box());
            faded |= placed.alpha() < 0.1F;
        }
        assertEquals(Cue.Side.LEFT, side);
        assertTrue(faded, "Seitenwechsel ohne Ausblenden");
    }

    @Test
    void glideIsEasedAndTakesItsTime() {
        assertEquals(0.0F, CalloutLayout.smooth(0.0F));
        assertEquals(1.0F, CalloutLayout.smooth(1.0F));
        assertEquals(0.5F, CalloutLayout.smooth(0.5F), 1.0E-6F);
        assertTrue(CalloutLayout.smooth(0.1F) < 0.1F);
        assertTrue(CalloutLayout.smooth(0.9F) > 0.9F);
        assertEquals(CalloutLayout.GLIDE_MIN, CalloutLayout.glideLength(0.0F));
        assertEquals(CalloutLayout.GLIDE_MAX, CalloutLayout.glideLength(10000.0F));
        assertTrue(CalloutLayout.glideLength(100.0F) > CalloutLayout.glideLength(20.0F));
    }

    @Test
    void sideDoesNotFlipBackAndForthAtTheEdge() {
        CalloutLayout layout = new CalloutLayout();
        List<Cue.Side> sides = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            float x = 470.0F + 30.0F * (float) Math.sin(i * 0.05F);
            CalloutLayout.Placement placed = frame(layout, 1.0F, label("a", x, 170.0F, Cue.Side.RIGHT)).getFirst();
            if (sides.isEmpty() || sides.getLast() != placed.side()) {
                sides.add(placed.side());
            }
        }
        assertTrue(sides.size() <= 2, "Seitenwechsel: " + sides);
    }

    @Test
    void labelsNeverOverlapAndStayInsideTheView() {
        CalloutLayout layout = new CalloutLayout();
        for (int i = 0; i < 300; i++) {
            float swing = 60.0F * (float) Math.sin(i * 0.03F);
            List<CalloutLayout.Placement> placed = frame(layout, 1.0F,
                    label("a", 300.0F + swing, 160.0F, Cue.Side.ABOVE),
                    label("b", 320.0F + swing, 170.0F, Cue.Side.ABOVE),
                    label("c", 290.0F - swing, 180.0F, Cue.Side.RIGHT),
                    label("d", 600.0F, 280.0F, Cue.Side.RIGHT));
            for (int a = 0; a < placed.size(); a++) {
                LabelRect target = placed.get(a).target();
                assertTrue(target.within(AREA, 0.5F), placed.get(a).id() + " ragt aus der Ansicht bei Bild " + i);
                assertTrue(placed.get(a).box().within(AREA, 0.5F), placed.get(a).id() + " gleitet aus der Ansicht bei Bild " + i);
                for (int b = a + 1; b < placed.size(); b++) {
                    assertFalse(target.overlaps(placed.get(b).target(), 0.0F),
                            placed.get(a).id() + "/" + placed.get(b).id() + " überlappen bei Bild " + i);
                }
            }
        }
    }

    @Test
    void olderLabelKeepsItsPlaceWhenANewOneArrives() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect older = frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT)).getFirst().target();
        List<CalloutLayout.Placement> both = frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT),
                label("b", 305.0F, 175.0F, Cue.Side.RIGHT));
        assertEquals(older, both.getFirst().target());
        assertFalse(both.get(1).target().overlaps(older, 0.0F));
        assertEquals(0L, layout.stats().moves());
    }

    @Test
    void keyHintsAreObstacles() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect hint = new LabelRect(330.0F, 140.0F, 470.0F, 200.0F);
        CalloutLayout.Placement placed = layout.update(VIEW, AREA, 1.0F, false, 1.0F, List.of(label("a", 300.0F, 170.0F, Cue.Side.RIGHT)),
                List.of(new CalloutLayout.Obstacle("hint", hint, CalloutLayout.Kind.HINT))).getFirst();
        assertFalse(placed.target().overlaps(hint, 0.0F));
        CalloutLayout later = new CalloutLayout();
        LabelRect free = frame(later, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT)).getFirst().target();
        assertTrue(free.overlaps(hint, 0.0F), "Testaufbau: ohne Hindernis läge der Hinweis im Tastenhinweis");
        CalloutLayout.Placement moved = null;
        for (int i = 0; i < 30; i++) {
            moved = later.update(VIEW, AREA, 1.0F, false, 1.0F, List.of(label("a", 300.0F, 170.0F, Cue.Side.RIGHT)),
                    List.of(new CalloutLayout.Obstacle("hint", hint, CalloutLayout.Kind.HINT))).getFirst();
        }
        assertFalse(moved.target().overlaps(hint, 0.0F));
        assertFalse(moved.gliding());
    }

    @Test
    void ownObjectStaysVisible() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect drone = new LabelRect(250.0F, 140.0F, 350.0F, 200.0F);
        CalloutLayout.Label label = new CalloutLayout.Label("a", 300.0F, 170.0F, Cue.Side.RIGHT, SIZE, "drone", drone);
        CalloutLayout.Placement placed = layout.update(VIEW, AREA, 1.0F, false, 1.0F, List.of(label),
                List.of(new CalloutLayout.Obstacle("drone", drone, CalloutLayout.Kind.ACTOR))).getFirst();
        assertTrue(placed.target().overlap(drone) / placed.target().area() <= 0.2F);
    }

    @Test
    void resetPlacesFreshAndRunsAreDeterministic() {
        List<List<CalloutLayout.Placement>> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            CalloutLayout layout = new CalloutLayout();
            List<CalloutLayout.Placement> all = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                float x = 200.0F + i * 1.7F;
                all.addAll(layout.update(VIEW, AREA, 0.7F + (i % 3) * 0.4F, i == 120, 1.0F,
                        List.of(label("a", x, 160.0F, Cue.Side.ABOVE), label("b", 500.0F - i, 200.0F, Cue.Side.LEFT)), List.of()));
            }
            runs.add(all);
            assertEquals(1L, layout.stats().resets());
        }
        assertEquals(runs.get(0), runs.get(1));
        CalloutLayout fresh = new CalloutLayout();
        CalloutLayout used = new CalloutLayout();
        for (int i = 0; i < 50; i++) {
            frame(used, 1.0F, label("a", 100.0F + i * 6.0F, 160.0F, Cue.Side.ABOVE));
        }
        CalloutLayout.Placement afterReset = used.update(VIEW, AREA, 1.0F, true, 1.0F, List.of(label("a", 400.0F, 160.0F, Cue.Side.ABOVE)), List.of())
                .getFirst();
        CalloutLayout.Placement first = frame(fresh, 1.0F, label("a", 400.0F, 160.0F, Cue.Side.ABOVE)).getFirst();
        assertEquals(first.target(), afterReset.target());
        assertFalse(afterReset.gliding());
    }

    @Test
    void vanishedLabelIsForgotten() {
        CalloutLayout layout = new CalloutLayout();
        LabelRect first = frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT)).getFirst().target();
        frame(layout, 1.0F);
        LabelRect again = frame(layout, 1.0F, label("a", 100.0F, 120.0F, Cue.Side.RIGHT)).getFirst().target();
        assertNotEquals(first, again);
        assertEquals(2L, layout.stats().placements());
    }

    @Test
    void offscreenAnchorPullsTheLabelToTheEdge() {
        CalloutLayout layout = new CalloutLayout();
        frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT));
        CalloutLayout.Placement placed = null;
        for (int i = 0; i < 60; i++) {
            placed = frame(layout, 1.0F, label("a", 900.0F, 170.0F, Cue.Side.RIGHT)).getFirst();
        }
        assertTrue(placed.target().within(AREA, 0.5F));
        assertTrue(placed.target().distance(VIEW.x1(), 170.0F) <= CalloutLayout.GAP + 1.0F, "Hinweis bleibt nicht am Rand zum Anker: "
                + placed.target());
        assertFalse(placed.gliding());
        assertEquals(900.0F, placed.anchorX());
    }

    @Test
    void labelMakesWayForAnotherLabelsPoint() {
        CalloutLayout layout = new CalloutLayout();
        frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT), label("b", 120.0F, 240.0F, Cue.Side.LEFT));
        List<CalloutLayout.Placement> placed = List.of();
        for (int i = 0; i < 40; i++) {
            placed = frame(layout, 1.0F, label("a", 300.0F, 170.0F, Cue.Side.RIGHT), label("b", 380.0F, 160.0F, Cue.Side.LEFT));
        }
        CalloutLayout.Placement a = placed.getFirst();
        assertFalse(a.target().contains(380.0F, 160.0F, CalloutLayout.ANCHOR_CLEARANCE), "Hinweis a liegt auf dem Punkt von b: " + a.target());
        assertTrue(a.target().distance(300.0F, 170.0F) <= a.target().distance(380.0F, 160.0F) + 12.0F, "Hinweis a steht näher am Punkt von b: " + a.target());
        assertFalse(a.gliding());
    }

    @Test
    void leaderCrossingCountsAgainstACandidate() {
        LabelRect box = new LabelRect(10.0F, 10.0F, 20.0F, 20.0F);
        assertTrue(box.crosses(0.0F, 15.0F, 30.0F, 15.0F));
        assertFalse(box.crosses(0.0F, 0.0F, 30.0F, 5.0F));
        assertTrue(box.crosses(15.0F, 15.0F, 40.0F, 40.0F));
    }
}
