package de.lardtom.propwash.race;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import net.minecraft.core.UUIDUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceStandardScoringTest {
    private static final long S = 1_000_000_000L;
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String FRAME = "race_5";
    private static final RaceRuleSet CALM = rules(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0));
    private static final RaceRuleSet DISARM_OFF = rules(RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DISARM, 0));
    private static final RaceStandard WIND_ZERO = RaceStandard.DEFAULTS.with("wind", 0);
    private static final Leaderboard.Scoring DEFAULTS = new Leaderboard.Scoring(RaceStandard.DEFAULTS);
    private static final Leaderboard.Scoring WIND_ZERO_SHOWN = new Leaderboard.Scoring(WIND_ZERO);

    private static RaceRuleSet rules(RuleValues values) {
        return RaceRules.world(RaceRules.values(values, Map.of()));
    }

    private static Leaderboard board(Leaderboard.Scoring scoring) {
        Leaderboard board = new Leaderboard();
        board.score(scoring);
        return board;
    }

    private static Leaderboard.Result lap(Leaderboard board, UUID player, long nanos, RaceRuleSet rules, boolean keep) {
        return board.submitTime(player, player.equals(ANNA) ? "Anna" : "Ben", FRAME, nanos, nanos / S, AssistReasons.NONE, rules, keep);
    }

    @Test
    void disarmOnCrashOnAndOffBothCountAsUnassisted() {
        Leaderboard board = board(DEFAULTS);
        Leaderboard.Result off = lap(board, ANNA, 30 * S, DISARM_OFF, false);
        assertTrue(off.clean());
        assertEquals(Leaderboard.Outcome.COURSE_RECORD, off.outcome());
        Leaderboard.Result on = lap(board, ANNA, 29 * S, RaceRuleSet.DEFAULTS, false);
        assertTrue(on.clean());
        assertEquals(29 * S, board.best(ANNA, FRAME).nanos());
        assertEquals(1, board.storedTimes().size());
        assertTrue(board.assistedEntries().isEmpty());
    }

    @Test
    void changingTheStandardReratesOldTimesWithoutArchiving() {
        Leaderboard board = board(DEFAULTS);
        assertTrue(lap(board, ANNA, 30 * S, RaceRuleSet.DEFAULTS, true).clean());
        Leaderboard.Result calm = lap(board, ANNA, 25 * S, CALM, true);
        assertFalse(calm.clean());
        assertTrue(calm.stored());
        assertTrue(lap(board, BEN, 28 * S, CALM, true).stored());
        assertEquals(30 * S, board.best(ANNA, FRAME).nanos());
        assertEquals(List.of(ANNA), board.top(null, 10).stream().map(Leaderboard.Entry::player).toList());
        assertEquals(List.of(ANNA, BEN), board.assistedTop(null, 10).stream().map(Leaderboard.Entry::player).toList());
        Leaderboard.Entry flagged = board.bestAssisted(ANNA, FRAME);
        assertEquals(LapAssist.WORLD_PHYSICS.bit(), flagged.assist());
        assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, "wind", 0, 1)), flagged.details());
        int stored = board.stored();

        board.score(WIND_ZERO_SHOWN);
        assertEquals(stored, board.stored());
        assertEquals(25 * S, board.best(ANNA, FRAME).nanos());
        assertEquals(List.of(ANNA, BEN), board.top(null, 10).stream().map(Leaderboard.Entry::player).toList());
        assertEquals(30 * S, board.bestAssisted(ANNA, FRAME).nanos());
        assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, "wind", 1, 0)), board.bestAssisted(ANNA, FRAME).details());
        assertNull(board.bestAssisted(BEN, FRAME));
        assertEquals(ANNA, board.record(null).orElseThrow().player());

        board.score(DEFAULTS);
        assertEquals(30 * S, board.best(ANNA, FRAME).nanos());
        assertNull(board.best(BEN, FRAME));
        assertEquals(stored, board.stored());
    }

    @Test
    void timesWithOtherRulesAreKeptEvenWhenAssistedLapsAreNotSaved() {
        Leaderboard board = board(DEFAULTS);
        Leaderboard.Result calm = lap(board, ANNA, 25 * S, CALM, false);
        assertFalse(calm.clean());
        assertTrue(calm.stored());
        assertFalse(calm.improved());
        assertTrue(board.assistedEntries().isEmpty());
        assertTrue(board.entries().isEmpty());
        assertEquals(1, board.stored());
        assertTrue(board.storedTimes().getFirst().hidden());
        Leaderboard.Result hold = board.submitTime(ANNA, "Anna", FRAME, 20 * S, 1L, AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD))),
                RaceRuleSet.DEFAULTS, false);
        assertFalse(hold.stored());
        assertEquals(1, board.stored());
        board.score(WIND_ZERO_SHOWN);
        assertEquals(25 * S, board.best(ANNA, FRAME).nanos());
        assertEquals(ANNA, board.record(null).orElseThrow().player());
        board.score(DEFAULTS);
        assertNull(board.best(ANNA, FRAME));
        assertNull(board.bestAssisted(ANNA, FRAME));
        Leaderboard.Result shown = lap(board, ANNA, 26 * S, CALM, true);
        assertTrue(shown.stored());
        assertTrue(shown.improved());
        assertEquals(26 * S, board.bestAssisted(ANNA, FRAME).nanos());
        assertEquals(2, board.stored());
        board.score(WIND_ZERO_SHOWN);
        assertEquals(25 * S, board.best(ANNA, FRAME).nanos());
        Leaderboard.Result hiddenRace = board.submitRaceTime(BEN, 2, 60 * S, "Ben", FRAME, 1L, AssistReasons.NONE, RaceRuleSet.DEFAULTS, false);
        assertTrue(hiddenRace.stored());
        assertFalse(hiddenRace.improved());
        assertNull(board.assistedRaceBest(BEN, 2));
        board.score(DEFAULTS);
        assertEquals(60 * S, board.raceBest(BEN, 2));
    }

    @Test
    void eachPilotKeepsTheBestTimePerRuleSetUpToALimit() {
        Leaderboard board = board(DEFAULTS);
        assertTrue(lap(board, ANNA, 30 * S, CALM, true).stored());
        assertFalse(lap(board, ANNA, 31 * S, CALM, true).stored());
        assertTrue(lap(board, ANNA, 29 * S, CALM, true).stored());
        assertTrue(lap(board, ANNA, 40 * S, RaceRuleSet.DEFAULTS, true).stored());
        assertEquals(2, board.storedTimes().size());
        for (int gravity = 1; gravity <= Leaderboard.MAX_RULE_SETS + 4; gravity++) {
            lap(board, ANNA, (50 + gravity) * S, rules(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 100 + gravity)), true);
        }
        assertEquals(Leaderboard.MAX_RULE_SETS, board.storedTimes().size());
        assertEquals(40 * S, board.best(ANNA, FRAME).nanos());
        assertTrue(board.storedTimes().stream().anyMatch(entry -> entry.rules().equals(CALM) && entry.nanos() == 29 * S));
    }

    @Test
    void raceTimesFollowTheStandardToo() {
        Leaderboard board = board(DEFAULTS);
        Leaderboard.Result calm = board.submitRaceTime(ANNA, 3, 90 * S, "Anna", FRAME, 1L, AssistReasons.NONE, CALM, true);
        assertFalse(calm.clean());
        assertTrue(calm.stored());
        assertTrue(calm.improved());
        assertTrue(board.submitRaceTime(ANNA, 3, 95 * S, "Anna", FRAME, 2L, AssistReasons.NONE, DISARM_OFF, true).clean());
        assertEquals(95 * S, board.raceBest(ANNA, 3));
        assertEquals(90 * S, board.assistedRaceBest(ANNA, 3).nanos());
        board.score(WIND_ZERO_SHOWN);
        assertEquals(90 * S, board.raceBest(ANNA, 3));
        assertEquals(95 * S, board.assistedRaceBest(ANNA, 3).nanos());
        assertEquals(List.of(new RaceRuleSet.Seen(WorldRuleSpecs.WIND.id(), 0, RaceRuleSet.WORLD, false)), board.raceRows(false, 3).getFirst().rules().seen());
    }

    @Test
    void savedTimesKeepTheirRules() {
        Leaderboard board = board(DEFAULTS);
        lap(board, ANNA, 30 * S, RaceRuleSet.DEFAULTS, true);
        lap(board, ANNA, 25 * S, CALM, true);
        board.submitRaceTime(BEN, 2, 70 * S, "Ben", FRAME, 3L, AssistReasons.NONE, CALM, true);
        JsonElement json = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow();
        Leaderboard decoded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        decoded.score(WIND_ZERO_SHOWN);
        assertEquals(board.storedTimes(), decoded.storedTimes());
        assertEquals(board.storedRaceTimes(), decoded.storedRaceTimes());
        assertEquals(25 * S, decoded.best(ANNA, FRAME).nanos());
        assertEquals(70 * S, decoded.raceBest(BEN, 2));
        JsonObject root = json.getAsJsonObject();
        assertTrue(root.has("entries"));
        assertTrue(root.has("assisted"));
        assertTrue(root.has("lap_times"));
    }

    @Test
    void oldTimesCountAsFlownWithTheDefaultsAndNeutralReasonsFallAway() {
        JsonObject root = new JsonObject();
        JsonArray entries = new JsonArray();
        entries.add(entry(ANNA, "Anna", 30, 0, null));
        root.add("entries", entries);
        JsonArray assisted = new JsonArray();
        JsonArray disarm = new JsonArray();
        disarm.add(detail("world_physics", "crash_disarm", 0, 1));
        assisted.add(entry(BEN, "Ben", 27, LapAssist.WORLD_PHYSICS.bit(), disarm));
        JsonArray wind = new JsonArray();
        wind.add(detail("world_physics", "wind", 0, 1));
        assisted.add(entry(UUID.fromString("00000000-0000-0000-0000-0000000000c3"), "Cleo", 26, LapAssist.WORLD_PHYSICS.bit(), wind));
        root.add("assisted", assisted);
        Leaderboard board = Leaderboard.CODEC.parse(JsonOps.INSTANCE, root).getOrThrow();
        board.score(DEFAULTS);
        assertEquals(List.of("Ben", "Anna"), board.top(null, 10).stream().map(Leaderboard.Entry::name).toList());
        assertEquals(List.of("Cleo"), board.assistedTop(null, 10).stream().map(Leaderboard.Entry::name).toList());
        board.score(WIND_ZERO_SHOWN);
        assertEquals(List.of("Cleo"), board.top(null, 10).stream().map(Leaderboard.Entry::name).toList());
        assertEquals(List.of("Ben", "Anna"), board.assistedTop(null, 10).stream().map(Leaderboard.Entry::name).toList());
    }

    private static JsonObject entry(UUID player, String name, int seconds, int assist, JsonArray details) {
        JsonObject out = new JsonObject();
        out.add("player", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, player).getOrThrow());
        out.addProperty("name", name);
        out.addProperty("frame", FRAME);
        out.addProperty("nanos", seconds * S);
        out.addProperty("date", 1_700_000_000_000L + seconds);
        if (assist != 0) {
            out.addProperty("assist", assist);
        }
        if (details != null) {
            out.add("assist_details", details);
        }
        return out;
    }

    private static JsonObject detail(String reason, String subject, int value, int reference) {
        JsonObject out = new JsonObject();
        out.addProperty("reason", reason);
        out.addProperty("subject", subject);
        out.addProperty("value", value);
        out.addProperty("reference", reference);
        return out;
    }
}
