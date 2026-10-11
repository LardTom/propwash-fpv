package de.lardtom.propwash.race;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardTest {
    private static final long S = 1_000_000_000L;
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID CARL = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Test
    void outcomesFollowCourseClassAndPersonalBests() {
        Leaderboard board = new Leaderboard();
        assertEquals(Leaderboard.Outcome.COURSE_RECORD, board.submit(ANNA, "Anna", "race_5", 20 * S, 1L));
        assertEquals(Leaderboard.Outcome.PERSONAL_BEST, board.submit(BEN, "Ben", "race_5", 22 * S, 2L));
        assertEquals(Leaderboard.Outcome.NONE, board.submit(BEN, "Ben", "race_5", 23 * S, 3L));
        assertEquals(Leaderboard.Outcome.PERSONAL_BEST, board.submit(BEN, "Ben", "race_5", 21 * S, 4L));
        assertEquals(Leaderboard.Outcome.CLASS_RECORD, board.submit(CARL, "Carl", "whoop_65", 30 * S, 5L));
        assertEquals(Leaderboard.Outcome.CLASS_RECORD, board.submit(BEN, "Ben", "whoop_65", 28 * S, 6L));
        assertEquals(Leaderboard.Outcome.COURSE_RECORD, board.submit(CARL, "Carl", "race_5", 19 * S, 7L));
        assertEquals(Leaderboard.Outcome.NONE, board.submit(ANNA, "Anna", "race_5", -1L, 8L));
        assertEquals(4, board.lapsOf(BEN));
        assertEquals(2, board.lapsOf(CARL));
    }

    @Test
    void rankingIsOrderedAndOverallViewKeepsOneEntryPerPilot() {
        Leaderboard board = new Leaderboard();
        board.submit(ANNA, "Anna", "race_5", 20 * S, 10L);
        board.submit(BEN, "Ben", "race_5", 20 * S, 5L);
        board.submit(BEN, "Ben", "whoop_65", 25 * S, 6L);
        board.submit(CARL, "Carl", "whoop_65", 24 * S, 7L);
        List<Leaderboard.Entry> overall = board.top(null, 10);
        assertEquals(List.of("Ben", "Anna", "Carl"), overall.stream().map(Leaderboard.Entry::name).toList());
        assertEquals(List.of("Carl", "Ben"), board.top("whoop_65", 10).stream().map(Leaderboard.Entry::name).toList());
        assertEquals(1, board.rank(BEN, null));
        assertEquals(2, board.rank(ANNA, null));
        assertEquals(2, board.rank(BEN, "whoop_65"));
        assertEquals(-1, board.rank(ANNA, "whoop_65"));
        assertEquals(List.of("race_5", "whoop_65"), board.classes());
        assertEquals(20 * S, board.record(null).orElseThrow().nanos());
        assertEquals(BEN, board.record(null).orElseThrow().player());
        assertNull(board.best(ANNA, "whoop_65"));
    }

    @Test
    void viewContainsTopTenPerClassAndTheViewer() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < 15; i++) {
            UUID player = new UUID(1L, i);
            board.submit(player, "P" + i, "race_5", (30 + i) * S, i);
        }
        UUID slow = new UUID(1L, 14);
        List<Leaderboard.Entry> view = board.view(slow, Leaderboard.TOP);
        assertEquals(11, view.size());
        assertTrue(view.stream().anyMatch(e -> e.player().equals(slow)));
        assertEquals(15, board.rank(slow, null));
        assertFalse(board.view(null, Leaderboard.TOP).stream().anyMatch(e -> e.player().equals(slow)));
    }

    @Test
    void classesAreTrimmed() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < Leaderboard.MAX_PER_CLASS + 10; i++) {
            board.submit(new UUID(2L, i), "P" + i, "race_5", (100 - i) * S, i);
        }
        board.submit(ANNA, "Anna", "whoop_65", 500 * S, 0L);
        assertEquals(Leaderboard.MAX_PER_CLASS, board.top("race_5", Integer.MAX_VALUE).size());
        assertEquals(1, board.top("whoop_65", Integer.MAX_VALUE).size());
        assertEquals(41 * S, board.top("race_5", 1).getFirst().nanos());
    }

    @Test
    void renamedPilotKeepsEntries() {
        Leaderboard board = new Leaderboard();
        board.submit(ANNA, "Anna", "race_5", 20 * S, 1L);
        board.submit(ANNA, "Anna2", "race_5", 25 * S, 2L);
        assertEquals("Anna2", board.top(null, 1).getFirst().name());
        assertEquals(20 * S, board.top(null, 1).getFirst().nanos());
        assertEquals(1, board.removePlayer("anna2"));
        assertTrue(board.top(null, 10).isEmpty());
    }

    @Test
    void codecRoundTrip() {
        Leaderboard board = new Leaderboard();
        board.submit(ANNA, "Anna", "race_5", 20 * S, 111L);
        board.submit(BEN, "Ben", "", 22 * S, 222L);
        board.submitRace(ANNA, 3, 70 * S);
        JsonElement json = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow();
        Leaderboard decoded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(board.entries(), decoded.entries());
        assertEquals(1, decoded.lapsOf(ANNA));
        assertEquals(70 * S, decoded.raceBest(ANNA, 3));
        assertFalse(decoded.submitRace(ANNA, 3, 71 * S));
        assertTrue(decoded.submitRace(ANNA, 3, 69 * S));
    }

    @Test
    void legacyEntriesAreImportedWithoutClass() {
        Leaderboard board = new Leaderboard();
        board.importLegacy(ANNA, "Anna", 18 * S, 99L, 12, Map.of("3", 60 * S, "x", 1L));
        assertEquals("", board.top(null, 1).getFirst().frame());
        assertEquals(12, board.lapsOf(ANNA));
        assertEquals(60 * S, board.raceBest(ANNA, 3));
        assertEquals(Leaderboard.Outcome.CLASS_RECORD, board.submit(BEN, "Ben", "race_5", 19 * S, 100L));
    }
}
