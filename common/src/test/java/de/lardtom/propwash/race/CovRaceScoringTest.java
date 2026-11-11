package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovRaceScoringTest {
    private static final double Y = 64.0;
    private static final long MS = 1_000_000L;
    private static final int COURSE = 11;
    private static final double MARGIN = 0.1;

    @TempDir
    Path temp;

    private static final class Flight {
        final LapTracker tracker = new LapTracker();
        final List<LapTracker.Event> events = new ArrayList<>();
        final List<String> hud = new ArrayList<>();
        long now = 1_000L * MS;
        double x;
        double y;
        double z;

        Flight(List<GateGeometry> gates, double x, double y, double z) {
            this.tracker.setCourse(COURSE, gates, this.now);
            this.tracker.setLapTarget(0);
            this.x = x;
            this.y = y;
            this.z = z;
            this.tracker.update(this.now, x, y, z);
        }

        void to(double tx, double ty, double tz) {
            double dx = tx - this.x;
            double dy = ty - this.y;
            double dz = tz - this.z;
            int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.3));
            double sx = this.x;
            double sy = this.y;
            double sz = this.z;
            for (int i = 1; i <= steps; i++) {
                this.now += 20 * MS;
                this.x = sx + dx * i / steps;
                this.y = sy + dy * i / steps;
                this.z = sz + dz * i / steps;
                this.tracker.update(this.now, this.x, this.y, this.z);
                for (LapTracker.Event event : this.tracker.drainEvents()) {
                    this.events.add(event);
                    if (event.type() == LapTracker.EventType.START || event.type() == LapTracker.EventType.GATE
                            || event.type() == LapTracker.EventType.LAP) {
                        this.hud.add((this.tracker.nextGate() + 1) + "/" + this.tracker.gateCount());
                    }
                }
            }
        }

        void to(double tx, double tz) {
            this.to(tx, this.y, tz);
        }

        List<Integer> of(LapTracker.EventType type) {
            return this.events.stream().filter(e -> e.type() == type).map(LapTracker.Event::gate).toList();
        }
    }

    private static GateGeometry start() {
        return GateGeometry.of(0.0, Y, 0.0, 0.0F, GateSize.MEDIUM);
    }

    private static Flight around(GateGeometry start, GateGeometry element, double height) {
        double gate = start.centerY();
        Flight flight = new Flight(List.of(start, element), 0.0, gate, -3.0);
        flight.to(0.0, gate, 3.0);
        GateGeometry.Waypoint last = null;
        for (GateGeometry.Waypoint point : element.waypoints()) {
            flight.to(point.x() - point.dx() * 2.0, height, point.z() - point.dz() * 2.0);
            flight.to(point.x() + point.dx() * 2.0, height, point.z() + point.dz() * 2.0);
            last = point;
        }
        assertNotNull(last);
        double far = Math.signum(flight.x == 0.0 ? 1.0 : flight.x) * 8.0;
        flight.to(far, gate, flight.z);
        flight.to(far, gate, -4.0);
        flight.to(0.0, gate, -4.0);
        flight.to(0.0, gate, 3.0);
        return flight;
    }

    @Test
    void tallRectangularFlagScoresExactlyWithinTheSetHeight() {
        double height = 7.0;
        double below = 2.5;
        double above = 1.5;
        GateGeometry flag = GateGeometry.element(0.0, Y, 14.0, 0.0F, CourseElement.FLAG, CourseElement.LEFT, 3.0, height, GateTilt.NONE,
                GateMount.STANDING, ScoreArea.DEFAULT.withBelow(below).withAbove(above));
        double low = Y - below;
        double high = Y + height + above;
        for (Passage stage : flag.stages()) {
            assertEquals(-below, stage.up0());
            assertEquals(height + above, stage.up1());
        }
        for (double y : new double[]{low + MARGIN, Y + 0.5, Y + height, high - MARGIN}) {
            Flight flight = around(start(), flag, y);
            assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE), "Durchflug bei " + (y - Y) + " über dem Fuß: " + flight.events);
            assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), "Runde bei " + (y - Y) + ": " + flight.events);
        }
        for (double y : new double[]{low - MARGIN, high + MARGIN}) {
            Flight flight = around(start(), flag, y);
            assertTrue(flight.of(LapTracker.EventType.GATE).isEmpty(), "knapp außerhalb bei " + (y - Y) + " zählt: " + flight.events);
            assertTrue(flight.of(LapTracker.EventType.LAP).isEmpty(), "Runde ohne Flagge bei " + (y - Y));
        }
        GateGeometry plain = flag.withScore(ScoreArea.DEFAULT);
        Flight standard = around(start(), plain, Y - 2.0);
        assertTrue(standard.of(LapTracker.EventType.GATE).isEmpty(), "ohne Einstellung reicht die Wertung nur 1 Block nach unten");
    }

    @Test
    void slalomPoleBelowSettingIsTheSameHalfPlaneOnTheWrongSide() {
        GateGeometry pole = GateGeometry.element(0.0, Y, 12.0, 0.0F, CourseElement.POLE, CourseElement.RIGHT, 4.0, 5.0, GateTilt.NONE,
                GateMount.STANDING, ScoreArea.DEFAULT.withBelow(4.0));
        Passage pass = pole.stages().getFirst();
        Passage foul = pole.fouls(0).getFirst();
        assertEquals(pass.up0(), foul.up0());
        assertEquals(pass.up1(), foul.up1());
        for (double y : new double[]{Y - 4.0 + MARGIN, Y + 5.0 - MARGIN}) {
            Flight flight = around(start(), pole, y);
            assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE), "Slalomstange bei " + (y - Y) + ": " + flight.events);
        }
        Flight under = around(start(), pole, Y - 4.0 - MARGIN);
        assertTrue(under.of(LapTracker.EventType.GATE).isEmpty(), under.events.toString());
    }

    @Test
    void gatePoleInACourseCountsThroughItsAreaAndNotBesideIt() {
        GateGeometry decor = GateGeometry.element(0.0, Y, 12.0, 0.0F, CourseElement.GATE_POLE, 3, GatePoles.THICKNESS, 4.0, GateTilt.NONE,
                GateMount.STANDING, ScoreArea.DEFAULT);
        GateGeometry pole = decor.withScore(ScoreArea.DEFAULT.forCourse(CourseElement.GATE_POLE).withReach(3.0));
        assertEquals(ScoreArea.Role.PASS, pole.score().role(), "im Kurs wertet die Gate-Stange immer");
        Passage pass = pole.stages().getFirst();
        double gate = start().centerY();
        for (double side : new double[]{0.4, 1.5, 3.0 - MARGIN, 3.0 + MARGIN, 4.5}) {
            double px = pass.ox() + pass.sx() * side;
            double pz = pass.oz() + pass.sz() * side;
            Flight flight = new Flight(List.of(start(), pole), 0.0, gate, -3.0);
            flight.to(0.0, gate, 3.0);
            flight.to(px - pass.nx() * 3.0, Y + 1.5, pz - pass.nz() * 3.0);
            flight.to(px + pass.nx() * 3.0, Y + 1.5, pz + pass.nz() * 3.0);
            double far = Math.signum(px) * 9.0;
            flight.to(far, gate, flight.z);
            flight.to(far, gate, -4.0);
            flight.to(0.0, gate, -4.0);
            flight.to(0.0, gate, 3.0);
            boolean inside = side <= 3.0;
            assertEquals(inside ? List.of(1) : List.of(), flight.of(LapTracker.EventType.GATE), "Gate-Stange " + side + " Blöcke daneben: "
                    + flight.events);
            assertEquals(inside ? 1 : 0, flight.of(LapTracker.EventType.LAP).size(), "Runde " + side + " daneben");
        }
    }

    private static CourseData.Gate pass(int index, double z, boolean reverse) {
        return new CourseData.Gate(index, 0.0, Y, z, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.GATE, 0, null, ScoreArea.DEFAULT, reverse);
    }

    private static CourseData loop() {
        return new CourseData(COURSE, "minecraft:overworld", List.of(pass(0, 0.0, false), pass(1, 12.0, false), pass(2, 12.0, true),
                pass(3, 0.0, true)));
    }

    @Test
    void bothGatesTwiceInBothDirectionsAreUnambiguous() {
        CourseData course = loop();
        assertEquals("1 · 4←", CourseOrder.label(List.of(new CourseOrder.Pass(0, false), new CourseOrder.Pass(3, true))));
        assertEquals("2 · 3←", CourseOrder.label(List.of(new CourseOrder.Pass(1, false), new CourseOrder.Pass(2, true))));
        assertEquals(1, course.firstPass(2));
        assertEquals(0, course.firstPass(3));
        double y = Y + 2.0;
        Flight flight = new Flight(course.geometry(), 0.0, y, -3.0);
        flight.to(0.0, 16.0);
        flight.to(0.0, -4.0);
        flight.to(-6.0, -4.0);
        flight.to(-6.0, -6.0);
        flight.to(0.0, -6.0);
        flight.to(0.0, 2.0);
        assertEquals(1, flight.of(LapTracker.EventType.START).size(), flight.events.toString());
        assertEquals(List.of(1, 2, 3), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.LAP).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.WRONG_WAY).isEmpty(), flight.events.toString());
        assertEquals(List.of("2/4", "3/4", "4/4", "1/4", "2/4"), flight.hud, "HUD-Zähler je Durchgang");
        Set<String> shown = new HashSet<>(flight.hud.subList(0, 4));
        assertEquals(4, shown.size(), "jeder Durchgang hat eine eigene Anzeige");
    }

    @Test
    void wrongDirectionOnTheSecondPassOfAGateDoesNotCount() {
        CourseData course = loop();
        double y = Y + 2.0;
        Flight flight = new Flight(course.geometry(), 0.0, y, -3.0);
        flight.to(0.0, 16.0);
        flight.to(6.0, 16.0);
        flight.to(6.0, 4.0);
        flight.to(0.0, 4.0);
        flight.to(0.0, 16.0);
        assertEquals(List.of(1), flight.of(LapTracker.EventType.GATE), flight.events.toString());
        assertEquals(1, flight.of(LapTracker.EventType.WRONG_WAY).size(), flight.events.toString());
        assertTrue(flight.of(LapTracker.EventType.MISSED).isEmpty(), flight.events.toString());
        assertEquals(2, flight.tracker.nextGate(), "erwartet bleibt B rückwärts");
        flight.to(0.0, -4.0);
        assertEquals(List.of(1, 2, 3), flight.of(LapTracker.EventType.GATE), "danach rückwärts durch B und A: " + flight.events);
    }

    @Test
    void serverVerifierFindsTheLoopWithOneCrossingPerPass() {
        LapVerifier verifier = new LapVerifier();
        long s = LapTracker.SECOND;
        verifier.record(COURSE, 0, 10 * s, 200L);
        verifier.record(COURSE, 1, 12 * s, 201L);
        verifier.record(COURSE, 2, 14 * s, 202L);
        verifier.record(COURSE, 3, 16 * s, 203L);
        assertEquals(null, verifier.find(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE), "ohne zweiten Start keine Runde");
        verifier.record(COURSE, 0, 19 * s, 204L);
        LapVerifier.VerifiedLap lap = verifier.find(COURSE, 4, LapVerifier.NO_SEQUENCE, Long.MIN_VALUE);
        assertNotNull(lap);
        assertEquals(9 * s, lap.nanos());
    }

    @Test
    void leaderboardOfTheLoopHoldsTheFlownTime() throws IOException {
        String json = """
                {"format": 2, "nextCourseId": 12, "courses": [{"id": 11, "dimension": "minecraft:overworld", "owner": "", "ownerName": "",
                  "layout": 1, "worldCourse": "", "site": "", "gates": [
                    {"uuid": "00000000-0000-0000-0000-0000000000a1", "index": 0, "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "size": "medium"},
                    {"uuid": "00000000-0000-0000-0000-0000000000b2", "index": 1, "x": 0.0, "y": 64.0, "z": 12.0, "yaw": 0.0, "size": "medium"},
                    {"uuid": "00000000-0000-0000-0000-0000000000b2", "index": 2, "x": 0.0, "y": 64.0, "z": 12.0, "yaw": 0.0, "size": "medium",
                     "reverse": true},
                    {"uuid": "00000000-0000-0000-0000-0000000000a1", "index": 3, "x": 0.0, "y": 64.0, "z": 0.0, "yaw": 0.0, "size": "medium",
                     "reverse": true}],
                  "leaderboard": [], "archive": []}]}
                """;
        Path file = this.temp.resolve("propwash_race.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        RaceRecords records = new RaceRecords();
        CourseRegistry registry = CourseRegistry.offline(file, records, new RecordGhosts());
        CourseRegistry.Course course = registry.course(COURSE);
        assertNotNull(course);
        assertEquals(4, course.checkpoints());
        assertEquals(loop().layoutHash(), course.toData().layoutHash(), "dieselbe Schleife aus Datei und Netz");
        Flight flight = new Flight(course.toData().geometry(), 0.0, Y + 2.0, -3.0);
        flight.to(0.0, 16.0);
        flight.to(0.0, -4.0);
        flight.to(-6.0, -4.0);
        flight.to(-6.0, -6.0);
        flight.to(0.0, -6.0);
        flight.to(0.0, 2.0);
        LapTracker.Event lap = flight.events.stream().filter(e -> e.type() == LapTracker.EventType.LAP).findFirst().orElseThrow();
        UUID pilot = UUID.fromString("00000000-0000-4000-8000-00000000c0f1");
        registry.submitLap(course, pilot, "CovPilot", "race_5", lap.valueNanos(), 1_792_300_000_000L);
        List<Leaderboard.Entry> entries = registry.board(course).entries();
        assertEquals(1, entries.size());
        assertEquals(lap.valueNanos(), entries.getFirst().nanos(), "Bestenliste hat die geflogene Zeit");
        registry.save();
        CourseRegistry.FileData again = CourseRegistry.read(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
        assertNotNull(again);
        List<Boolean> reversed = again.courses.getFirst().gates.stream().map(CourseRegistry.GateRecord::reversed).toList();
        assertEquals(List.of(false, false, true, true), reversed, "Richtungen nach dem Speichern");
        assertEquals(course.toData().layoutHash(), again.courses.getFirst().toData().layoutHash());
    }
}
