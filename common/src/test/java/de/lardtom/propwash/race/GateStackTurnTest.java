package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateStackTurnTest {
    private static final double EPS = 1.0E-6;
    private static final double X = 10.5;
    private static final double Y = 64.0;
    private static final double Z = -3.5;
    private static final float[] TURNS = {5.0F, 15.0F, 30.0F, 45.0F, 60.0F, 90.0F, 135.0F, 270.0F, -45.0F};
    private static final long MS = 1_000_000L;
    private static final int COURSE = 7;

    private static GateGeometry lower(GateSize size, float heading) {
        return GatePlacement.turnedTo(GateGeometry.of(X, Y, Z, 0.0F, size), heading);
    }

    private static GateGeometry onTop(GateGeometry lower, GateSize size, float heading) {
        return GatePlacement.turnedTo(GateGeometry.of(lower.x(), lower.y() + lower.height(), lower.z(), 0.0F, size), heading);
    }

    private static List<double[]> boxes(GateGeometry gate, GateStacking.Base base) {
        List<double[]> out = new ArrayList<>();
        gate.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> out.add(new double[]{minX, minY, minZ, maxX, maxY, maxZ}), base);
        return out;
    }

    private static boolean overlaps(GateGeometry a, GateStacking.Base baseA, GateGeometry b, GateStacking.Base baseB) {
        for (double[] p : boxes(a, baseA)) {
            for (double[] q : boxes(b, baseB)) {
                if (p[0] < q[3] - EPS && p[3] > q[0] + EPS && p[1] < q[4] - EPS && p[4] > q[1] + EPS && p[2] < q[5] - EPS && p[5] > q[2] + EPS) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void turnedGateOnTopRestsOnTheCrossbarOfTheLowerGate() {
        for (float base : new float[]{0.0F, 90.0F, 30.0F}) {
            for (float turn : TURNS) {
                GateGeometry lower = lower(GateSize.MEDIUM, base);
                GateGeometry upper = onTop(lower, GateSize.MEDIUM, base + turn);
                String where = base + "° + " + turn + "°";
                assertTrue(GateStacking.restsOn(upper, lower), where + ": gedrehtes Gate liegt auf dem Querbalken");
                List<GateGeometry> gates = List.of(lower, upper);
                assertEquals(List.of(0, 1), GateStacking.column(gates, 1), where);
                assertEquals(GateStacking.Base.LEGS, GateStacking.base(gates, 0), where + ": unten auf Beinen");
                assertEquals(GateStacking.Base.MOUNTED, GateStacking.base(gates, 1), where + ": oben mit eigenem Balken, ohne Füße");
                assertFalse(overlaps(lower, GateStacking.Base.LEGS, upper, GateStacking.Base.MOUNTED), where + ": keine Überschneidung");
                double lowest = Double.POSITIVE_INFINITY;
                for (double[] box : boxes(upper, GateStacking.Base.MOUNTED)) {
                    lowest = Math.min(lowest, box[1]);
                }
                assertEquals(lower.y() + lower.height(), lowest, EPS, where + ": kein Schweben, Unterkante auf dem Balken");
            }
        }
    }

    @Test
    void stackOfThreeTurnedGatesCountsAsOneColumn() {
        GateGeometry bottom = lower(GateSize.LARGE, 0.0F);
        GateGeometry middle = onTop(bottom, GateSize.MEDIUM, 45.0F);
        GateGeometry top = onTop(middle, GateSize.SMALL, 135.0F);
        List<GateGeometry> gates = List.of(bottom, middle, top);
        assertEquals(List.of(0, 1, 2), GateStacking.column(gates, 2));
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(gates, 0));
        assertEquals(GateStacking.Base.MOUNTED, GateStacking.base(gates, 1));
        assertEquals(GateStacking.Base.MOUNTED, GateStacking.base(gates, 2));
        assertEquals(GateStacking.Refusal.NONE, GateStacking.refusal(top, gates.subList(0, 2)));
    }

    @Test
    void removingTheLowerGateLetsTurnedGatesDropAndKeepTheirSeat() {
        GateGeometry bottom = lower(GateSize.MEDIUM, 0.0F);
        GateGeometry middle = onTop(bottom, GateSize.MEDIUM, 45.0F);
        GateGeometry top = GateGeometry.of(X, GateStacking.seat(middle), Z, 0.0F, GateSize.MEDIUM).withTilt(new GateTilt(45.0F, 0.0F, 0.0F));
        List<GateGeometry> gates = List.of(bottom, middle, top);
        assertEquals(List.of(0, 1, 2), GateStacking.column(gates, 0));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(gates, 2), "gleich gedreht: gemeinsamer Balken wie bisher");
        assertEquals(List.of(new GateStacking.Drop(1, Y), new GateStacking.Drop(2, Y + middle.height() - middle.frame())),
                GateStacking.afterRemoval(gates, 0));
        assertEquals(List.of(new GateStacking.Drop(2, Y + bottom.height())), GateStacking.afterRemoval(gates, 1),
                "oberes Gate fällt auf den Balken des unteren, gedreht also obenauf");
    }

    @Test
    void wideGateTurnedOnANarrowOneIsRefused() {
        GateGeometry narrow = lower(GateSize.SMALL, 0.0F);
        for (float turn : TURNS) {
            GateGeometry wide = onTop(narrow, GateSize.LARGE, turn);
            assertFalse(GateStacking.restsOn(wide, narrow), turn + "°");
            assertEquals(GateStacking.Refusal.TOO_WIDE, GateStacking.refusal(wide, List.of(narrow)), turn + "°");
            assertEquals(GateStacking.Base.BAR, GateStacking.base(List.of(narrow, wide), 1), turn + "°");
        }
    }

    @Test
    void turnedGateInSharedBarHeightStaysOutOfTheStack() {
        GateGeometry lower = lower(GateSize.MEDIUM, 0.0F);
        for (float turn : TURNS) {
            GateGeometry low = GatePlacement.turnedTo(GateGeometry.of(X, GateStacking.seat(lower), Z, 0.0F, GateSize.MEDIUM), turn);
            assertFalse(GateStacking.restsOn(low, lower), turn + "°: Beine stecken im Balken, kein Stapel");
            assertEquals(GateStacking.Base.BAR, GateStacking.base(List.of(lower, low), 0), turn + "°");
        }
        GateGeometry flipped = GateGeometry.of(X, GateStacking.seat(lower), Z, 180.0F, GateSize.MEDIUM);
        assertTrue(GateStacking.restsOn(flipped, lower), "um 180° gedreht: gemeinsamer Balken wie bisher");
        GateGeometry above = GateGeometry.of(X, Y + lower.height(), Z, 180.0F, GateSize.MEDIUM);
        assertFalse(GateStacking.restsOn(above, lower), "gleich ausgerichtet obenauf schwebt nicht auf einem zweiten Balken");
    }

    @Test
    void placingWithATurnUsesTheStepsOfFloorGatesAndPivotsOnTheBar() {
        GateGeometry lower = lower(GateSize.MEDIUM, 90.0F);
        GatePlacement.Preference plain = GatePlacement.Preference.DEFAULT;
        assertEquals(0.0F, GateStacking.placementTurn(plain));
        float yaw = GateStacking.stackYaw(lower.yawDegrees(), 95.0F);
        assertEquals(GateStacking.stackOn(lower, GateSize.MEDIUM, yaw), GateStacking.stackOn(lower, GateSize.MEDIUM, yaw, 0.0F),
                "ohne Drehung wie bisher");
        for (float step : GatePlacement.STEPS) {
            GatePlacement.Preference preference = plain;
            for (int n = 1; n <= 8; n++) {
                preference = preference.turnedBy(GateMount.Surface.FLOOR, step);
                float turn = GateStacking.placementTurn(preference);
                assertEquals(GateTilt.wrap(step * n), turn, 1.0E-3F, step + "° × " + n);
                GateGeometry upper = GateStacking.stackOn(lower, GateSize.SMALL, yaw, turn);
                String where = step + "° × " + n;
                assertEquals(GateTilt.wrap(lower.heading() + turn), GateTilt.wrap(upper.heading()), 1.0E-3F, where);
                assertEquals(lower.x(), upper.x(), EPS, where + ": Drehpunkt ist die Mitte der Auflage");
                assertEquals(lower.z(), upper.z(), EPS, where + ": Drehpunkt ist die Mitte der Auflage");
                assertTrue(GateStacking.restsOn(upper, lower), where);
                boolean half = GateStacking.halfTurn(turn);
                assertEquals(half ? GateStacking.seat(lower) : lower.y() + lower.height(), upper.y(), EPS, where);
                assertEquals(half ? GateStacking.Base.STACKED : GateStacking.Base.MOUNTED, GateStacking.base(List.of(lower, upper), 1), where);
            }
        }
        GatePlacement.Preference lying = new GatePlacement.Preference(GateMount.FLOOR_FLAT, 1, GateMount.WALL_DIVE, 0, GateMount.HANGING, 0);
        assertEquals(0.0F, GateStacking.placementTurn(lying), "liegende Vorliebe dreht das Stapeln nicht");
    }

    @Test
    void halfTurnsAreRecognised() {
        for (float degrees : new float[]{0.0F, 180.0F, -180.0F, 360.0F, 540.0F}) {
            assertTrue(GateStacking.halfTurn(degrees), degrees + "°");
        }
        for (float degrees : new float[]{5.0F, 45.0F, 90.0F, -90.0F, 179.0F}) {
            assertFalse(GateStacking.halfTurn(degrees), degrees + "°");
        }
    }

    private static List<GateGeometry> ladder() {
        GateGeometry bottom = lower(GateSize.LARGE, 0.0F);
        GateGeometry middle = GateStacking.stackOn(bottom, GateSize.MEDIUM, 0.0F, 45.0F);
        GateGeometry top = GateStacking.stackOn(middle, GateSize.SMALL, middle.yawDegrees(), 0.0F);
        return List.of(bottom, middle, top);
    }

    @Test
    void turningALowerGateTurnsTheGatesAboveAndKeepsTheirRelativeTurn() {
        List<GateGeometry> gates = ladder();
        assertEquals(List.of(0, 1, 2), GateStacking.column(gates, 0));
        for (float turn : TURNS) {
            List<GateGeometry> turned = GateStacking.turned(gates, 0, gates.getFirst().heading() + turn);
            assertEquals(3, turned.size());
            for (int i = 0; i < 3; i++) {
                assertEquals(GateTilt.wrap(gates.get(i).heading() + turn), GateTilt.wrap(turned.get(i).heading()), 1.0E-3F, turn + "° Gate " + i);
                assertEquals(gates.get(i).y(), turned.get(i).y(), EPS, turn + "° Gate " + i + " bleibt in der Höhe");
                assertEquals(X, turned.get(i).x(), EPS);
                assertEquals(Z, turned.get(i).z(), EPS);
            }
            assertEquals(List.of(0, 1, 2), GateStacking.column(turned, 2), turn + "°");
            for (int i = 0; i < 3; i++) {
                assertEquals(GateStacking.base(gates, i), GateStacking.base(turned, i), turn + "° Gate " + i);
            }
        }
    }

    @Test
    void turningAMiddleGateBackInLineLowersItOntoTheSharedBar() {
        List<GateGeometry> gates = ladder();
        GateGeometry bottom = gates.getFirst();
        List<GateGeometry> back = GateStacking.turned(gates, 1, 180.0F);
        List<GateGeometry> after = List.of(bottom, back.get(0), back.get(1));
        assertEquals(GateStacking.seat(bottom), back.getFirst().y(), EPS, "gleich ausgerichtet: gemeinsamer Balken");
        assertEquals(gates.get(2).y() - bottom.frame(), back.get(1).y(), EPS, "das Gate darüber rutscht mit");
        assertEquals(List.of(0, 1, 2), GateStacking.column(after, 0));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(after, 1));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(after, 2));
        assertEquals(GateTilt.wrap(gates.get(2).heading() + 135.0F), GateTilt.wrap(back.get(1).heading()), 1.0E-3F);
        List<GateGeometry> current = gates;
        for (int i = 0; i < 24; i++) {
            List<GateGeometry> step = GateStacking.turned(current, 1, current.get(1).heading() + 15.0F);
            current = List.of(bottom, step.get(0), step.get(1));
            assertEquals(List.of(0, 1, 2), GateStacking.column(current, 0), "Schritt " + i);
        }
        for (int i = 0; i < 3; i++) {
            assertTrue(current.get(i).sameAs(gates.get(i)), "nach 24 × 15° wieder gleich: Gate " + i);
        }
    }

    @Test
    void headingOnlyAcceptsPureTurnsOfStackedGates() {
        GateGeometry lower = lower(GateSize.MEDIUM, 0.0F);
        GateGeometry upper = GateStacking.stackOn(lower, GateSize.MEDIUM, 0.0F);
        GateSize size = GateSize.MEDIUM;
        GateGeometry turned = GatePlacement.edited(upper, 45.0F, 0.0F, 0.0F, size.width(), size.height());
        assertTrue(GateMounting.headingOnly(upper, turned));
        assertTrue(GateMounting.headingOnly(upper, GatePlacement.edited(upper, 270.0F, 0.0F, 0.0F, size.width(), size.height())));
        assertFalse(GateMounting.headingOnly(upper, GatePlacement.edited(upper, 45.0F, 0.0F, 0.0F, size.width() + 1.0, size.height())));
        assertFalse(GateMounting.headingOnly(upper, GatePlacement.edited(upper, 45.0F, 10.0F, 0.0F, size.width(), size.height())));
        assertFalse(GateMounting.headingOnly(upper, turned.moved(0.0, 0.5, 0.0)));
        assertFalse(GateMounting.headingOnly(upper, turned.moved(0.25, 0.0, 0.0)));
    }

    private static List<LapTracker.Event> fly(List<GateGeometry> gates, List<double[]> path) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(COURSE, gates, 0L);
        tracker.setOpenings(index -> GateStacking.base(gates, index) == GateStacking.Base.LEGS);
        long now = 1_000L * MS;
        double[] at = path.getFirst();
        tracker.update(now, at[0], at[1], at[2]);
        List<LapTracker.Event> events = new ArrayList<>();
        for (int leg = 1; leg < path.size(); leg++) {
            double[] to = path.get(leg);
            double length = Math.sqrt(Math.pow(to[0] - at[0], 2) + Math.pow(to[1] - at[1], 2) + Math.pow(to[2] - at[2], 2));
            int steps = Math.max(1, (int) Math.ceil(length / 0.25));
            for (int i = 1; i <= steps; i++) {
                now += 15 * MS;
                tracker.update(now, at[0] + (to[0] - at[0]) * i / steps, at[1] + (to[1] - at[1]) * i / steps, at[2] + (to[2] - at[2]) * i / steps);
                events.addAll(tracker.drainEvents());
            }
            at = to;
        }
        return events;
    }

    private static long count(List<LapTracker.Event> events, LapTracker.EventType type) {
        return events.stream().filter(e -> e.type() == type).count();
    }

    @Test
    void lapThroughTurnedStacksCountsEveryOpening() {
        for (float turn : TURNS) {
            GateGeometry bottom = lower(GateSize.MEDIUM, 0.0F);
            GateGeometry upper = GateStacking.stackOn(bottom, GateSize.MEDIUM, 0.0F, turn);
            GateGeometry top = GateStacking.stackOn(upper, GateSize.SMALL, 0.0F, turn + 45.0F);
            for (List<GateGeometry> gates : List.of(List.of(bottom, upper), List.of(bottom, upper, top))) {
                List<double[]> path = StackTurnRoute.lap(gates, index -> GateStacking.base(gates, index) == GateStacking.Base.LEGS);
                List<LapTracker.Event> events = fly(gates, path);
                String where = turn + "°, " + gates.size() + " Gates: " + events;
                assertEquals(1, count(events, LapTracker.EventType.START), where);
                assertEquals(gates.size() - 1, count(events, LapTracker.EventType.GATE), where);
                assertEquals(1, count(events, LapTracker.EventType.LAP), where);
                assertEquals(0, count(events, LapTracker.EventType.MISSED) + count(events, LapTracker.EventType.WRONG_WAY), where);
            }
        }
    }

    @Test
    void turnedUpperGateCountsFromItsOwnBarAndKeepsItsFrameSolid() {
        GateGeometry bottom = lower(GateSize.MEDIUM, 0.0F);
        for (float turn : TURNS) {
            GateGeometry upper = GateStacking.stackOn(bottom, GateSize.MEDIUM, 0.0F, turn);
            if (GateStacking.halfTurn(turn)) {
                continue;
            }
            double ahead = 2.0;
            double high = upper.y() + upper.frame() + 0.2;
            double low = upper.y() + upper.frame() * 0.5;
            assertTrue(upper.crossing(X - upper.normalX() * ahead, high, Z - upper.normalZ() * ahead, X + upper.normalX() * ahead, high,
                    Z + upper.normalZ() * ahead, true) >= 0.0, turn + "°: knapp über dem eigenen Balken zählt");
            assertEquals(GateGeometry.NO_CROSSING, upper.crossing(X - upper.normalX() * ahead, low, Z - upper.normalZ() * ahead,
                    X + upper.normalX() * ahead, low, Z + upper.normalZ() * ahead, true), turn + "°: durch den eigenen Balken zählt nicht");
            boolean hit = false;
            for (double[] box : boxes(upper, GateStacking.Base.MOUNTED)) {
                hit |= X > box[0] && X < box[3] && low > box[1] && low < box[4] && Z > box[2] && Z < box[5];
            }
            assertTrue(hit, turn + "°: der eigene Balken kollidiert");
            assertEquals(4, upper.boxCount(GateStacking.Base.MOUNTED), turn + "°");
        }
    }

    @Test
    void courseFileKeepsTheTurnOfAStackedGateAndOldStacksStayTheSame() {
        String json = """
                {"format": 2, "nextCourseId": 2, "courses": [{"id": 1, "dimension": "minecraft:overworld", "owner": "", "ownerName": "",
                  "layout": 1, "worldCourse": "", "site": "", "gates": [
                    {"uuid": "00000000-0000-0000-0000-000000000041", "index": 0, "x": 10.5, "y": 64.0, "z": -3.5, "yaw": 0.0, "size": "medium"},
                    {"uuid": "00000000-0000-0000-0000-000000000042", "index": 1, "x": 10.5, "y": 68.0, "z": -3.5, "yaw": 0.0, "size": "medium",
                     "turn": 45.0},
                    {"uuid": "00000000-0000-0000-0000-000000000043", "index": 2, "x": 10.5, "y": 71.5, "z": -3.5, "yaw": 180.0, "size": "small",
                     "turn": 45.0}],
                  "leaderboard": [], "archive": []}]}
                """;
        CourseRegistry.FileData data = CourseRegistry.read(new StringReader(json));
        assertNotNull(data);
        CourseData loaded = data.courses.getFirst().toData();
        List<GateGeometry> gates = loaded.geometry();
        assertEquals(45.0F, gates.get(1).heading(), 1.0E-3F);
        assertEquals(225.0F, gates.get(2).heading(), 1.0E-3F);
        assertEquals(List.of(0, 1, 2), GateStacking.column(gates, 0));
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(gates, 0));
        assertEquals(GateStacking.Base.MOUNTED, GateStacking.base(gates, 1));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(gates, 2));
        String written = CourseRegistry.write(data);
        CourseRegistry.FileData again = CourseRegistry.read(new StringReader(written));
        assertNotNull(again);
        assertEquals(loaded, again.courses.getFirst().toData());
        assertEquals(loaded.layoutHash(), again.courses.getFirst().toData().layoutHash());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, loaded);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(loaded, decoded);
        assertEquals(loaded.geometry(), decoded.geometry());
        CourseData straight = new CourseData(COURSE, "minecraft:overworld", List.of(
                new CourseData.Gate(0, X, Y, Z, 0.0F, GateSize.MEDIUM),
                new CourseData.Gate(1, X, Y + 3.5, Z, 180.0F, GateSize.MEDIUM)));
        assertEquals(RaceFormatMigrationTest.legacyHash(straight), straight.layoutHash(), "alter Stapel: gleiche Prüfsumme wie bisher");
        List<GateGeometry> old = straight.geometry();
        assertEquals(GateStacking.Base.LEGS, GateStacking.base(old, 0));
        assertEquals(GateStacking.Base.STACKED, GateStacking.base(old, 1), "alter Stapel: oberes Gate wie bisher auf dem gemeinsamen Balken");
        CourseData turned = new CourseData(COURSE, "minecraft:overworld", List.of(
                new CourseData.Gate(0, X, Y, Z, 0.0F, GateSize.MEDIUM),
                new CourseData.Gate(1, X, Y + 4.0, Z, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, new GateTilt(45.0F, 0.0F, 0.0F), 0.0, 0.0)));
        assertNotEquals(straight.layoutHash(), turned.layoutHash(), "Drehung gehört zum Layout");
    }
}
