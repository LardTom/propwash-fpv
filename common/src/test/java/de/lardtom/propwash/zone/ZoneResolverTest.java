package de.lardtom.propwash.zone;

import de.lardtom.propwash.race.AssistCheck;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.AssistWords;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneResolverTest {
    private static final PhysicsRules WORLD = PhysicsRules.DEFAULT;

    private static Zone zone(int id, int priority, double min, double max, Map<String, Integer> overrides) {
        return new Zone(id, Zone.OVERWORLD, "z" + id, ZoneShape.BOX, new ZoneBox(min, 0, min, max, 20, max), priority, false, 5, overrides);
    }

    @Test
    void withoutZonesTheWorldRulesApply() {
        ZoneResolution here = ZoneResolver.resolve(List.of(), Zone.OVERWORLD, WORLD, 1, 1, 1);
        assertSame(WORLD, here.rules());
        assertFalse(here.inZone());
        assertFalse(here.assisted());
        PhysicsRules windy = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 3));
        ZoneResolution world = ZoneResolver.resolve(List.of(zone(1, 0, 50, 60, Map.of("wind", 0))), Zone.OVERWORLD, windy, 1, 1, 1);
        assertTrue(world.assisted());
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.WIND, 3)), world.worldDetails());
    }

    @Test
    void higherPriorityWinsPerSettingAndLowerZonesFillTheRest() {
        Zone course = zone(1, 0, 0, 100, Map.of("battery_scale", 0, "gravity_scale", 50));
        Zone passage = zone(2, 5, 10, 20, Map.of("gravity_scale", 0));
        ZoneResolution inside = ZoneResolver.resolve(List.of(course, passage), Zone.OVERWORLD, WORLD, 15, 5, 15);
        assertEquals(0, inside.rules().gravityPercent());
        assertTrue(inside.rules().infiniteBattery());
        assertEquals(passage, inside.source(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(course, inside.source(WorldRuleSpecs.BATTERY_SCALE));
        assertNull(inside.source(WorldRuleSpecs.WIND));
        assertEquals(List.of(passage, course), inside.inside());
        ZoneResolution outside = ZoneResolver.resolve(List.of(course, passage), Zone.OVERWORLD, WORLD, 50, 5, 50);
        assertEquals(50, outside.rules().gravityPercent());
    }

    @Test
    void equalPriorityPrefersTheNewerZone() {
        Zone older = zone(3, 1, 0, 10, Map.of("wind", 0));
        Zone newer = zone(4, 1, 0, 10, Map.of("wind", 3));
        assertEquals(3, ZoneResolver.resolve(List.of(newer, older), Zone.OVERWORLD, WORLD, 5, 5, 5).rules().wind());
    }

    @Test
    void otherDimensionsAreIgnored() {
        Zone nether = zone(1, 0, 0, 10, Map.of("gravity_scale", 0)).withDimension("minecraft:the_nether");
        assertEquals(100, ZoneResolver.resolve(List.of(nether), Zone.OVERWORLD, WORLD, 5, 5, 5).rules().gravityPercent());
        assertEquals(0, ZoneResolver.resolve(List.of(nether), "minecraft:the_nether", WORLD, 5, 5, 5).rules().gravityPercent());
    }

    @Test
    void raceLegalZonesAreNotAssistedButTheRestOfTheWorldStillIs() {
        PhysicsRules world = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 130).with(WorldRuleSpecs.CRASH_TOLERANCE, 200));
        Zone legal = zone(1, 0, 0, 10, Map.of("gravity_scale", 0)).withRaceLegal(true);
        ZoneResolution here = ZoneResolver.resolve(List.of(legal), Zone.OVERWORLD, world, 5, 5, 5);
        assertTrue(here.zoneDetails().isEmpty());
        assertEquals(List.of(AssistDetail.rule(WorldRuleSpecs.CRASH_TOLERANCE, 200)), here.worldDetails());
        assertEquals(100, here.worldPart().gravityPercent());
        assertEquals(200, here.worldPart().crashTolerancePercent());
        ZoneResolution clean = ZoneResolver.resolve(List.of(legal), Zone.OVERWORLD, WORLD, 5, 5, 5);
        assertFalse(clean.assisted());
        assertTrue(AssistCheck.physics(clean, false).none());
    }

    @Test
    void assistedZonesNameTheZoneAndTheRule() {
        Zone training = zone(12, 0, 0, 10, Map.of("battery_scale", 0, "wind", 1));
        ZoneResolution here = ZoneResolver.resolve(List.of(training), Zone.OVERWORLD, WORLD, 5, 5, 5);
        assertTrue(here.assisted());
        assertEquals(List.of(AssistDetail.zone(12, WorldRuleSpecs.BATTERY_SCALE, 0)), here.zoneDetails(), "Standardwerte zählen nicht");
        AssistReasons reasons = AssistCheck.physics(here, true);
        assertTrue(reasons.has(LapAssist.ZONE_PHYSICS));
        assertFalse(reasons.has(LapAssist.WORLD_PHYSICS), "Meldung des Clients wird durch die Zone erklärt");
        assertTrue(LapAssist.ZONE_PHYSICS.serverSide());
        AssistReasons two = AssistReasons.of(List.of(AssistDetail.zone(1, WorldRuleSpecs.WIND, 0), AssistDetail.zone(2, WorldRuleSpecs.WIND, 3)));
        assertEquals(2, two.details().size(), "gleiche Regel in zwei Zonen bleibt getrennt");
        ZoneNames.client(id -> id == 12 ? "Übungsplatz" : null);
        try {
            Component text = AssistWords.describe(reasons);
            TranslatableContents outer = (TranslatableContents) text.getSiblings().getFirst().getContents();
            assertEquals("propwash.race.assist.zone_physics.values", outer.getKey());
            assertEquals("Übungsplatz", ((Component) outer.getArgs()[0]).getString());
            Component unknown = AssistWords.zoneName(99);
            assertEquals("propwash.race.assist.zone_physics.unknown", ((TranslatableContents) unknown.getContents()).getKey());
        } finally {
            ZoneNames.client(null);
        }
    }

    @Test
    void clientClaimWithoutAnyReasonStaysVisible() {
        ZoneResolution clean = ZoneResolver.resolve(List.of(), Zone.OVERWORLD, WORLD, 5, 5, 5);
        assertEquals(List.of(AssistDetail.clientPhysics()), AssistCheck.physics(clean, true).details());
    }

    @Test
    void setupWithZonesMatchesTheServerView() {
        Zone training = zone(12, 0, 0, 10, Map.of("battery_scale", 0));
        PhysicsRules world = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 0));
        ZoneResolution here = ZoneResolver.resolve(List.of(training), Zone.OVERWORLD, world, 5, 5, 5);
        AssistReasons client = AssistCheck.evaluate(AssistCheck.Setup.clean().withZones(here, here.assisted()));
        assertEquals(AssistCheck.physics(here, here.assisted()), client);
    }
}
