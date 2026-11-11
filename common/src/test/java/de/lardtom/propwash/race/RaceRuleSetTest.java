package de.lardtom.propwash.race;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneField;
import de.lardtom.propwash.zone.ZoneShape;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceRuleSetTest {
    private static final String DIM = Zone.OVERWORLD;
    private static final long MS = 1_000_000L;
    private static final String WIND = WorldRuleSpecs.WIND.id();
    private static final String GRAVITY = WorldRuleSpecs.GRAVITY_SCALE.id();

    private static Zone zone(int id, boolean raceLegal, Map<String, Integer> overrides) {
        return new Zone(id, DIM, "z" + id, ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, raceLegal, 0, overrides);
    }

    private static RaceRuleSet inside(Zone zone, RuleValues world) {
        ZoneField field = new ZoneField();
        field.update(List.of(zone), DIM, PhysicsRules.of(world), 5, 5, 5, 0.0);
        return RaceRules.observe(RaceRules.values(world, Map.of()), field.resolution(), field);
    }

    @Test
    void worldRulesAtTheirDefaultsAreTheEmptySet() {
        RaceRuleSet rules = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS, Map.of()));
        assertTrue(rules.isDefault());
        assertEquals(RaceRuleSet.DEFAULTS, rules);
        assertTrue(rules.matches(RaceStandard.DEFAULTS));
        assertEquals(List.of(new RaceRuleSet.Seen(WIND, 1, RaceRuleSet.WORLD, false)), rules.seen(RaceRules.find(WIND).orElseThrow()));
    }

    @Test
    void zoneValuesAreSavedWithTheZoneAndRaceLegalZonesAreMarked() {
        RaceRuleSet practice = inside(zone(3, false, Map.of("gravity_scale", 40)), RuleValues.DEFAULTS);
        assertEquals(List.of(new RaceRuleSet.Seen(GRAVITY, 40, 3, false)), practice.seen());
        assertFalse(practice.matches(RaceStandard.DEFAULTS));
        assertEquals(List.of(new AssistDetail(LapAssist.ZONE_PHYSICS, "gravity_scale", 40, 3)), practice.mismatches(RaceStandard.DEFAULTS));
        assertTrue(practice.matches(RaceStandard.DEFAULTS.with("gravity_scale", 40)));

        RaceRuleSet legal = inside(zone(4, true, Map.of("gravity_scale", 40)), RuleValues.DEFAULTS);
        assertEquals(List.of(new RaceRuleSet.Seen(GRAVITY, 40, 4, true)), legal.seen());
        assertTrue(legal.matches(RaceStandard.DEFAULTS));
        assertTrue(legal.matches(RaceStandard.DEFAULTS.with("gravity_scale", 70)));
        assertTrue(legal.mismatches(RaceStandard.DEFAULTS).isEmpty());

        RaceRuleSet neutral = inside(zone(5, false, Map.of("crash_disarm", 0)), RuleValues.DEFAULTS);
        assertTrue(neutral.isDefault());
    }

    @Test
    void windZeroInTheWorldOnlyCountsUnderAMatchingStandard() {
        RaceRuleSet calm = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0), Map.of()));
        RaceRuleSet breeze = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS, Map.of()));
        RaceStandard standard = RaceStandard.DEFAULTS.with("wind", 0);
        assertTrue(calm.matches(standard));
        assertFalse(breeze.matches(standard));
        assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, "wind", 1, 0)), breeze.mismatches(standard));
        assertFalse(calm.matches(RaceStandard.DEFAULTS));
        assertTrue(breeze.matches(RaceStandard.DEFAULTS));
    }

    @Test
    void setsAreNormalizedAndSurviveTheCodecs() {
        List<RaceRuleSet.Seen> seen = new ArrayList<>();
        seen.add(new RaceRuleSet.Seen(WIND, 3, RaceRuleSet.WORLD, false));
        seen.add(new RaceRuleSet.Seen(GRAVITY, 100, RaceRuleSet.WORLD, false));
        seen.add(new RaceRuleSet.Seen(GRAVITY, 40, 7, true));
        seen.add(new RaceRuleSet.Seen(WIND, 3, RaceRuleSet.WORLD, true));
        seen.add(new RaceRuleSet.Seen(WIND, 3, RaceRuleSet.WORLD, false));
        RaceRuleSet rules = RaceRuleSet.of(seen);
        assertEquals(List.of(new RaceRuleSet.Seen(GRAVITY, 100, RaceRuleSet.WORLD, false), new RaceRuleSet.Seen(GRAVITY, 40, 7, true),
                new RaceRuleSet.Seen(WIND, 3, RaceRuleSet.WORLD, false)), rules.seen());
        JsonElement json = RaceRuleSet.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow();
        assertEquals(rules, RaceRuleSet.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(RaceRuleSet.DEFAULTS, RaceRuleSet.of(List.of(new RaceRuleSet.Seen(WIND, 1, RaceRuleSet.WORLD, false))));
        List<RaceRuleSet.Seen> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            many.add(new RaceRuleSet.Seen("other:rule_" + i, i, RaceRuleSet.WORLD, false));
        }
        RaceRuleSet crowded = RaceRuleSet.of(many);
        assertEquals(RaceRuleSet.MAX_SEEN, crowded.seen().size());
        assertTrue(crowded.overflow());
        assertFalse(crowded.matches(RaceStandard.DEFAULTS));
        assertEquals(LapAssist.WORLD_PHYSICS.bit(), crowded.reasons(RaceStandard.DEFAULTS).mask());
        RaceRuleSet reread = RaceRuleSet.CODEC.parse(JsonOps.INSTANCE, RaceRuleSet.CODEC.encodeStart(JsonOps.INSTANCE, crowded).getOrThrow()).getOrThrow();
        assertTrue(reread.overflow());
        assertTrue(crowded.union(RaceRuleSet.DEFAULTS).overflow());
        assertFalse(rules.overflow());
        String longId = "other:" + "x".repeat(200);
        RaceRuleSet.Seen cut = new RaceRuleSet.Seen(longId, 5, RaceRuleSet.WORLD, false);
        assertEquals(RaceRuleSet.MAX_RULE, cut.rule().length());
        assertEquals(RaceRuleSet.key(longId), cut.rule());
        assertEquals(WIND, RaceRuleSet.key("Propwash:Wind"));
    }

    @Test
    void unionKeepsEveryValueThatWasInForceDuringTheLap() {
        RaceRuleSet calm = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0), Map.of()));
        RaceRuleSet both = calm.union(RaceRuleSet.DEFAULTS);
        assertEquals(List.of(new RaceRuleSet.Seen(WIND, 0, RaceRuleSet.WORLD, false), new RaceRuleSet.Seen(WIND, 1, RaceRuleSet.WORLD, false)),
                both.seen());
        assertFalse(both.matches(RaceStandard.DEFAULTS));
        assertFalse(both.matches(RaceStandard.DEFAULTS.with("wind", 0)));
        assertEquals(calm, calm.union(calm));
    }

    @Test
    void oldReasonsAreSplitIntoRulesAndFixedAids() {
        AssistReasons old = AssistReasons.of(List.of(AssistDetail.rule(WorldRuleSpecs.CRASH_DISARM, 0), AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 130),
                AssistDetail.zone(9, WorldRuleSpecs.WIND, 3), AssistDetail.hold(HoldMode.ALTHOLD)));
        RaceRuleSet.Split split = RaceRuleSet.split(old);
        assertEquals(LapAssist.HOLD_MODE.bit(), split.fixed().mask());
        assertEquals(List.of(new RaceRuleSet.Seen(GRAVITY, 130, RaceRuleSet.WORLD, false), new RaceRuleSet.Seen(WIND, 3, 9, false)),
                split.rules().seen());
        RaceRuleSet.Split disarmOnly = RaceRuleSet.split(AssistReasons.of(List.of(AssistDetail.rule(WorldRuleSpecs.CRASH_DISARM, 0))));
        assertTrue(disarmOnly.fixed().none());
        assertTrue(disarmOnly.rules().isDefault());
        RaceRuleSet.Split bare = RaceRuleSet.split(AssistReasons.of(LapAssist.WORLD_PHYSICS.bit()));
        assertEquals(LapAssist.WORLD_PHYSICS.bit(), bare.fixed().mask());
        assertTrue(bare.rules().isDefault());
    }

    @Test
    void lapVerdictUsesTheRulesInForceDuringTheLap() {
        AssistTimeline assist = new AssistTimeline();
        RuleTimeline rules = new RuleTimeline();
        RaceRuleSet disarmOff = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DISARM, 0), Map.of()));
        RaceRuleSet calm = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0), Map.of()));
        long t = 1_000L * MS;
        for (int i = 0; i <= 100; i++) {
            assist.observe(t + i * 50L * MS, AssistReasons.NONE);
            rules.observe(t + i * 50L * MS, disarmOff);
        }
        AssistCheck.LapVerdict clean = AssistCheck.lapVerdict(assist, rules, t, t + 5_000L * MS, AssistReasons.NONE, RaceRuleSet.DEFAULTS,
                AssistReasons.NONE, RaceStandard.DEFAULTS);
        assertTrue(clean.clean());
        assertTrue(clean.rules().isDefault());
        t += 10_000L * MS;
        for (int i = 0; i <= 100; i++) {
            assist.observe(t + i * 50L * MS, AssistReasons.NONE);
            rules.observe(t + i * 50L * MS, i < 50 ? calm : RaceRuleSet.DEFAULTS);
        }
        AssistCheck.LapVerdict mixed = AssistCheck.lapVerdict(assist, rules, t, t + 5_000L * MS, AssistReasons.NONE, RaceRuleSet.DEFAULTS,
                AssistReasons.NONE, RaceStandard.DEFAULTS.with("wind", 0));
        assertFalse(mixed.clean());
        assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, "wind", 1, 0)), mixed.reasons().details());
        assertTrue(mixed.fixed().none());
        t += 10_000L * MS;
        AssistCheck.LapVerdict fallback = AssistCheck.lapVerdict(new AssistTimeline(), new RuleTimeline(), t, t + 5_000L * MS, AssistReasons.NONE,
                calm, AssistReasons.of(List.of(AssistDetail.hold(HoldMode.POSHOLD))), RaceStandard.DEFAULTS.with("wind", 0));
        assertEquals(LapAssist.HOLD_MODE.bit(), fallback.reasons().mask());
        assertEquals(calm, fallback.rules());
    }

    @Test
    void timelineFoldsEvictedSpansInsteadOfForgettingThem() {
        RuleTimeline timeline = new RuleTimeline();
        RaceRuleSet calm = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0), Map.of()));
        RaceRuleSet heavy = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 150), Map.of()));
        long t = 0L;
        timeline.observe(t, heavy);
        for (int i = 1; i <= 3 * RuleTimeline.MAX_SPANS; i++) {
            timeline.observe(t + i * 100L * MS, i % 2 == 0 ? calm : RaceRuleSet.DEFAULTS);
        }
        assertTrue(timeline.spans() <= RuleTimeline.MAX_SPANS);
        RaceRuleSet lap = timeline.between(0L, 3L * RuleTimeline.MAX_SPANS * 100L * MS);
        assertFalse(lap.matches(RaceStandard.DEFAULTS.with("wind", 0)));
        assertTrue(lap.seen(RaceRules.find(GRAVITY).orElseThrow()).stream().anyMatch(seen -> seen.value() == 150), lap.toString());
    }

    @Test
    void timelineKeepsABoundedHistory() {
        RuleTimeline timeline = new RuleTimeline();
        RaceRuleSet calm = RaceRules.world(RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0), Map.of()));
        for (int i = 0; i < 500; i++) {
            timeline.observe(i * 1_000L * MS, i % 2 == 0 ? calm : RaceRuleSet.DEFAULTS);
        }
        assertTrue(timeline.spans() <= RuleTimeline.MAX_SPANS);
        timeline.forgetBefore(499_000L * MS);
        assertEquals(1, timeline.spans());
        timeline.clear();
        assertEquals(0, timeline.spans());
        assertFalse(timeline.covers(0L, 1L));
    }

    @Test
    void standardKeepsOnlyRelevantNonDefaultValidValues() {
        RaceStandard standard = new RaceStandard(Map.of("wind", 0, "propwash:gravity_scale", 100, "crash_disarm", 0, "battery_scale", 5000,
                "other:unknown", 4));
        assertEquals(Map.of(WIND, 0, "other:unknown", 4), standard.values());
        assertEquals(0, standard.value(RaceRules.find("wind").orElseThrow()));
        assertEquals(100, standard.value(RaceRules.find(GRAVITY).orElseThrow()));
        assertEquals(List.of(RaceRules.find(WIND).orElseThrow()), standard.differences());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        standard.write(buf);
        assertEquals(standard, RaceStandard.read(buf));
        JsonElement json = RaceStandard.CODEC.encodeStart(JsonOps.INSTANCE, standard).getOrThrow();
        assertEquals(standard, RaceStandard.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeVarInt(RaceStandard.MAX_ENTRIES + 1);
        assertThrows(IllegalArgumentException.class, () -> RaceStandard.read(bad));
    }

    @Test
    void adoptTakesTheCurrentRelevantValues() {
        Map<String, Integer> values = RaceRules.values(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0).with(WorldRuleSpecs.CRASH_DISARM, 0)
                .with(WorldRuleSpecs.GRAVITY_SCALE, 80), Map.of());
        RaceStandard adopted = RaceStandard.adopt(values);
        assertEquals(Map.of(WIND, 0, GRAVITY, 80), adopted.values());
        assertTrue(adopted.differences(values).isEmpty());
        assertEquals(List.of(RaceRules.find(GRAVITY).orElseThrow(), RaceRules.find(WIND).orElseThrow()),
                RaceStandard.DEFAULTS.differences(values).stream().sorted((a, b) -> a.id().compareTo(b.id())).toList());
        assertTrue(RaceRules.world(values).matches(adopted));
    }

    @Test
    void oldWorldsStartWithTheDefaultStandard() {
        RaceStandards empty = RaceStandards.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
        assertEquals(RaceStandard.DEFAULTS, empty.standard());
        assertEquals(0L, empty.changedAt());
        assertEquals("", empty.changedBy());
    }
}
