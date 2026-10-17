package de.lardtom.propwash.race;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistReasonsTest {
    private static final long S = 1_000_000_000L;
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final AssistDetail GRAVITY = AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 130);
    private static final AssistDetail HOLD = AssistDetail.hold(HoldMode.ALTHOLD);

    @Test
    void detailsFollowTheMaskAndKeepTheLatestValue() {
        AssistReasons reasons = AssistReasons.of(LapAssist.WORLD_PHYSICS.bit(), List.of(HOLD, GRAVITY, AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 150)));
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 150)), reasons.details());
        AssistReasons sorted = AssistReasons.of(List.of(GRAVITY, HOLD));
        assertEquals(List.of(HOLD, GRAVITY), sorted.details());
        assertEquals(LapAssist.HOLD_MODE.bit() | LapAssist.WORLD_PHYSICS.bit(), sorted.mask());
        List<AssistDetail> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(AssistDetail.issue("issue_" + i));
        }
        assertEquals(AssistReasons.MAX_DETAILS, AssistReasons.of(many).details().size());
    }

    @Test
    void combiningAndFiltering() {
        AssistReasons hold = AssistReasons.of(List.of(HOLD));
        AssistReasons physics = AssistReasons.of(List.of(GRAVITY));
        AssistReasons both = hold.plus(physics);
        assertEquals(List.of(HOLD, GRAVITY), both.details());
        assertSame(hold, hold.plus(AssistReasons.NONE));
        assertSame(physics, AssistReasons.NONE.plus(physics));
        assertEquals(physics, both.only(LapAssist.SERVER_REASONS));
        assertEquals(hold, both.without(LapAssist.SERVER_REASONS));
        assertSame(AssistReasons.NONE, both.only(LapAssist.GOGGLES_LATENCY.bit()));
        assertEquals(LapAssist.OTHER.bit(), AssistReasons.NONE.orOther().mask());
        assertSame(hold, hold.orOther());
        assertTrue(both.has(LapAssist.HOLD_MODE));
        assertFalse(both.has(LapAssist.RESCUE));
        assertEquals(AssistReasons.NONE, AssistReasons.of(-1 & ~LapAssist.ALL));
    }

    @Test
    void subjectsAreCleanedForStorageAndTranslationKeys() {
        assertEquals("gravity_scale", AssistDetail.clean("Gravity_Scale"));
        assertEquals("abc", AssistDetail.clean("a.b/c §"));
        assertEquals("", AssistDetail.clean(null));
        assertEquals(AssistDetail.MAX_SUBJECT, AssistDetail.clean("x".repeat(200)).length());
        assertEquals(WorldRuleSpecs.GRAVITY_SCALE, GRAVITY.rule().orElseThrow());
        assertTrue(HOLD.rule().isEmpty());
    }

    @Test
    void detailCodecSurvivesUnknownReasons() {
        JsonElement json = AssistDetail.CODEC.encodeStart(JsonOps.INSTANCE, GRAVITY).getOrThrow();
        assertEquals(GRAVITY, AssistDetail.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        AssistDetail future = AssistDetail.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"reason\":\"autopilot\",\"subject\":\"x\"}"))
                .getOrThrow();
        assertEquals(LapAssist.OTHER, future.reason());
    }

    @Test
    void leaderboardStoresDetailsOnlyWhenThereAreAny() {
        Leaderboard board = new Leaderboard();
        board.submitAssisted(ANNA, "Anna", "race_5", 20 * S, 1L, LapAssist.HOLD_MODE.bit());
        JsonObject plain = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow().getAsJsonObject();
        assertFalse(plain.getAsJsonArray("assisted").get(0).getAsJsonObject().has("assist_details"), plain.toString());
        AssistReasons reasons = AssistReasons.of(List.of(HOLD, GRAVITY));
        assertTrue(board.submitAssisted(ANNA, "Anna", "race_5", 19 * S, 2L, reasons));
        assertTrue(board.submitAssistedRace(ANNA, 3, 60 * S, reasons));
        JsonElement json = Leaderboard.CODEC.encodeStart(JsonOps.INSTANCE, board).getOrThrow();
        Leaderboard decoded = Leaderboard.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(reasons, decoded.bestAssisted(ANNA, null).reasons());
        assertEquals(reasons, decoded.assistedRaceBest(ANNA, 3).reasons());
        assertTrue(board.submitAssisted(ANNA, "Anna", "race_5", 18 * S, 3L, AssistReasons.NONE));
        assertEquals(LapAssist.OTHER.bit(), board.bestAssisted(ANNA, null).assist());
    }

    @Test
    void reportAndRowsCarryTheDetails() {
        List<AssistDetail> details = List.of(HOLD, AssistDetail.goggles(10, 25), GRAVITY);
        int mask = LapAssist.HOLD_MODE.bit() | LapAssist.GOGGLES_LATENCY.bit() | LapAssist.WORLD_PHYSICS.bit();
        RacePayloads.RaceReportC2S report = new RacePayloads.RaceReportC2S(RacePayloads.ReportKind.LAP, 3, 1, 20 * S, 0L,
                RacePayloads.RaceReportC2S.assistFlags(mask), 0L, details);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.RaceReportC2S.CODEC.encode(buffer, report);
        RacePayloads.RaceReportC2S decoded = RacePayloads.RaceReportC2S.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(report, decoded);
        assertEquals(AssistReasons.of(mask, details), decoded.reasons());

        RacePayloads.LeaderboardS2C payload = new RacePayloads.LeaderboardS2C(4, 1, 6, false, 3, List.of(
                new RacePayloads.BoardRow("Ben", "race_5", 17 * S, 222L, false, 1, 1, mask, details)));
        FriendlyByteBuf rows = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.LeaderboardS2C.CODEC.encode(rows, payload);
        RacePayloads.LeaderboardS2C board = RacePayloads.LeaderboardS2C.CODEC.decode(rows);
        assertEquals(payload, board);
        assertEquals(AssistReasons.of(mask, details), board.rows().getFirst().reasons());
    }

    @Test
    void oversizedDetailListsAreRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(RacePayloads.ReportKind.LAP.ordinal());
        buffer.writeVarInt(1);
        buffer.writeVarInt(1);
        buffer.writeVarLong(S);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(0);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(AssistReasons.MAX_DETAILS + 1);
        assertThrows(RuntimeException.class, () -> RacePayloads.RaceReportC2S.CODEC.decode(buffer));
    }
}
