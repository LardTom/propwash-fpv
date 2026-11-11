package de.lardtom.propwash.race;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreAreaTest {
    private static final long MS = 1_000_000L;
    private static final double GROUND = 64.0;
    private static final double FLY = 65.5;

    private static GateGeometry pole(double x, double z, int side, ScoreArea score) {
        return GateGeometry.element(x, GROUND, z, 0.0F, CourseElement.POLE, side, 4.0, 3.0, GateTilt.NONE, GateMount.STANDING, score);
    }

    private static GateGeometry flag(double x, double z, int turn, ScoreArea score) {
        return GateGeometry.element(x, GROUND, z, 0.0F, CourseElement.FLAG, turn, 5.0, 4.0, GateTilt.NONE, GateMount.STANDING, score);
    }

    private static GateGeometry gatePole(double x, double y, double z, GateMount mount, ScoreArea score) {
        return GateGeometry.element(x, y, z, 0.0F, CourseElement.GATE_POLE, 4, CourseElement.GATE_POLE.outerWidth(0.0), 4.0, GateTilt.NONE, mount,
                score);
    }

    @Test
    void packingKeepsEveryFieldOnTheHalfBlockGrid() {
        ScoreArea area = new ScoreArea(ScoreArea.Role.TURN, CourseElement.RIGHT, 7.5, 12.5, 3.0, true);
        assertEquals(area, ScoreArea.unpack(area.pack()));
        assertEquals(ScoreArea.DEFAULT, ScoreArea.unpack(ScoreArea.DEFAULT.pack()));
        ScoreArea extreme = new ScoreArea(ScoreArea.Role.PASS, CourseElement.LEFT, 99.0, 99.0, -4.0, false);
        assertEquals(ScoreArea.MAX_REACH, extreme.reach());
        assertEquals(ScoreArea.MAX_EXTENT, extreme.below());
        assertEquals(0.0, extreme.above());
        assertEquals(extreme, ScoreArea.unpack(extreme.pack()));
        assertEquals(1.0, new ScoreArea(ScoreArea.Role.NONE, 0, 4.0, 1.2, 0.0, false).below(), "auf halbe Blöcke gerastet");
        assertEquals(ScoreArea.DEFAULT_BELOW, new ScoreArea(ScoreArea.Role.NONE, 0, 4.0, Double.NaN, 0.0, false).below());
    }

    @Test
    void normalizingKeepsOnlyTheFieldsOfTheKind() {
        ScoreArea all = new ScoreArea(ScoreArea.Role.TURN, CourseElement.RIGHT, 6.0, 3.0, 2.0, true);
        assertEquals(new ScoreArea(ScoreArea.Role.NONE, CourseElement.LEFT, ScoreArea.DEFAULT_REACH, 3.0, 2.0, false), all.normalized(CourseElement.FLAG));
        assertEquals(ScoreArea.DEFAULT, all.normalized(CourseElement.GATE));
        assertEquals(ScoreArea.DEFAULT, all.normalized(CourseElement.CUBE));
        assertEquals(ScoreArea.DEFAULT.withOpen(true), all.normalized(CourseElement.FREEFORM));
        assertEquals(all.withOpen(false), all.normalized(CourseElement.GATE_POLE));
        assertEquals(ScoreArea.Role.PASS, ScoreArea.DEFAULT.forCourse(CourseElement.GATE_POLE).role(), "eine Stange im Kurs wertet immer");
        assertTrue(ScoreArea.DEFAULT.forCourse(CourseElement.POLE).isDefault());
        assertFalse(ScoreArea.DEFAULT.withOpen(true).forCourse(CourseElement.FREEFORM).open(), "Lücken ignorieren gehört nicht in die Kursdaten");
    }

    @Test
    void defaultScoringIsBitIdenticalToTheOldHalfPlanes() {
        GateGeometry old = GateGeometry.element(0.0, GROUND, 10.0, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0, GateTilt.NONE);
        GateGeometry scored = pole(0.0, 10.0, CourseElement.LEFT, ScoreArea.DEFAULT);
        assertEquals(old, scored);
        assertEquals(old.hashCode(), scored.hashCode());
        Passage pass = scored.stages().getFirst();
        assertEquals(-CourseElement.BELOW, pass.up0());
        assertEquals(3.0, pass.up1());
        assertEquals(4.0, pass.side1());
    }

    @Test
    void scoringReachesDownAndUpWhereItIsSet() {
        GateGeometry plain = pole(0.0, 10.0, CourseElement.LEFT, ScoreArea.DEFAULT);
        GateGeometry tall = pole(0.0, 10.0, CourseElement.LEFT, ScoreArea.DEFAULT.withBelow(8.0).withAbove(2.0));
        Passage low = tall.stages().getFirst();
        assertTrue(plain.stages().getFirst().crossing(1.5, GROUND - 4.0, 9.0, 1.5, GROUND - 4.0, 11.0, true) < 0.0);
        assertTrue(low.crossing(1.5, GROUND - 4.0, 9.0, 1.5, GROUND - 4.0, 11.0, true) >= 0.0, "4 Blöcke unter dem Fuß zählt mit 8 nach unten");
        assertTrue(low.crossing(1.5, GROUND - 9.0, 9.0, 1.5, GROUND - 9.0, 11.0, true) < 0.0, "9 Blöcke darunter nicht mehr");
        assertTrue(low.crossing(1.5, GROUND + 4.5, 9.0, 1.5, GROUND + 4.5, 11.0, true) >= 0.0, "über der Spitze mit 2 nach oben");
        assertTrue(low.crossing(1.5, GROUND + 5.5, 9.0, 1.5, GROUND + 5.5, 11.0, true) < 0.0);
        assertTrue(tall.fouls(0).getFirst().crossing(-1.5, GROUND - 4.0, 9.0, -1.5, GROUND - 4.0, 11.0, true) >= 0.0,
                "die falsche Seite reicht genauso weit");
        assertNotEquals(plain, tall);
        assertFalse(plain.sameAs(tall));
        assertTrue(tall.sameAs(tall.at(0.0, GROUND, 10.0)));
        GateGeometry flag = flag(0.0, 20.0, CourseElement.LEFT, ScoreArea.DEFAULT.withBelow(6.0));
        for (Passage stage : flag.stages()) {
            assertEquals(-6.0, stage.up0());
        }
    }

    @Test
    void tallFlagOnAPillarCountsAllTheWayDownInARace() {
        double pillar = 6.0;
        GateGeometry start = GateGeometry.of(0.0, GROUND, 0.0, 0.0F, GateSize.MEDIUM);
        GateGeometry high = GateGeometry.element(0.0, GROUND + pillar, 10.0, 0.0F, CourseElement.FLAG, CourseElement.LEFT, 5.0, 4.0, GateTilt.NONE,
                GateMount.STANDING, ScoreArea.DEFAULT.withBelow(pillar));
        GateGeometry plain = high.withScore(ScoreArea.DEFAULT);
        assertEquals(1, fly(List.of(start, high)), "mit Wertung nach unten zählt die Wende in Bodennähe");
        assertEquals(0, fly(List.of(start, plain)), "ohne Wertung nach unten zählt sie dort nicht");
    }

    private static int fly(List<GateGeometry> course) {
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(5, course, 0L);
        List<LapTracker.Event> events = new ArrayList<>();
        long now = 1_000L * MS;
        double[][] path = {{0.0, FLY, -2.0}, {0.0, FLY, 2.0}, {-2.0, FLY, 8.0}, {-2.0, FLY, 10.0}, {-1.4, FLY, 11.4}, {0.0, FLY, 12.0},
                {1.4, FLY, 11.4}, {2.0, FLY, 10.0}, {2.0, FLY, 6.0}};
        double x = path[0][0];
        double y = path[0][1];
        double z = path[0][2];
        tracker.update(now, x, y, z);
        for (int leg = 1; leg < path.length; leg++) {
            for (int i = 1; i <= 10; i++) {
                now += 20 * MS;
                tracker.update(now, x + (path[leg][0] - x) * i / 10.0, y + (path[leg][1] - y) * i / 10.0, z + (path[leg][2] - z) * i / 10.0);
                events.addAll(tracker.drainEvents());
            }
            x = path[leg][0];
            y = path[leg][1];
            z = path[leg][2];
        }
        return (int) events.stream().filter(e -> e.type() == LapTracker.EventType.GATE && e.gate() == 1).count();
    }

    @Test
    void gatePoleScoresLikeASlalomPoleOrAFlag() {
        GateGeometry decor = gatePole(0.0, GROUND, 10.0, GateMount.STANDING, ScoreArea.DEFAULT);
        assertTrue(decor.stages().isEmpty());
        assertEquals(CourseElement.GATE_POLE, decor.scoringKind());
        GateGeometry pass = gatePole(0.0, GROUND, 10.0, GateMount.STANDING, ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS).withReach(2.0));
        assertEquals(CourseElement.POLE, pass.scoringKind());
        assertEquals(1, pass.stageCount());
        assertEquals(2.0, pass.stages().getFirst().side1());
        assertTrue(pass.stages().getFirst().crossing(1.5, FLY, 9.0, 1.5, FLY, 11.0, true) >= 0.0, "links vorbei");
        assertTrue(pass.stages().getFirst().crossing(2.5, FLY, 9.0, 2.5, FLY, 11.0, true) < 0.0, "außerhalb der Reichweite");
        GateGeometry right = pass.withScore(pass.score().withSide(CourseElement.RIGHT));
        assertTrue(right.stages().getFirst().crossing(-1.5, FLY, 9.0, -1.5, FLY, 11.0, true) >= 0.0, "rechts vorbei");
        assertEquals(4, right.variant(), "die Farbe bleibt");
        GateGeometry turn = gatePole(0.0, GROUND, 10.0, GateMount.STANDING, ScoreArea.DEFAULT.withRole(ScoreArea.Role.TURN));
        assertEquals(CourseElement.FLAG, turn.scoringKind());
        assertEquals(3, turn.stages().size());
        assertEquals(3, turn.waypoints().size());
        GateGeometry wall = gatePole(0.0, GROUND + 2.0, 10.0, GateMount.WALL_DIVE, ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS));
        assertTrue(wall.stages().isEmpty(), "Wandstangen werten nicht");
        GateGeometry hanging = gatePole(0.0, GROUND + 2.0, 10.0, GateMount.HANGING, ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS));
        assertEquals(1, hanging.stages().size(), "hängende Stangen werten");
    }

    @Test
    void gatePolesCountAsCheckpointsLikeTheirRole() {
        GateGeometry start = GateGeometry.of(0.0, GROUND, 0.0, 0.0F, GateSize.MEDIUM);
        GateGeometry pass = gatePole(0.0, GROUND, 8.0, GateMount.STANDING, ScoreArea.DEFAULT.withRole(ScoreArea.Role.PASS));
        GateGeometry turn = gatePole(0.0, GROUND, 16.0, GateMount.STANDING, ScoreArea.DEFAULT.withRole(ScoreArea.Role.TURN));
        List<CourseElement> kinds = CourseCheckpoints.kinds(List.of(start, pass, turn));
        assertEquals(List.of(CourseElement.GATE, CourseElement.POLE, CourseElement.FLAG), kinds);
        assertEquals(5, CourseCheckpoints.count(kinds));
        LapTracker tracker = new LapTracker();
        tracker.setLapTarget(0);
        tracker.setCourse(5, List.of(start, pass), 0L);
        long now = 1_000L * MS;
        tracker.update(now, 0.0, FLY, -2.0);
        List<LapTracker.Event> events = new ArrayList<>();
        double[][] path = {{0.0, FLY, 2.0}, {1.5, FLY, 8.0}, {1.5, FLY, 11.0}};
        double[] at = {0.0, FLY, -2.0};
        for (double[] target : path) {
            for (int i = 1; i <= 10; i++) {
                now += 20 * MS;
                tracker.update(now, at[0] + (target[0] - at[0]) * i / 10.0, at[1] + (target[1] - at[1]) * i / 10.0,
                        at[2] + (target[2] - at[2]) * i / 10.0);
                events.addAll(tracker.drainEvents());
            }
            at = target;
        }
        assertEquals(List.of(1), events.stream().filter(e -> e.type() == LapTracker.EventType.GATE).map(LapTracker.Event::gate).toList(),
                events.toString());
    }

    @Test
    void courseDataCarriesTheScoringOnlyWhenItDiffers() {
        CourseData.Gate plain = new CourseData.Gate(1, 0.0, GROUND, 8.0, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.POLE, CourseElement.LEFT);
        CourseData before = new CourseData(4, "minecraft:overworld", List.of(plain));
        CourseData.Gate tall = new CourseData.Gate(1, 0.0, GROUND, 8.0, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0,
                CourseElement.POLE, CourseElement.LEFT, null, ScoreArea.DEFAULT.withBelow(5.0));
        CourseData after = before.withGates(List.of(tall));
        assertNotEquals(before.layoutHash(), after.layoutHash());
        assertEquals(before.layoutHash(), before.withGates(List.of(new CourseData.Gate(1, 0.0, GROUND, 8.0, 0.0F, GateSize.MEDIUM,
                GateMount.STANDING, false, GateTilt.NONE, 0.0, 0.0, CourseElement.POLE, CourseElement.LEFT, null, ScoreArea.DEFAULT))).layoutHash());
        CourseData.Gate hanging = new CourseData.Gate(2, 0.0, GROUND + 3.0, 16.0, 0.0F, GateSize.MEDIUM, GateMount.HANGING, false, GateTilt.NONE,
                0.0, 0.0, CourseElement.GATE_POLE, 7, null, ScoreArea.DEFAULT.withRole(ScoreArea.Role.TURN));
        assertEquals(GateMount.HANGING, hanging.mount(), "Gate-Stangen behalten ihre Lage im Kurs");
        assertEquals(CourseElement.FLAG, hanging.scoringKind());
        assertEquals(3, hanging.geometry().stages().size());
        CourseData.Gate decor = new CourseData.Gate(2, 0.0, GROUND, 16.0, 0.0F, GateSize.MEDIUM, GateMount.STANDING, false, GateTilt.NONE,
                0.0, 0.0, CourseElement.GATE_POLE, 7, null, ScoreArea.DEFAULT);
        assertEquals(ScoreArea.Role.PASS, decor.score().role(), "eine Stange in Kursdaten wertet immer");
        assertEquals(1, decor.geometry().stages().size());
    }
}
