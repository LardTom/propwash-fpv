package de.lardtom.propwash.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.race.Leaderboard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationBoardTopTest {
    private static final long MS = 1_000_000L;
    private static final UUID OWNER = UUID.fromString("37522c46-169d-4c8a-9e45-de8ad13bf162");
    private static final UUID PILOT_B = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
    private static final UUID PILOT_C = UUID.fromString("00000000-0000-4000-8000-0000000000b2");
    private static final UUID PILOT_D = UUID.fromString("00000000-0000-4000-8000-0000000000b3");

    @Test
    void topKeepsTheBestTimeOfEachPilotLikeTheFixtureExpectations() {
        Leaderboard board = new Leaderboard();
        board.submit(OWNER, "PropwashFixture", "race_5", 6000 * MS, 1L);
        board.submit(PILOT_B, "FixturePilotB", "race_5", 7420 * MS, 2L);
        board.submit(OWNER, "PropwashFixture", "freestyle_5", 7500 * MS, 3L);
        board.submit(PILOT_C, "FixturePilotC", "race_5", 8015 * MS, 4L);
        board.submit(PILOT_D, "FixturePilotD", "whoop_65", 15300 * MS, 5L);

        JsonObject values = MigrationValues.leaderboard(JsonOps.INSTANCE, board);

        assertEquals(5, values.get("size").getAsInt());
        assertEquals(5, values.getAsJsonObject("codec").getAsJsonArray("entries").size(), "gespeicherte Zeilen bleiben vollständig");
        assertEquals(List.of("PropwashFixture race_5", "FixturePilotB race_5", "FixturePilotC race_5", "FixturePilotD whoop_65"),
                rows(values.getAsJsonArray("top")));
    }

    @Test
    void topMatchesAnExpectationWrittenBeforeEveryClassGotItsOwnRow() {
        Leaderboard board = new Leaderboard();
        board.submit(OWNER, "PropwashFixture", "race_5", 6000 * MS, 1L);
        board.submit(PILOT_B, "FixturePilotB", "race_5", 7420 * MS, 2L);
        board.submit(OWNER, "PropwashFixture", "freestyle_5", 7500 * MS, 3L);
        board.submit(PILOT_C, "FixturePilotC", "race_5", 8015 * MS, 4L);
        board.submit(PILOT_D, "FixturePilotD", "whoop_65", 15300 * MS, 5L);
        JsonObject expected = MigrationValues.leaderboard(JsonOps.INSTANCE, board).deepCopy();
        JsonArray old = new JsonArray();
        for (UUID pilot : List.of(OWNER, PILOT_B, PILOT_C, PILOT_D)) {
            old.add(Leaderboard.Entry.CODEC.encodeStart(JsonOps.INSTANCE, board.best(pilot, null)).getOrThrow());
        }
        expected.add("top", old);

        MigrationValues.Diff diff = new MigrationValues.Diff(false);
        MigrationValues.compare("courses[id=1].leaderboard", expected, MigrationValues.leaderboard(JsonOps.INSTANCE, board), diff);

        assertEquals(List.of(), diff.failures, diff.report());
        assertTrue(diff.compared > 0);
    }

    @Test
    void topCountsFiftyPilotsNotFiftyRows() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 60; i++) {
            UUID pilot = new UUID(0L, 1000L + i);
            board.submit(pilot, "Pilot" + i, "race_5", (20_000 + i) * MS, i);
            board.submit(pilot, "Pilot" + i, "whoop_65", (20_000 + i) * MS + 1, 100L + i);
        }

        JsonArray top = MigrationValues.leaderboard(JsonOps.INSTANCE, board).getAsJsonArray("top");

        assertEquals(50, top.size());
        Set<String> pilots = new HashSet<>();
        for (JsonElement entry : top) {
            pilots.add(entry.getAsJsonObject().get("player").toString());
            assertEquals("race_5", entry.getAsJsonObject().get("frame").getAsString(), "Bestzeit je Pilot");
        }
        assertEquals(50, pilots.size());
        assertEquals("Pilot49", top.get(49).getAsJsonObject().get("name").getAsString());
    }

    private static List<String> rows(JsonArray top) {
        List<String> rows = new ArrayList<>();
        for (JsonElement entry : top) {
            JsonObject row = entry.getAsJsonObject();
            rows.add(row.get("name").getAsString() + " " + row.get("frame").getAsString());
        }
        return rows;
    }
}
