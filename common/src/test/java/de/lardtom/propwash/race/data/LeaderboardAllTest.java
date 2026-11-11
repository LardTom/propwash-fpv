package de.lardtom.propwash.race.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.export.RaceDataJson;
import de.lardtom.propwash.race.export.RaceSnapshots;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardAllTest {
    static final int RECORDS = 120;
    static final int ASSISTED = 60;
    static final int OWN_RANK = 87;
    static final UUID VIEWER = new UUID(0x1BA11L, 87L);
    private static final long MS = 1_000_000L;
    private static final long DAY = TestWorlds.DAY;

    @TempDir
    Path temp;

    static UUID pilot(int index) {
        return new UUID(0x1BA11L, 1_000L + index);
    }

    static Leaderboard board() {
        Leaderboard board = new Leaderboard();
        int other = 0;
        for (int rank = 1; rank <= RECORDS; rank++) {
            UUID player = rank == OWN_RANK ? VIEWER : pilot(other++);
            board.submit(player, rank == OWN_RANK ? "Ich" : "Pilot" + rank, "race_5", (30_000L + rank * 10L) * MS, DAY + rank);
        }
        for (int rank = 1; rank <= ASSISTED; rank++) {
            board.submitAssisted(new UUID(0x1BA12L, rank), "Hilfe" + rank, "race_5", (20_000L + rank * 10L) * MS, DAY + rank,
                    LapAssist.HOLD_MODE.bit());
        }
        return board;
    }

    @Test
    void everyTimeOfAClassStaysOnTheBoard() {
        Leaderboard board = board();
        assertEquals(RECORDS, board.top("race_5", Integer.MAX_VALUE).size());
        assertEquals(RECORDS, board.top(null, Integer.MAX_VALUE).size());
        assertEquals(ASSISTED, board.assistedTop("race_5", Integer.MAX_VALUE).size());
        assertEquals(RECORDS + ASSISTED, board.times());
        assertEquals(OWN_RANK, board.rank(VIEWER, null));
    }

    @Test
    void storageKeepsEveryTimeBeyondTheOldLimits() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 260; i++) {
            board.submit(pilot(i), "Pilot" + i, "race_5", (40_000L + i) * MS, DAY + i);
            board.submitRace(pilot(i), 3, (130_000L + i) * MS, "Pilot" + i, "race_5", DAY + i);
        }
        assertEquals(260, board.storedTimes().size());
        assertEquals(260, board.storedRaceTimes().size());
        Leaderboard loaded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow()).getOrThrow();
        assertEquals(260, loaded.top("race_5", Integer.MAX_VALUE).size());
        assertEquals(260, loaded.raceRows(false, 3).size());
    }

    @Test
    void firstPacketCarriesAFullPageAndTheOwnRow() {
        RacePayloads.LeaderboardS2C payload = RaceServer.leaderboardPayload(board(), new CourseRegistry.Course(), VIEWER, false, true);
        Set<Integer> clean = new HashSet<>();
        Set<Integer> assisted = new HashSet<>();
        for (RacePayloads.BoardRow row : payload.rows()) {
            if (row.overallRank() > 0) {
                (row.assisted() ? assisted : clean).add(row.overallRank());
            }
        }
        for (int rank = 1; rank <= 50; rank++) {
            assertTrue(clean.contains(rank), "Platz " + rank + " ohne Flughilfe fehlt im ersten Paket");
            assertTrue(assisted.contains(rank), "Platz " + rank + " mit Flughilfe fehlt im ersten Paket");
        }
        assertTrue(clean.contains(OWN_RANK), "eigene Zeit fehlt");
        assertEquals(RECORDS + ASSISTED, payload.times());
    }

    @Test
    void apiAndJsonListEveryTime() throws IOException {
        CourseRegistry registry = RaceDataJsonTest.fixture(this.temp);
        CourseRegistry.Course course = registry.course(2);
        for (int i = 0; i < 600; i++) {
            registry.submitLap(course, pilot(i), "Pilot" + i, "race_5", (50_000L + i) * MS, DAY + i);
        }
        BoardSnapshot all = RaceSnapshots.board(course, registry.records(), BoardQuery.laps().limit(BoardQuery.MAX_LIMIT)).orElseThrow();
        assertEquals(600, all.total());
        assertEquals(600, all.entries().size());
        assertEquals(600, all.entries().getLast().rank());
        CourseInfo info = RaceSnapshots.info(course, registry.records(), List.of());
        JsonObject root = JsonParser.parseString(RaceDataJson.leaderboard(info, course, registry.records())).getAsJsonObject();
        JsonObject layout = root.getAsJsonArray("layouts").get(0).getAsJsonObject();
        JsonArray clean = layout.getAsJsonObject("laps").getAsJsonArray("clean");
        assertEquals(600, clean.size());
        assertEquals(600, layout.getAsJsonObject("classes").getAsJsonObject("clean").getAsJsonArray("race_5").size());
    }
}
