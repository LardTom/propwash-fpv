package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneDraftTest {
    private static final PhysicsRules WORLD = PhysicsRules.DEFAULT;

    private static ZoneDraft.Row rule(de.lardtom.propwash.server.rules.RuleSpec spec) {
        return new ZoneDraft.Row(ZoneDraft.Kind.RULE, spec);
    }

    @Test
    void freshZoneSurroundsThePlayerOnBlockEdges() {
        Zone zone = ZoneDraft.fresh(Zone.OVERWORLD, "Zone 1", 10.7, 64.2, -3.4);
        assertEquals(new ZoneBox(2, 64, -12, 18, 72, 4), zone.box());
        assertTrue(new ZoneDraft(zone).isNew());
        assertTrue(new ZoneDraft(zone).changed());
    }

    @Test
    void sizeGrowsAroundTheCenterAndPositionMovesByBlocks() {
        ZoneDraft draft = new ZoneDraft(ZoneDraft.fresh(Zone.OVERWORLD, "a", 0.5, 64, 0.5));
        double center = draft.zone().box().centerX();
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.WIDTH), 1, false, WORLD);
        assertEquals(18.0, draft.zone().box().sizeX());
        assertEquals(center, draft.zone().box().centerX());
        assertEquals(Math.floor(draft.zone().box().minX()), draft.zone().box().minX(), "Kanten bleiben auf Blockgrenzen");
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.X), -3, false, WORLD);
        assertEquals(center - 3.0, draft.zone().box().centerX());
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.HEIGHT), -100, false, WORLD);
        assertEquals(ZoneLimits.MIN_SIZE, draft.zone().box().sizeY());
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.SHAPE), 1, false, WORLD);
        assertEquals(ZoneShape.CYLINDER, draft.zone().shape());
        assertFalse(draft.rows().contains(ZoneDraft.Row.of(ZoneDraft.Kind.DEPTH)), "Zylinder ohne eigene Tiefe");
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.WIDTH), 1, true, WORLD);
        assertEquals(draft.zone().box().sizeX(), draft.zone().box().sizeZ());
    }

    @Test
    void rulesStartFromTheWorldValueAndResetToIt() {
        PhysicsRules world = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 80));
        ZoneDraft draft = new ZoneDraft(ZoneDraft.fresh(Zone.OVERWORLD, "a", 0, 64, 0));
        draft.change(rule(WorldRuleSpecs.GRAVITY_SCALE), -1, false, world);
        assertEquals(70, draft.zone().value(WorldRuleSpecs.GRAVITY_SCALE, -1));
        draft.change(rule(WorldRuleSpecs.GRAVITY_SCALE), -1, true, world);
        assertEquals(69, draft.zone().value(WorldRuleSpecs.GRAVITY_SCALE, -1));
        draft.change(rule(WorldRuleSpecs.GRAVITY_SCALE), -100, false, world);
        assertEquals(0, draft.zone().value(WorldRuleSpecs.GRAVITY_SCALE, -1));
        draft.reset(rule(WorldRuleSpecs.GRAVITY_SCALE));
        assertFalse(draft.zone().overrides(WorldRuleSpecs.GRAVITY_SCALE));
        draft.change(rule(WorldRuleSpecs.BATTERY_SCALE), -1, false, world);
        draft.change(rule(WorldRuleSpecs.BATTERY_SCALE), -9, false, world);
        assertTrue(draft.zone().overrides(WorldRuleSpecs.BATTERY_SCALE));
        assertEquals(0, draft.zone().value(WorldRuleSpecs.BATTERY_SCALE, -1), "0 = unendlicher Akku");
        PhysicsRules max = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 3));
        draft.change(rule(WorldRuleSpecs.WIND), 1, false, max);
        assertEquals(2, draft.zone().value(WorldRuleSpecs.WIND, -1), "am Rand geht der erste Schritt in die andere Richtung");
    }

    @Test
    void switchesCycleThroughWorldOnAndOff() {
        ZoneDraft draft = new ZoneDraft(ZoneDraft.fresh(Zone.OVERWORLD, "a", 0, 64, 0));
        ZoneDraft.Row wash = rule(WorldRuleSpecs.PROP_WASH);
        draft.change(wash, 1, false, WORLD);
        assertEquals(1, draft.zone().value(WorldRuleSpecs.PROP_WASH, -1));
        draft.change(wash, 1, false, WORLD);
        assertEquals(0, draft.zone().value(WorldRuleSpecs.PROP_WASH, -1));
        draft.change(wash, 1, false, WORLD);
        assertFalse(draft.zone().overrides(WorldRuleSpecs.PROP_WASH));
        draft.change(wash, -1, false, WORLD);
        assertEquals(0, draft.zone().value(WorldRuleSpecs.PROP_WASH, -1));
    }

    @Test
    void selectionWrapsAndExistingZonesTrackChanges() {
        Zone stored = ZoneDraft.fresh(Zone.OVERWORLD, "a", 0, 64, 0).withId(4);
        ZoneDraft draft = new ZoneDraft(stored);
        assertFalse(draft.changed());
        draft.moveSelection(-1);
        assertEquals(draft.rows().getLast(), draft.selected());
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.PRIORITY), 3, false, WORLD);
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.RACE_LEGAL), 1, false, WORLD);
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.TRANSITION), -10, false, WORLD);
        assertEquals(3, draft.zone().priority());
        assertTrue(draft.zone().raceLegal());
        assertEquals(0, draft.zone().transitionTenths());
        assertTrue(draft.changed());
        draft.change(ZoneDraft.Row.of(ZoneDraft.Kind.Y), 2, false, WORLD);
        draft.reset(ZoneDraft.Row.of(ZoneDraft.Kind.Y));
        assertEquals(stored.box(), draft.zone().box());
        draft.centerOn(100.4, 70.9, -50.2);
        assertEquals(100.0, draft.zone().box().centerX());
        assertEquals(70.0, draft.zone().box().minY());
    }
}
