package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackRowTest {
    private static final double EPS = 1.0E-9;
    private static final double DEFLATE = 0.01;
    private static final long MS = 1_000_000L;
    private static final double Y = 64.0;

    private static final class World {
        final List<GateGeometry> gates = new ArrayList<>();

        GateGeometry collider(int index) {
            GateGeometry own = this.gates.get(index);
            List<GateGeometry> others = new ArrayList<>(this.gates);
            others.remove(index);
            return own.withJoints(GateStacking.joints(own, others).mask());
        }

        GateStacking.Base base(int index) {
            return GateStacking.base(this.gates, index);
        }

        @org.jspecify.annotations.Nullable String blocker(GateGeometry placed, GateStacking.Base base) {
            List<double[]> mine = boxes(placed, base);
            for (int i = 0; i < this.gates.size(); i++) {
                for (double[] other : boxes(this.collider(i), this.base(i))) {
                    for (double[] part : mine) {
                        if (part[0] + DEFLATE < other[3] && part[3] - DEFLATE > other[0] && part[1] + DEFLATE < other[4] && part[4] - DEFLATE > other[1]
                                && part[2] + DEFLATE < other[5] && part[5] - DEFLATE > other[2]) {
                            return "Gate " + i + " bei " + text(this.gates.get(i));
                        }
                    }
                }
            }
            return null;
        }

        int floor(double x, double z, float yaw, GateSize size) {
            GateGeometry raw = GateGeometry.of(x, Y, z, yaw, size);
            GateGeometry snapped = GateStacking.snap(raw, this.gates);
            GateGeometry placed = snapped.withJoints(GateStacking.placementJoints(snapped, this.gates));
            String blocked = this.blocker(placed, GateStacking.placedBase(placed));
            assertNull(blocked, "am Boden " + text(placed) + " blockiert durch " + blocked);
            this.gates.add(snapped);
            return this.gates.size() - 1;
        }

        int stack(int index, GateSize size) {
            List<Integer> column = GateStacking.column(this.gates, index);
            GateGeometry top = this.gates.get(column.getLast());
            assertEquals(GateStacking.Refusal.NONE, GateStacking.check(top, column.size(), size), "Stapel " + column.size());
            GateGeometry stacked = GateStacking.stackFor(top, top.yawDegrees(), size, top.yawDegrees(), GatePlacement.Preference.DEFAULT);
            GateGeometry placed = GateStacking.joined(stacked, this.gates);
            String blocked = this.blocker(placed, GateStacking.stackBase(placed, top));
            assertNull(blocked, "gestapelt " + text(placed) + " (Stapelhöhe " + (column.size() + 1) + ") blockiert durch " + blocked);
            assertEquals(stacked.x(), placed.x(), EPS, "die Säule bestimmt die Lage");
            assertEquals(stacked.y(), placed.y(), EPS);
            assertEquals(stacked.z(), placed.z(), EPS);
            this.gates.add(stacked);
            assertEquals(column.size() + 1, GateStacking.column(this.gates, this.gates.size() - 1).size());
            return this.gates.size() - 1;
        }

        int column(int bottom, int height, GateSize size) {
            int top = bottom;
            for (int i = 1; i < height; i++) {
                top = this.stack(top, size);
            }
            return top;
        }

        int sharedPosts() {
            int dropped = 0;
            for (int i = 0; i < this.gates.size(); i++) {
                GateGeometry collider = this.collider(i);
                dropped += Integer.bitCount(collider.joints() & (GateGeometry.JOINT_LEFT | GateGeometry.JOINT_RIGHT));
            }
            return dropped;
        }

        void assertNoOverlap() {
            for (int i = 0; i < this.gates.size(); i++) {
                for (int j = i + 1; j < this.gates.size(); j++) {
                    for (double[] p : boxes(this.collider(i), this.base(i))) {
                        for (double[] q : boxes(this.collider(j), this.base(j))) {
                            boolean overlap = p[0] + DEFLATE < q[3] && p[3] - DEFLATE > q[0] && p[1] + DEFLATE < q[4] && p[4] - DEFLATE > q[1]
                                    && p[2] + DEFLATE < q[5] && p[5] - DEFLATE > q[2];
                            assertFalse(overlap, "Gate " + i + " und " + j + " überlappen: " + text(this.gates.get(i)) + " / " + text(this.gates.get(j)));
                        }
                    }
                }
            }
        }
    }

    private static List<double[]> boxes(GateGeometry gate, GateStacking.Base base) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> out.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}), base);
        return out;
    }

    private static String text(GateGeometry gate) {
        return String.format(java.util.Locale.ROOT, "%.2f/%.2f/%.2f %.0f° %.1f breit", gate.x(), gate.y(), gate.z(), gate.yawDegrees(), gate.width());
    }

    @Test
    void secondStackDirectlyBesideTheFirstSharesThePostInEveryOrder() {
        int[][] orders = {{0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}, {1, 3, 0, 2}};
        for (int[] order : orders) {
            World world = new World();
            int[] placed = {-1, -1, -1, -1};
            for (int step : order) {
                switch (step) {
                    case 0 -> placed[0] = world.floor(0.5, 0.5, 0.0F, GateSize.MEDIUM);
                    case 1 -> placed[1] = world.floor(placed[0] < 0 ? 4.0 : 4.5, 0.5, 0.0F, GateSize.MEDIUM);
                    case 2 -> placed[2] = world.stack(placed[0], GateSize.MEDIUM);
                    default -> placed[3] = world.stack(placed[1], GateSize.MEDIUM);
                }
            }
            String where = "Reihenfolge " + java.util.Arrays.toString(order);
            assertEquals(4.0 - 0.5, world.gates.get(placed[1]).x() - world.gates.get(placed[0]).x(), EPS, where + ": Pfostenmitte auf Pfostenmitte");
            assertEquals(2, world.sharedPosts(), where + ": unten und oben je ein geteilter Pfosten");
            assertEquals(GateGeometry.JOINT_LEFT, world.collider(placed[3]).joints(), where + ": das östliche obere Gate lässt den Pfosten weg");
            assertEquals(0, world.collider(placed[2]).joints(), where + ": das westliche behält ihn");
            assertEquals(GateStacking.Base.STACKED, world.base(placed[3]));
            assertEquals(GateStacking.Base.LEGS, world.base(placed[1]));
            world.assertNoOverlap();
        }
    }

    @Test
    void stacksOfEveryHeightStandSideBySideInBothDirections() {
        for (int west = 1; west <= GateStacking.MAX_STACK; west++) {
            for (int east = 1; east <= GateStacking.MAX_STACK; east++) {
                for (boolean eastFirst : new boolean[]{false, true}) {
                    World world = new World();
                    int first = world.floor(eastFirst ? 4.0 : 0.5, 0.5, 0.0F, GateSize.MEDIUM);
                    world.column(first, eastFirst ? east : west, GateSize.MEDIUM);
                    int second = world.floor(eastFirst ? 0.0 : 4.5, 0.5, 0.0F, GateSize.MEDIUM);
                    world.column(second, eastFirst ? west : east, GateSize.MEDIUM);
                    String where = "Westen " + west + " hoch, Osten " + east + " hoch, " + (eastFirst ? "Osten zuerst" : "Westen zuerst");
                    assertEquals(Math.min(west, east), world.sharedPosts(), where);
                    world.assertNoOverlap();
                }
            }
        }
    }

    @Test
    void threeStacksInARowShareBothPostsOfTheMiddleOne() {
        for (int height = 1; height <= GateStacking.MAX_STACK; height++) {
            World world = new World();
            int middle = world.floor(4.0, 0.5, 0.0F, GateSize.MEDIUM);
            int east = world.floor(8.0, 0.5, 180.0F, GateSize.MEDIUM);
            int west = world.floor(0.0, 0.5, 0.0F, GateSize.MEDIUM);
            assertEquals(4.0, world.gates.get(middle).x(), EPS);
            assertEquals(7.5, world.gates.get(east).x(), EPS);
            assertEquals(0.5, world.gates.get(west).x(), EPS);
            world.column(middle, height, GateSize.MEDIUM);
            world.column(east, height, GateSize.MEDIUM);
            world.column(west, height, GateSize.MEDIUM);
            assertEquals(2 * height, world.sharedPosts(), height + " hoch");
            world.assertNoOverlap();
            int middleTop = GateStacking.top(world.gates, middle);
            if (height > 1) {
                assertEquals(GateGeometry.JOINT_LEFT, world.collider(middleTop).joints() & GateGeometry.JOINT_LEFT, "Mitte oben teilt links");
            }
        }
    }

    @Test
    void stacksMeetAtACornerWithTheSquarePost() {
        for (GateSize size : GateSize.values()) {
            World world = new World();
            int south = world.floor(0.5, 0.5, 0.0F, size);
            double half = size.width() * 0.5;
            double post = half - size.frame() * 0.5;
            int east = world.floor(0.5 + post + 0.25, 0.5 + post + 0.25, 90.0F, size);
            assertTrue(GateStacking.sharedSide(world.gates.get(east), world.gates.get(south)) != 0, size + ": Ecke eingerastet");
            for (int height = 2; height <= GateStacking.MAX_STACK; height++) {
                world.stack(GateStacking.top(world.gates, south), size);
                world.stack(GateStacking.top(world.gates, east), size);
                assertEquals(height, world.sharedPosts(), size + ", " + height + " hoch: je Lage eine Ecke");
                world.assertNoOverlap();
            }
        }
    }

    @Test
    void realCollisionsAndTheStackLimitStillRefuse() {
        World world = new World();
        int a = world.floor(0.5, 0.5, 0.0F, GateSize.MEDIUM);
        int b = world.floor(4.5, 0.5, 0.0F, GateSize.MEDIUM);
        int top = world.column(a, GateStacking.MAX_STACK, GateSize.MEDIUM);
        assertEquals(GateStacking.Refusal.TOO_HIGH, GateStacking.check(world.gates.get(top), GateStacking.MAX_STACK, GateSize.MEDIUM),
                "das fünfte Gate meldet „höchstens 4“");
        GateGeometry fifth = GateStacking.stackOn(world.gates.get(top), GateSize.MEDIUM, 0.0F);
        assertEquals(GateStacking.Refusal.TOO_HIGH, GateStacking.refusal(fifth, world.gates));
        assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.check(world.gates.get(b), 1, GateSize.LARGE));
        GateGeometry small = GateGeometry.of(3.5, GateStacking.seat(world.gates.get(b)), 0.5, 0.0F, GateSize.SMALL);
        assertEquals(0, GateStacking.joined(small, world.gates).joints(), "kleineres Gate teilt keinen Pfosten mit dem größeren");
        assertTrue(world.blocker(small, GateStacking.Base.STACKED) != null, "kleines Gate im Pfosten des Nachbarn: kein Platz");
        GateGeometry shifted = GateGeometry.of(3.75, GateStacking.seat(world.gates.get(b)), 0.5, 0.0F, GateSize.MEDIUM);
        assertEquals(0, GateStacking.joined(shifted, world.gates).joints(), "versetzt um eine Viertel-Blockbreite teilt nichts");
        assertTrue(world.blocker(GateStacking.joined(shifted, world.gates), GateStacking.Base.STACKED) != null,
                "versetzter Pfosten steckt im Nachbarn: kein Platz");
    }

    private static final class Flight {
        final LapTracker tracker = new LapTracker();
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double y;
        double z;

        Flight(List<GateGeometry> gates, GateOpenings openings, double x, double y, double z) {
            this.tracker.setLapTarget(0);
            this.tracker.setCourse(7, gates, this.now);
            this.tracker.setOpenings(openings);
            this.x = x;
            this.y = y;
            this.z = z;
            this.tracker.update(this.now, x, y, z);
        }

        Flight to(double tx, double ty, double tz) {
            double dx = tx - this.x;
            double dy = ty - this.y;
            double dz = tz - this.z;
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.25));
            double sx = this.x;
            double sy = this.y;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 15 * MS;
                this.x = sx + dx * i / steps;
                this.y = sy + dy * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, this.y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
            return this;
        }

        List<Integer> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
        }
    }

    @Test
    void aLapThroughTheTwoByTwoCountsEveryGateOnce() {
        World world = new World();
        int a = world.floor(0.5, 0.5, 0.0F, GateSize.MEDIUM);
        int b = world.floor(4.5, 0.5, 0.0F, GateSize.MEDIUM);
        int a2 = world.stack(a, GateSize.MEDIUM);
        int b2 = world.stack(b, GateSize.MEDIUM);
        List<GateGeometry> course = List.of(world.gates.get(a), world.gates.get(b), world.gates.get(b2), world.gates.get(a2));
        double ax = world.gates.get(a).x();
        double bx = world.gates.get(b).x();
        double low = Y + 1.5;
        double high = world.gates.get(a2).y() + 2.0;
        double out = bx + 4.0;
        Flight flight = new Flight(course, index -> index < 2, ax, low, -3.0)
                .to(ax, low, 3.0)
                .to(out, low, 3.0).to(out, low, -3.0).to(bx, low, -3.0).to(bx, low, 3.0)
                .to(out, high, 3.0).to(out, high, -3.0).to(bx, high, -3.0).to(bx, high, 3.0)
                .to(out, high, 3.0).to(out, high, -3.0).to(ax, high, -3.0).to(ax, high, 3.0)
                .to(ax - 4.0, high, 3.0).to(ax - 4.0, low, -3.0).to(ax, low, -3.0).to(ax, low, 3.0);
        assertEquals(List.of(1, 2, 3), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.WRONG_WAY).isEmpty(), flight.events.toString());
        Flight wrong = new Flight(course, index -> index < 2, ax, low, -3.0).to(ax, low, 3.0).to(out, low, 3.0).to(out, high, -3.0)
                .to(bx, high, -3.0).to(bx, high, 3.0);
        assertFalse(wrong.of(LapTracker.EventType.GATE).contains(2), "das obere Gate zählt nicht für das untere: " + wrong.events);
        assertEquals(List.of(1), wrong.of(LapTracker.EventType.MISSED), "das untere gilt als ausgelassen: " + wrong.events);
    }
}
