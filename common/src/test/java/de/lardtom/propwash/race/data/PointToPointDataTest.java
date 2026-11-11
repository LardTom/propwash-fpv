package de.lardtom.propwash.race.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointToPointDataTest {
    private static final long S = TestWorlds.S;
    private static final long DAY = TestWorlds.DAY;

    @TempDir
    Path temp;

    private static CourseData roundTrip(CourseData data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CourseData.STREAM_CODEC.encode(buf, data);
        CourseData decoded = CourseData.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        return decoded;
    }

    @Test
    void oldCoursesAreLapsAndKeepTheirFile() throws IOException {
        for (String world : List.of(TestWorlds.V040, TestWorlds.V050, TestWorlds.V052)) {
            CourseRegistry registry = TestWorlds.open(this.temp.resolve(Integer.toString(world.hashCode())), world);
            for (CourseRegistry.Course course : registry.courses()) {
                assertEquals(CourseMode.LAPS, course.mode());
                assertFalse(course.pointToPoint());
                assertEquals(CourseMode.LAPS, course.toData().mode());
            }
            registry.markDirty();
            registry.save();
            String saved = Files.readString(registry.path(), StandardCharsets.UTF_8);
            assertFalse(saved.contains("\"mode\""), "Runden-Kurse schreiben kein Feld mode");
        }
    }

    @Test
    void switchingTheModeIsALayoutChangeWithTheSameId() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        assertNotNull(course);
        UUID key = course.key();
        int lapsHash = course.toData().layoutHash();
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, DAY);
        assertTrue(registry.setMode(course, CourseMode.POINT_TO_POINT));
        assertFalse(registry.setMode(course, CourseMode.POINT_TO_POINT), "gleicher Modus ist keine Änderung");
        assertEquals(CourseMode.POINT_TO_POINT, course.mode());
        assertTrue(course.pointToPoint());
        assertEquals(2, course.layout, "die Bestenliste der Runden wird archiviert");
        assertEquals(key, course.key());
        assertNotEquals(lapsHash, course.toData().layoutHash(), "Geister der Runden passen nicht zu Punkt zu Punkt");
        BoardSnapshot archived = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().layout(1)).orElseThrow();
        assertEquals(List.of(TestWorlds.ANNA), archived.entries().stream().map(e -> e.player()).toList());
        BoardSnapshot current = RaceSnapshots.board(course, registry.records(), BoardQuery.laps()).orElseThrow();
        assertTrue(current.entries().isEmpty());
        registry.submitLap(course, TestWorlds.BEN, "Ben", "race_5", 12 * S, DAY + 1);
        assertTrue(registry.setMode(course, CourseMode.LAPS));
        assertEquals(3, course.layout);
        assertEquals(lapsHash, course.toData().layoutHash(), "zurück zu Runden ergibt die alte Prüfsumme");
        assertEquals(List.of(1, 2, 3), RaceSnapshots.layouts(course, registry.records()));
        BoardSnapshot point = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().layout(2)).orElseThrow();
        assertEquals(12_000L, point.entries().getFirst().timeMillis(), "Zeit = Gesamtzeit von Start bis Ziel");
    }

    @Test
    void modeIsSavedAndLoaded() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(2);
        assertNotNull(course);
        registry.setMode(course, CourseMode.POINT_TO_POINT);
        registry.save();
        String saved = Files.readString(registry.path(), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"mode\": \"point_to_point\""));
        CourseRegistry reopened = TestWorlds.reopen(registry.path());
        assertEquals(CourseMode.POINT_TO_POINT, reopened.course(2).mode());
        assertEquals(CourseMode.LAPS, reopened.course(1).mode());
        assertEquals(course.toData().layoutHash(), reopened.course(2).toData().layoutHash());
    }

    @Test
    void unknownModeLoadsAsLaps() throws IOException {
        String json = TestWorlds.V052.replace("\"name\": \"Stapel-Kurs\", ", "\"name\": \"Stapel-Kurs\", \"mode\": \"zickzack\", ");
        CourseRegistry registry = TestWorlds.open(this.temp, json);
        assertEquals(CourseMode.LAPS, registry.course(1).mode());
        assertNull(registry.course(1).mode);
    }

    @Test
    void networkCarriesTheMode() {
        CourseData laps = new CourseData(5, TestWorlds.OVERWORLD, List.of(new CourseData.Gate(0, 0.5, 64.0, 0.5, 0.0F,
                GateSize.MEDIUM), new CourseData.Gate(1, 0.5, 64.0, 12.5, 0.0F, GateSize.MEDIUM)),
                "Sprint", 3, TestWorlds.ANNA, "Anna", UUID.fromString("5f0c2a8e-3b1d-4c6e-9a7f-1b2c3d4e5f60"));
        assertEquals(CourseMode.LAPS, laps.mode());
        CourseData sent = roundTrip(laps);
        assertEquals(laps, sent);
        CourseData point = laps.withMode(CourseMode.POINT_TO_POINT);
        CourseData received = roundTrip(point);
        assertEquals(CourseMode.POINT_TO_POINT, received.mode());
        assertEquals(point.key(), received.key());
        assertTrue(received.pointToPoint());
        assertTrue(received.finish(1));
        assertFalse(received.finish(0));
        CourseData noKey = new CourseData(6, TestWorlds.OVERWORLD, laps.gates(), "", 1, null, "", null, CourseMode.POINT_TO_POINT);
        assertEquals(noKey, roundTrip(noKey));
        assertFalse(point.withGates(List.of(point.gates().getFirst())).pointToPoint(), "ein Element ist kein Punkt-zu-Punkt-Kurs");
    }

    @Test
    void raceDataReportsTheMode() throws IOException {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        CourseInfo before = RaceSnapshots.info(course, registry.records(), List.of());
        assertEquals(CourseMode.LAPS, before.mode());
        assertFalse(before.pointToPoint());
        registry.setMode(course, CourseMode.POINT_TO_POINT);
        CourseInfo info = RaceSnapshots.info(course, registry.records(), List.of());
        assertEquals(CourseMode.POINT_TO_POINT, info.mode());
        assertTrue(info.pointToPoint());
        JsonObject json = RaceDataJson.course(info, RaceSnapshots.creativeFc(course), false);
        assertEquals("point_to_point", json.get("mode").getAsString());
        JsonObject board = JsonParser.parseString(RaceDataJson.leaderboard(info, course, registry.records())).getAsJsonObject();
        assertEquals("point_to_point", board.get("mode").getAsString());
        assertEquals("laps", RaceDataJson.course(before, RaceSnapshots.creativeFc(course), false).get("mode").getAsString());
        CourseInfo legacy = new CourseInfo(info.id(), info.number(), info.name(), info.owner(), info.ownerName(), info.dimension(), info.layout(),
                info.layouts(), info.laps(), info.elements(), info.checkpoints(), info.worldCourse(), info.start(), info.zones(), info.record());
        assertEquals(CourseMode.LAPS, legacy.mode(), "alter Konstruktor bleibt bei Runden");
        assertEquals(CourseMode.POINT_TO_POINT, CourseMode.byKey("point_to_point"));
        assertEquals(CourseMode.LAPS, CourseMode.byKey("laps"));
    }
}
