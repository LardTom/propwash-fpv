package de.lardtom.propwash.race;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistedLapsTest {
    private static final long S = 1_000_000_000L;
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final int HOLD = LapAssist.HOLD_MODE.bit();
    private static final int PHYSICS = LapAssist.WORLD_PHYSICS.bit();

    @Test
    void assistedTimesNeverEnterTheRankingOrBecomeRecords() {
        Leaderboard board = new Leaderboard();
        assertTrue(board.submitAssisted(ANNA, "Anna", "race_5", 15 * S, 1L, HOLD));
        assertTrue(board.top(null, 10).isEmpty());
        assertTrue(board.record(null).isEmpty());
        assertEquals(0, board.size());
        assertEquals(1, board.assistedSize());
        assertEquals(0, board.lapsOf(ANNA));
        assertEquals(Leaderboard.Outcome.COURSE_RECORD, board.submit(BEN, "Ben", "race_5", 20 * S, 2L));
        assertEquals(BEN, board.record(null).orElseThrow().player());
        assertEquals(1, board.rank(BEN, null));
        assertEquals(-1, board.rank(ANNA, null));
        assertEquals(1, board.assistedRank(ANNA, null));
        assertEquals(-1, board.assistedRank(BEN, null));
        Leaderboard.Entry assisted = board.assistedTop(null, 1).getFirst();
        assertTrue(assisted.assisted());
        assertEquals(HOLD, assisted.assist());
        assertFalse(board.top(null, 1).getFirst().assisted());
    }

    @Test
    void eachPilotKeepsTheBestAssistedTimePerClassSeparately() {
        Leaderboard board = new Leaderboard();
        board.submit(ANNA, "Anna", "race_5", 20 * S, 1L);
        assertTrue(board.submitAssisted(ANNA, "Anna", "race_5", 25 * S, 2L, HOLD));
        assertFalse(board.submitAssisted(ANNA, "Anna", "race_5", 26 * S, 3L, PHYSICS));
        assertTrue(board.submitAssisted(ANNA, "Anna", "race_5", 24 * S, 4L, PHYSICS));
        assertTrue(board.submitAssisted(ANNA, "Anna", "whoop_65", 40 * S, 5L, HOLD | PHYSICS));
        assertEquals(20 * S, board.best(ANNA, "race_5").nanos());
        assertEquals(24 * S, board.bestAssisted(ANNA, "race_5").nanos());
        assertEquals(PHYSICS, board.bestAssisted(ANNA, "race_5").assist());
        assertEquals(HOLD | PHYSICS, board.bestAssisted(ANNA, "whoop_65").assist());
        assertEquals(2, board.assistedView(ANNA, Leaderboard.TOP).size());
        assertEquals(1, board.view(ANNA, Leaderboard.TOP).size());
        assertFalse(board.submitAssisted(ANNA, "Anna", "race_5", -1L, 6L, HOLD));
    }

    @Test
    void assistedWithoutReasonIsStillMarked() {
        Leaderboard board = new Leaderboard();
        board.submitAssisted(BEN, "Ben", "race_5", 30 * S, 1L, LapAssist.NONE);
        assertEquals(LapAssist.OTHER.bit(), board.bestAssisted(BEN, null).assist());
        assertTrue(board.submitAssistedRace(BEN, 3, 90 * S, LapAssist.NONE));
        assertEquals(LapAssist.OTHER.bit(), board.assistedRaceBest(BEN, 3).assist());
    }

    @Test
    void assistedRacesAreSeparateFromRaceBests() {
        Leaderboard board = new Leaderboard();
        assertTrue(board.submitAssistedRace(ANNA, 3, 60 * S, HOLD));
        assertEquals(LapTracker.NONE, board.raceBest(ANNA, 3));
        assertFalse(board.submitAssistedRace(ANNA, 3, 61 * S, HOLD));
        assertTrue(board.submitRace(ANNA, 3, 70 * S));
        assertEquals(70 * S, board.raceBest(ANNA, 3));
        assertEquals(60 * S, board.assistedRaceBest(ANNA, 3).nanos());
        assertNull(board.assistedRaceBest(BEN, 3));
    }

    @Test
    void assistedClassesAreTrimmedLikeTheRanking() {
        Leaderboard board = new Leaderboard();
        for (int i = 0; i < Leaderboard.MAX_PER_CLASS + 7; i++) {
            board.submitAssisted(new UUID(3L, i), "P" + i, "race_5", (100 - i) * S, i, HOLD);
        }
        assertEquals(Leaderboard.MAX_PER_CLASS, board.assistedTop("race_5", Integer.MAX_VALUE).size());
        assertEquals(44 * S, board.assistedTop("race_5", 1).getFirst().nanos());
    }

    @Test
    void resetAndRemoveIncludeAssistedTimes() {
        Leaderboard board = new Leaderboard();
        board.submit(ANNA, "Anna", "race_5", 20 * S, 1L);
        board.submitAssisted(ANNA, "Anna", "race_5", 18 * S, 2L, HOLD);
        board.submitAssisted(BEN, "Ben", "race_5", 19 * S, 3L, HOLD);
        board.submitAssistedRace(BEN, 2, 40 * S, HOLD);
        assertEquals(3, board.times());
        assertEquals(2, board.removePlayer("anna"));
        assertEquals(1, board.times());
        assertEquals(1, board.clear());
        assertTrue(board.isEmpty());
        assertNull(board.assistedRaceBest(BEN, 2));
    }

    @Test
    void renamingCoversAssistedTimes() {
        Leaderboard board = new Leaderboard();
        board.submitAssisted(ANNA, "Anna", "race_5", 18 * S, 2L, HOLD);
        board.submit(ANNA, "Anna B", "race_5", 25 * S, 3L);
        assertEquals("Anna B", board.bestAssisted(ANNA, null).name());
    }

    @Test
    void oldSavedBoardsLoadUnchangedAndNewDataRoundTrips() {
        String old = "{\"entries\":[{\"player\":[0,0,0,10],\"name\":\"Anna\",\"frame\":\"race_5\","
                + "\"nanos\":20000000000,\"date\":111}],\"laps\":[{\"player\":[0,0,0,10],\"count\":4}],"
                + "\"races\":[{\"player\":[0,0,0,10],\"laps\":3,\"nanos\":70000000000}]}";
        Leaderboard legacy = Leaderboard.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(old)).getOrThrow();
        assertEquals(1, legacy.size());
        assertEquals(0, legacy.assistedSize());
        assertFalse(legacy.top(null, 1).getFirst().assisted());
        assertEquals(4, legacy.lapsOf(ANNA));
        assertEquals(70 * S, legacy.raceBest(ANNA, 3));
        JsonObject unchanged = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow().getAsJsonObject();
        assertFalse(unchanged.has("assisted"), unchanged.toString());
        assertFalse(unchanged.getAsJsonArray("entries").get(0).getAsJsonObject().has("assist"), unchanged.toString());

        legacy.submitAssisted(BEN, "Ben", "race_5", 17 * S, 222L, HOLD | PHYSICS);
        legacy.submitAssistedRace(BEN, 3, 55 * S, HOLD);
        JsonElement json = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, legacy).getOrThrow();
        Leaderboard decoded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(legacy.entries(), decoded.entries());
        assertEquals(legacy.assistedEntries(), decoded.assistedEntries());
        assertEquals(HOLD | PHYSICS, decoded.bestAssisted(BEN, null).assist());
        assertEquals(new Leaderboard.AssistedRace(55 * S, HOLD), decoded.assistedRaceBest(BEN, 3));
        assertEquals(ANNA, decoded.record(null).orElseThrow().player());
    }

    @Test
    void reportCarriesTheReasons() {
        int mask = HOLD | LapAssist.RESCUE.bit() | LapAssist.GOGGLES_LATENCY.bit();
        RacePayloads.RaceReportC2S report = new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, 3, 1, 20 * S, 0L,
                RacePayloads.RaceReportC2S.assistFlags(mask), 0L);
        RacePayloads.RaceReportC2S decoded = roundTrip(report);
        assertTrue(decoded.assisted());
        assertEquals(mask, decoded.assistMask());
        RacePayloads.RaceReportC2S plain = roundTrip(new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, 3, 1, 20 * S, 0L,
                RacePayloads.RaceReportC2S.assistFlags(LapAssist.NONE), 0L));
        assertFalse(plain.assisted());
        assertEquals(LapAssist.NONE, plain.assistMask());
        RacePayloads.RaceReportC2S bare = new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, 3, 1, 20 * S, 0L,
                RacePayloads.RaceReportC2S.FLAG_ASSISTED, 0L);
        assertEquals(LapAssist.OTHER.bit(), bare.assistMask());
        assertEquals(LapAssist.ALL, RacePayloads.RaceReportC2S.assistFlags(-1) >>> RacePayloads.RaceReportC2S.ASSIST_SHIFT);
    }

    @Test
    void leaderboardRowsCarryTheReasons() {
        RacePayloads.LeaderboardS2C payload = new RacePayloads.LeaderboardS2C(4, 1, 6, false, 3, List.of(
                new RacePayloads.BoardRow("Anna", "race_5", 20 * S, 111L, true, 1, 1),
                new RacePayloads.BoardRow("Ben", "race_5", 17 * S, 222L, false, 1, 1, HOLD | PHYSICS)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(buffer, payload);
        RacePayloads.LeaderboardS2C decoded = RacePayloads.LeaderboardS2C.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(payload, decoded);
        assertFalse(decoded.rows().get(0).assisted());
        assertTrue(decoded.rows().get(1).assisted());
        assertEquals(HOLD | PHYSICS, decoded.rows().get(1).assist());
    }

    private static RacePayloads.RaceReportC2S roundTrip(RacePayloads.RaceReportC2S report) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.RaceReportC2S.CODEC.encode(buffer, report);
        RacePayloads.RaceReportC2S decoded = RacePayloads.RaceReportC2S.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(report, decoded);
        return decoded;
    }
}
