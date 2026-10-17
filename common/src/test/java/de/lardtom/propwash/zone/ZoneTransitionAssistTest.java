package de.lardtom.propwash.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.race.AssistCheck;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTransitionAssistTest {
    private static final String DIM = Zone.OVERWORLD;

    private static Zone zone(int id, boolean raceLegal, int tenths, Map<String, Integer> overrides, int x0, int x1, int priority) {
        return new Zone(id, DIM, "z" + id, ZoneShape.BOX, new ZoneBox(x0, 0, 0, x1, 10, 10), priority, raceLegal, tenths, overrides);
    }

    private static AssistReasons verdict(ZoneField field) {
        return AssistCheck.physics(field.resolution(), field.blendDetails(), false);
    }

    @Test
    void leavingAPracticeZoneCountsUntilItsGravityHasFadedOut() {
        Zone practice = zone(1, false, 50, Map.of("gravity_scale", 0), 0, 10, 0);
        ZoneField field = new ZoneField();
        field.update(List.of(practice), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        assertTrue(field.assisted());
        field.update(List.of(practice), DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.05);
        assertTrue(field.gravityPercent() < 100.0);
        assertFalse(field.resolution().assisted(), "nach der Position allein sauber");
        assertTrue(field.assisted(), "die Schwerkraft der Zone wirkt noch nach");
        AssistReasons reasons = verdict(field);
        assertTrue(LapAssist.ZONE_PHYSICS.in(reasons.mask()));
        assertEquals(List.of(AssistDetail.zone(1, WorldRuleSpecs.GRAVITY_SCALE, 0)), reasons.details());
        for (int i = 0; i < 100; i++) {
            field.update(List.of(practice), DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.05);
        }
        assertEquals(100.0, field.gravityPercent());
        assertFalse(field.assisted());
        assertTrue(verdict(field).none());
    }

    @Test
    void deletedZoneCountsWhileItFades() {
        Zone practice = zone(1, false, 20, Map.of("wind", 3), 0, 10, 0);
        ZoneField field = new ZoneField();
        field.update(List.of(practice), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        field.update(List.of(), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.5);
        assertTrue(field.assisted());
        assertEquals(List.of(AssistDetail.zone(1, WorldRuleSpecs.WIND, 3)), verdict(field).details());
        field.update(List.of(), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 2.0);
        assertFalse(field.assisted());
    }

    @Test
    void enteringARaceLegalZoneFromAPracticeZoneCountsDuringTheBlend() {
        Zone practice = zone(1, false, 30, Map.of("gravity_scale", 0), 0, 100, 0);
        Zone legal = zone(2, true, 30, Map.of("gravity_scale", 100), 40, 60, 5);
        ZoneField field = new ZoneField();
        List<Zone> zones = List.of(practice, legal);
        field.update(zones, DIM, PhysicsRules.DEFAULT, 20, 5, 5, 0.0);
        field.update(zones, DIM, PhysicsRules.DEFAULT, 50, 5, 5, 0.05);
        assertFalse(field.resolution().assisted());
        assertTrue(field.gravityPercent() < 10.0);
        assertTrue(field.assisted());
        assertEquals(List.of(AssistDetail.zone(1, WorldRuleSpecs.GRAVITY_SCALE, 0)), verdict(field).details());
        for (int i = 0; i < 80; i++) {
            field.update(zones, DIM, PhysicsRules.DEFAULT, 50, 5, 5, 0.05);
        }
        assertFalse(field.assisted(), "voll in der rennfreien Zone ist die Runde sauber");
    }

    @Test
    void enteringARaceLegalZoneInAModifiedWorldCountsTheWorldUntilTheZoneIsFull() {
        PhysicsRules world = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 0));
        Zone legal = zone(1, true, 10, Map.of("gravity_scale", 100), 0, 10, 0);
        ZoneField field = new ZoneField();
        field.update(List.of(legal), DIM, world, -5, 5, 5, 0.0);
        field.update(List.of(legal), DIM, world, 5, 5, 5, 0.1);
        assertTrue(field.assisted());
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.GRAVITY_SCALE, 0)), verdict(field).details());
        for (int i = 0; i < 20; i++) {
            field.update(List.of(legal), DIM, world, 5, 5, 5, 0.1);
        }
        assertFalse(field.assisted());
    }

    @Test
    void raceLegalZonesNeverCountEvenWhileFading() {
        Zone legal = zone(1, true, 50, Map.of("gravity_scale", 130, "wind", 3), 0, 10, 0);
        ZoneField field = new ZoneField();
        field.update(List.of(legal), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        for (int i = 0; i < 120; i++) {
            field.update(List.of(legal), DIM, PhysicsRules.DEFAULT, i % 2 == 0 ? -5 : 5, 5, 5, 0.05);
            assertFalse(field.assisted());
        }
    }

    @Test
    void savedZonesAreCappedPerDimension() {
        JsonArray list = new JsonArray();
        for (int i = 1; i <= ZoneLimits.MAX_ZONES + 5; i++) {
            list.add(Zone.CODEC.encodeStart(JsonOps.INSTANCE, zone(i, false, 5, Map.of(), 0, 10, 0)).getOrThrow());
        }
        Zone nether = new Zone(500, "minecraft:the_nether", "n", ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, false, 5, Map.of());
        list.add(Zone.CODEC.encodeStart(JsonOps.INSTANCE, nether).getOrThrow());
        JsonObject root = new JsonObject();
        root.addProperty("next_id", 1);
        root.add("zones", list);
        ZoneData data = ZoneData.TYPE.codec().parse(JsonOps.INSTANCE, (JsonElement) root).getOrThrow();
        assertEquals(ZoneLimits.MAX_ZONES, data.in(DIM).size());
        assertEquals(1, data.in("minecraft:the_nether").size());
        assertEquals(1, data.in(DIM).getFirst().id());
    }
}
