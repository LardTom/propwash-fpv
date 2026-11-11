package de.lardtom.propwash.race;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceFormatMigrationTest {
    private static final String V040 = """
            {"nextCourseId": 2, "courses": [{"id": 1, "dimension": "minecraft:overworld", "owner": "", "ownerName": "", "layout": 1,
              "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000011", "index": 0, "x": -30.5, "y": -60.0, "z": 16.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000012", "index": 1, "x": -30.5, "y": -56.5, "z": 16.5, "yaw": 180.0, "size": "small",
                 "mount": "standing", "flipped": false, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000013", "index": 2, "x": 2.5, "y": -60.0, "z": 30.5, "yaw": 90.0, "size": "large",
                 "slot": -1}],
              "leaderboard": [], "archive": []}]}
            """;
    private static final String V050 = """
            {"nextCourseId": 3, "courses": [{"id": 2, "dimension": "minecraft:overworld", "owner": "", "ownerName": "", "name": "Elemente",
              "laps": 3, "layout": 2, "worldCourse": "", "site": "", "gates": [
                {"uuid": "00000000-0000-0000-0000-000000000021", "index": 0, "x": -42.5, "y": -60.0, "z": -37.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 45.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1},
                {"uuid": "00000000-0000-0000-0000-000000000022", "index": 1, "x": -42.5, "y": -60.0, "z": -29.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1,
                 "kind": "pole", "variant": 0},
                {"uuid": "00000000-0000-0000-0000-000000000023", "index": 2, "x": -42.5, "y": -60.0, "z": -19.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 6.0, "height": 5.0, "slot": -1,
                 "kind": "flag", "variant": 1},
                {"uuid": "00000000-0000-0000-0000-000000000024", "index": 3, "x": -30.0, "y": -59.5, "z": -10.0, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 0.0, "height": 0.0, "slot": -1,
                 "kind": "freeform", "variant": 0, "region": "f1:z:3x2:1,0:Pw=="},
                {"uuid": "00000000-0000-0000-0000-000000000025", "index": 4, "x": -20.5, "y": -60.0, "z": -10.5, "yaw": 0.0, "size": "medium",
                 "mount": "standing", "flipped": false, "turn": 0.0, "pitch": 0.0, "roll": 0.0, "width": 4.5, "height": 4.5, "slot": -1,
                 "kind": "cube", "variant": 2}],
              "leaderboard": [], "archive": []}]}
            """;

    private static CourseRegistry.FileData read(String json) {
        CourseRegistry.FileData data = CourseRegistry.read(new StringReader(json));
        assertNotNull(data);
        return data;
    }

    static int legacyHash(CourseData course) {
        int hash = 17;
        for (CourseData.Gate gate : course.gates()) {
            hash = hash * 31 + gate.index();
            hash = hash * 31 + Long.hashCode(Math.round(gate.x() * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.y() * 100.0));
            hash = hash * 31 + Long.hashCode(Math.round(gate.z() * 100.0));
            hash = hash * 31 + Math.round(gate.yaw() * 10.0F);
            hash = hash * 31 + gate.size().ordinal();
            if (gate.mount() != GateMount.STANDING) {
                hash = hash * 31 + GateMount.pack(gate.mount(), gate.flipped()) + 1;
            }
            if (!gate.tilt().none()) {
                hash = hash * 31 + Math.round(gate.tilt().yaw() * 10.0F);
                hash = hash * 31 + Math.round(gate.tilt().pitch() * 10.0F);
                hash = hash * 31 + Math.round(gate.tilt().roll() * 10.0F);
            }
            if (!gate.standardSize()) {
                hash = hash * 31 + Long.hashCode(Math.round(gate.width() * 100.0));
                hash = hash * 31 + Long.hashCode(Math.round(gate.height() * 100.0));
            }
            if (!gate.kind().gate()) {
                hash = hash * 31 + 1000 + gate.kind().ordinal() * 16 + gate.variant();
            }
            if (gate.region() != null) {
                hash = hash * 31 + gate.region().hashCode();
            }
        }
        return hash;
    }

    @Test
    void filesWithoutFormatAreFormatOneAndKeepTheirLayout() {
        for (String json : List.of(V040, V050)) {
            CourseRegistry.FileData data = read(json);
            assertEquals(CourseRegistry.LEGACY_FORMAT, data.format());
            for (CourseRegistry.Course course : data.courses) {
                CourseData course0 = course.toData();
                assertEquals(legacyHash(course0), course0.layoutHash(), "Prüfsumme wie bis 0.5.2");
                for (CourseData.Gate gate : course0.gates()) {
                    assertTrue(gate.score().isDefault(), gate.toString());
                }
                assertEquals(course.kinds(), course.scoringKinds(), "ohne Gate-Stangen sind Art und Wertung gleich");
            }
        }
        CourseRegistry.Course elements = read(V050).courses.getFirst();
        assertEquals(List.of(CourseElement.GATE, CourseElement.POLE, CourseElement.FLAG, CourseElement.FREEFORM, CourseElement.CUBE),
                elements.kinds());
        assertEquals(1 + 1 + 3 + 1 + 2, elements.checkpoints());
        CourseRegistry.Course gates = read(V040).courses.getFirst();
        assertEquals(GateMount.STANDING, gates.gates.get(2).mount(), "0.4.0 ohne Lage lädt stehend");
    }

    @Test
    void writingKeepsOldFieldsAndAddsOnlyTheFormat() {
        CourseRegistry.FileData data = read(V050);
        String written = CourseRegistry.write(data);
        assertTrue(written.contains("\"format\": 2"), written);
        assertFalse(written.contains("\"score\""), "Standardwerte schreiben keine Wertung: " + written);
        CourseRegistry.FileData again = read(written);
        assertEquals(CourseRegistry.FORMAT, again.format());
        assertEquals(data.courses.getFirst().toData(), again.courses.getFirst().toData());
    }

    @Test
    void scoredElementsAndGatePolesSurviveTheFile() {
        CourseRegistry.FileData data = read(V050);
        CourseRegistry.Course course = data.courses.getFirst();
        CourseRegistry.GateRecord flag = course.gates.get(2);
        flag.score = CourseRegistry.Score.of(ScoreArea.DEFAULT.withBelow(6.0).withAbove(1.5), CourseElement.FLAG);
        CourseRegistry.GateRecord pole = new CourseRegistry.GateRecord();
        pole.uuid = "00000000-0000-0000-0000-000000000026";
        pole.index = 5;
        pole.x = -10.5;
        pole.y = -57.0;
        pole.z = -5.5;
        pole.kind = CourseElement.GATE_POLE.key();
        pole.variant = 11;
        pole.mount = GateMount.HANGING.key();
        pole.height = 3.0;
        pole.score = CourseRegistry.Score.of(new ScoreArea(ScoreArea.Role.TURN, CourseElement.RIGHT, 6.0, 2.0, 0.0, true), CourseElement.GATE_POLE);
        course.gates.add(pole);
        String written = CourseRegistry.write(data);
        assertTrue(written.contains("\"below\": 6.0"), written);
        assertFalse(written.contains("\"open\""), "Lücken ignorieren gehört nicht in die Kursdatei");
        CourseRegistry.Course loaded = read(written).courses.getFirst();
        assertEquals(new ScoreArea(ScoreArea.Role.NONE, CourseElement.LEFT, ScoreArea.DEFAULT_REACH, 6.0, 1.5, false),
                loaded.gates.get(2).scoreArea());
        assertEquals(new ScoreArea(ScoreArea.Role.TURN, CourseElement.RIGHT, 6.0, 2.0, 0.0, false), loaded.gates.get(5).scoreArea());
        assertEquals(CourseElement.FLAG, loaded.gates.get(5).scoringKind());
        assertEquals(1 + 1 + 3 + 1 + 2 + 3, loaded.checkpoints());
        CourseData data1 = loaded.toData();
        assertEquals(GateMount.HANGING, data1.gates().get(5).mount());
        assertEquals(11, data1.gates().get(5).variant());
        assertEquals(3, data1.gates().get(5).geometry().stages().size());
        assertTrue(data1.layoutHash() != legacyHash(data1), "Wertung ändert das Layout");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data1);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(data1, decoded);
        assertEquals(data1.layoutHash(), decoded.layoutHash());
        assertEquals(data1.geometry(), decoded.geometry());
    }

    @Test
    void unknownNewerFormatStillLoads() {
        CourseRegistry.FileData data = read(V040.replace("{\"nextCourseId\"", "{\"format\": 9, \"future\": {\"x\": 1}, \"nextCourseId\""));
        assertEquals(9, data.format());
        assertEquals(3, data.courses.getFirst().gates.size());
    }

    @Test
    void editPacketCarriesScoringAndOffset() {
        RacePayloads.GateEditC2S edit = new RacePayloads.GateEditC2S(9, RacePayloads.GateEditAction.APPLY, 45.0F, 0.0F, 0.0F, 0.5F, 4.0F, 13,
                new ScoreArea(ScoreArea.Role.PASS, CourseElement.RIGHT, 3.5, 4.0, 1.0, false).pack(), 0.0625F, -1.0F, 8.0F);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.GateEditC2S.CODEC.encode(buf, edit);
        RacePayloads.GateEditC2S decoded = RacePayloads.GateEditC2S.CODEC.decode(buf);
        assertFalse(buf.isReadable());
        assertEquals(edit, decoded);
        assertEquals(13, decoded.variant(), "Farben der Gate-Stange über 3 bleiben erhalten");
        assertTrue(GateMounting.nudgeAllowed(8.0, -8.0, 0.0625));
        assertFalse(GateMounting.nudgeAllowed(8.5, 0.0, 0.0));
        assertFalse(GateMounting.nudgeAllowed(Double.NaN, 0.0, 0.0));
    }
}
