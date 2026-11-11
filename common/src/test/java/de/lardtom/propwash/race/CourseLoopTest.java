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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseLoopTest {
    private static final long MS = 1_000_000L;
    private static final double Y = 65.5;
    private static final int COURSE = 3;

    private static final class Flight {
        final LapTracker tracker = new LapTracker();
        final List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double z;

        Flight(List<GateGeometry> gates, double x, double z) {
            this.tracker.setCourse(COURSE, gates, this.now);
            this.tracker.setLapTarget(2);
            this.x = x;
            this.z = z;
            this.tracker.update(this.now, x, Y, z);
        }

        void to(double tx, double tz) {
            double dx = tx - this.x;
            double dz = tz - this.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(length / 0.4));
            double sx = this.x;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 20 * MS;
                this.x = sx + dx * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, Y, this.z);
                this.events.addAll(this.tracker.drainEvents());
            }
        }

        List<LapTracker.Event> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).toList();
        }
    }

    private static GateGeometry gate(double x, double z, float yaw) {
        return GateGeometry.of(x, 64.0, z, yaw, GateSize.MEDIUM);
    }

    private static CourseData.Gate pass(int index, double x, double z, boolean reverse) {
        return new CourseData.Gate(index, x, 64.0, z, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.GATE, 0, null, ScoreArea.DEFAULT, reverse);
    }

    @Test
    void passesEncodeCompactlyAndLabelEveryNumber() {
        List<CourseOrder.Pass> loop = List.of(new CourseOrder.Pass(1, false), new CourseOrder.Pass(4, true), new CourseOrder.Pass(6, false));
        String text = CourseOrder.encode(loop);
        assertEquals("1,4r,6", text);
        assertEquals(loop, CourseOrder.decode(text, 1));
        assertEquals("2 · 5← · 7", CourseOrder.label(loop));
        assertEquals("", CourseOrder.encode(List.of(new CourseOrder.Pass(3, false))), "ein Durchgang vorwärts bleibt wie bisher");
        assertEquals("3r", CourseOrder.encode(List.of(new CourseOrder.Pass(3, true))));
        assertEquals(List.of(new CourseOrder.Pass(5, false)), CourseOrder.decode("", 5));
        assertEquals(List.of(), CourseOrder.decode("", -1));
        assertEquals(List.of(new CourseOrder.Pass(2, false)), CourseOrder.decode("kaputt,4", 2), "unlesbarer Text fällt auf den ersten Durchgang zurück");
        assertTrue(CourseOrder.plain(List.of(new CourseOrder.Pass(0, false))));
        assertFalse(CourseOrder.plain(loop));
    }

    @Test
    void reversedGateIsTheSameFrameFlownTheOtherWay() {
        GateGeometry gate = gate(0.0, 0.0, 0.0F);
        GateGeometry back = gate.withReverse(true);
        assertTrue(back.reversed());
        assertEquals(gate, back.withReverse(false));
        assertNotEquals(gate, back);
        assertEquals(-gate.normalZ(), back.normalZ(), 1.0E-12);
        List<double[]> a = new ArrayList<>();
        List<double[]> b = new ArrayList<>();
        gate.boxes((x0, y0, z0, x1, y1, z1) -> a.add(new double[]{x0, y0, z0, x1, y1, z1}));
        back.boxes((x0, y0, z0, x1, y1, z1) -> b.add(new double[]{x0, y0, z0, x1, y1, z1}));
        assertEquals(a.size(), b.size());
        for (double[] box : a) {
            assertTrue(b.stream().anyMatch(other -> java.util.Arrays.equals(other, box)), "Rahmen bleibt gleich");
        }
        assertTrue(gate.crossing(0.0, Y, -1.0, 0.0, Y, 1.0, true) >= 0.0);
        assertTrue(back.crossing(0.0, Y, -1.0, 0.0, Y, 1.0, true) < 0.0);
        assertTrue(back.crossing(0.0, Y, 1.0, 0.0, Y, -1.0, true) >= 0.0);
        assertEquals(gate.crossing(0.0, Y, -0.7, 0.3, Y, 1.3, true), back.crossing(0.0, Y, -0.7, 0.3, Y, 1.3, false));
        GateGeometry flat = GatePlacement.place(GateMount.FLOOR_FLAT, 0.5, 64.0, 0.5, 0.0F, false, GateSize.MEDIUM);
        assertTrue(flat.withReverse(true).crossing(0.5, 63.0, 0.5, 0.5, 66.0, 0.5, true) >= 0.0, "liegendes Gate rückwärts: nach oben");
        assertEquals(gate.at(1.0, 64.0, 2.0).withReverse(true), back.at(1.0, 64.0, 2.0), "Verschieben behält die Richtung");
    }

    @Test
    void clickFromBehindMeansBackwards() {
        GateGeometry gate = gate(0.0, 0.0, 0.0F);
        assertEquals(1, CourseOrder.repeatSide(0.0, 0.0, 1.0, gate), "Blick in Flugrichtung");
        assertEquals(-1, CourseOrder.repeatSide(0.0, 0.0, -1.0, gate), "Blick von hinten");
        GateGeometry flat = GatePlacement.place(GateMount.FLOOR_FLAT, 0.5, 64.0, 0.5, 0.0F, false, GateSize.MEDIUM);
        assertEquals(1, CourseOrder.repeatSide(0.0, -0.9, 0.4, flat), "von oben auf ein liegendes Gate geschaut");
        assertEquals(-1, CourseOrder.repeatSide(0.0, 0.9, 0.4, flat));
        GateGeometry cube = GateGeometry.element(0.0, 64.0, 0.0, 90.0F, CourseElement.CUBE, CourseElement.EXIT_LEFT, 4.5, 4.5, GateTilt.NONE);
        assertEquals(1, CourseOrder.repeatSide(cube.exitX(), -0.5, cube.exitZ(), cube), "Würfel: Blick zum Ausgang");
        assertEquals(-1, CourseOrder.repeatSide(-cube.exitX(), -0.5, -cube.exitZ(), cube));
    }

    @Test
    void reversedElementsMirrorTheirScoring() {
        GateGeometry pole = GateGeometry.element(0.0, 64.0, 0.0, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE);
        GateGeometry turned = GateGeometry.element(0.0, 64.0, 0.0, 180.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE);
        GateGeometry back = pole.withReverse(true);
        Passage own = back.stages().getFirst();
        Passage expected = turned.stages().getFirst();
        for (double side = 0.5; side < 4.0; side += 1.0) {
            double sx = expected.ox() + expected.sx() * side;
            double sz = expected.oz() + expected.sz() * side;
            double t = own.crossing(sx - expected.nx(), Y, sz - expected.nz(), sx + expected.nx(), Y, sz + expected.nz(), true);
            assertTrue(t >= 0.0, "Rückwärts gleich wie die um 180° gedrehte Stange bei " + side);
        }
        GateGeometry cube = GateGeometry.element(0.0, 64.0, 0.0, 0.0F, CourseElement.CUBE, CourseElement.EXIT_FRONT, 4.5, 4.5, GateTilt.NONE);
        GateGeometry cubeBack = cube.withReverse(true);
        assertEquals(-cube.exitX(), cubeBack.exitX(), 1.0E-12);
        assertEquals(-cube.exitZ(), cubeBack.exitZ(), 1.0E-12);
        assertEquals(cube.stageCount(), cubeBack.stageCount());
        GateGeometry flag = GateGeometry.element(0.0, 64.0, 0.0, 0.0F, CourseElement.FLAG, CourseElement.RIGHT, 5.0, 4.0, GateTilt.NONE);
        assertEquals(3, flag.withReverse(true).stages().size());
        assertEquals(3, flag.withReverse(true).waypoints().size());
        FreeformRegion region = FreeformRegion.single(FreeformRegion.AXIS_Z);
        GateGeometry freeform = GateGeometry.freeform(0.0, 64.0, 0.0, region, 0);
        assertEquals(-freeform.stages().getFirst().nz(), freeform.withReverse(true).stages().getFirst().nz(), 1.0E-12);
    }

    @Test
    void courseDataCarriesTheDirectionWithoutChangingOldLayouts() {
        CourseData plain = new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, 0.0, false), pass(1, 0.0, 12.0, false)));
        CourseData loop = new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, 0.0, false), pass(1, 0.0, 12.0, false),
                pass(2, 0.0, 0.0, true)));
        assertEquals(RaceFormatMigrationTest.legacyHash(plain), plain.layoutHash(), "ohne Rückwärts-Durchgang gleiche Prüfsumme wie bisher");
        CourseData forwardAgain = new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, 0.0, false), pass(1, 0.0, 12.0, false),
                pass(2, 0.0, 0.0, false)));
        assertNotEquals(loop.layoutHash(), forwardAgain.layoutHash(), "Richtung gehört zum Layout");
        assertEquals(0, loop.firstPass(2));
        assertEquals(1, loop.firstPass(1));
        assertTrue(loop.geometry().get(2).reversed());
        assertFalse(loop.geometry().get(0).reversed());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, loop);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(loop, decoded);
        assertEquals(loop.geometry(), decoded.geometry());
        assertEquals(loop.layoutHash(), decoded.layoutHash());
    }

    @Test
    void loopThroughOneGateBothWaysCountsEveryPass() {
        CourseData loop = new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, 0.0, false), pass(1, 0.0, 12.0, false),
                pass(2, 0.0, 0.0, true)));
        Flight flight = new Flight(loop.geometry(), 0.0, -3.0);
        flight.to(0.0, 16.0);
        flight.to(6.0, 16.0);
        flight.to(6.0, 4.0);
        flight.to(0.0, 4.0);
        flight.to(0.0, -4.0);
        flight.to(-6.0, -4.0);
        flight.to(-6.0, -6.0);
        flight.to(0.0, -6.0);
        flight.to(0.0, 2.0);
        assertEquals(1, flight.of(LapTracker.EventType.START).size(), flight.events.toString());
        assertEquals(List.of(1, 2), flight.of(LapTracker.EventType.GATE).stream().map(LapTracker.Event::gate).toList(), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.WRONG_WAY).isEmpty(), flight.events.toString());
    }

    @Test
    void wrongDirectionOnARepeatedGateIsOneWrongWay() {
        CourseData loop = new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, 0.0, false), pass(1, 0.0, 12.0, false),
                pass(2, 0.0, 0.0, true)));
        Flight flight = new Flight(loop.geometry(), 0.0, -3.0);
        flight.to(0.0, 16.0);
        flight.to(6.0, 16.0);
        flight.to(6.0, -4.0);
        flight.to(0.0, -4.0);
        flight.to(0.0, 3.0);
        assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE).stream().map(LapTracker.Event::gate).toList(), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_WAY).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), "falsche Richtung ist kein Auslassen: " + flight.events);
        assertTrue(flight.of(LapTracker.EventType.LAP).isEmpty());
    }

    @Test
    void sameGateTwiceInTheSameDirectionNeedsTwoCrossings() {
        List<GateGeometry> gates = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), gate(0.0, 12.0, 0.0F), gate(10.0, 6.0, 180.0F));
        Flight flight = new Flight(gates, 0.0, -3.0);
        flight.to(0.0, 16.0);
        assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE).stream().map(LapTracker.Event::gate).toList(), "ein Durchflug zählt nur einmal");
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        flight.to(-6.0, 16.0);
        flight.to(-6.0, 8.0);
        flight.to(0.0, 8.0);
        flight.to(0.0, 16.0);
        flight.to(10.0, 16.0);
        flight.to(10.0, -4.0);
        flight.to(0.0, -4.0);
        flight.to(0.0, 2.0);
        assertEquals(List.of(1, 2, 3), flight.of(LapTracker.EventType.GATE).stream().map(LapTracker.Event::gate).toList(), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
    }

    @Test
    void startGateUsedAgainStartsOnlyOnce() {
        List<GateGeometry> gates = List.of(gate(0.0, 0.0, 0.0F), gate(0.0, 12.0, 0.0F), gate(0.0, 0.0, 0.0F));
        Flight flight = new Flight(gates, 0.0, -3.0);
        flight.to(0.0, 3.0);
        assertEquals(1, flight.of(LapTracker.EventType.START).size());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        assertEquals(1, flight.tracker.nextGate());
    }

    @Test
    void serverVerifierNeedsSeparateCrossingsForRepeatedPasses() {
        LapVerifier verifier = new LapVerifier();
        long s = LapTracker.SECOND;
        verifier.record(COURSE, 2, 10 * s, 100L);
        verifier.record(COURSE, 0, 10 * s, 100L);
        verifier.record(COURSE, 1, 15 * s, 101L);
        verifier.record(COURSE, 2, 20 * s, 102L);
        verifier.record(COURSE, 0, 20 * s, 102L);
        assertNull(verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE), "Durchgang 3 und Rundenende im selben Durchflug zählen nicht");
        verifier.record(COURSE, 2, 25 * s, 103L);
        verifier.record(COURSE, 0, 25 * s, 103L);
        LapVerifier.VerifiedLap lap = verifier.find(COURSE, 3, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(lap);
        assertEquals(15 * s, lap.nanos());
    }

    @Test
    void courseFileWritesTheDirectionOnlyWhenSet() {
        String json = """
                {"format": 2, "nextCourseId": 2, "courses": [{"id": 1, "dimension": "minecraft:overworld", "owner": "", "ownerName": "",
                  "layout": 1, "worldCourse": "", "site": "", "gates": [
                    {"uuid": "00000000-0000-0000-0000-000000000031", "index": 0, "x": 0.5, "y": 64.0, "z": 0.5, "yaw": 0.0, "size": "medium"},
                    {"uuid": "00000000-0000-0000-0000-000000000032", "index": 1, "x": 0.5, "y": 64.0, "z": 12.5, "yaw": 0.0, "size": "medium"},
                    {"uuid": "00000000-0000-0000-0000-000000000031", "index": 2, "x": 0.5, "y": 64.0, "z": 0.5, "yaw": 0.0, "size": "medium",
                     "reverse": true}],
                  "leaderboard": [], "archive": []}]}
                """;
        CourseRegistry.FileData data = CourseRegistry.read(new StringReader(json));
        assertNotNull(data);
        CourseRegistry.Course course = data.courses.getFirst();
        assertEquals(3, course.gates.size(), "dieselbe UUID darf mehrmals vorkommen");
        assertTrue(course.gates.get(2).reversed());
        assertFalse(course.gates.get(0).reversed());
        CourseData loaded = course.toData();
        assertTrue(loaded.gates().get(2).reverse());
        assertEquals(3, course.checkpoints());
        String written = CourseRegistry.write(data);
        assertEquals(1, written.split("\"reverse\"", -1).length - 1, written);
        CourseRegistry.FileData again = CourseRegistry.read(new StringReader(written));
        assertNotNull(again);
        assertEquals(loaded, again.courses.getFirst().toData());
    }
}
