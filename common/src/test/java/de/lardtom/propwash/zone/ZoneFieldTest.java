package de.lardtom.propwash.zone;

import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.SimTuning;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneFieldTest {
    private static final String DIM = Zone.OVERWORLD;

    private static Zone zeroG(int id, int tenths) {
        return new Zone(id, DIM, "zero" + id, ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, true, tenths,
                Map.of("gravity_scale", 0, "wind", 0, "prop_wash", 0));
    }

    private static void assertSameTuning(SimTuning a, SimTuning b) {
        assertEquals(a.gravityScale, b.gravityScale);
        assertEquals(a.windScale, b.windScale);
        assertEquals(a.batteryScale, b.batteryScale);
        assertEquals(a.infiniteBattery, b.infiniteBattery);
        assertEquals(a.propWash, b.propWash);
        assertEquals(a.crashSpeedScale, b.crashSpeedScale);
        assertEquals(a.turbulenceScale, b.turbulenceScale);
    }

    @Test
    void withoutZonesTheTuningIsExactlyTheWorldTuning() {
        for (PhysicsRules world : List.of(PhysicsRules.DEFAULT, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 137)
                .with(WorldRuleSpecs.WIND, 3).with(WorldRuleSpecs.BATTERY_SCALE, 0)))) {
            ZoneField field = new ZoneField();
            assertTrue(field.update(List.of(), DIM, world, 0, 0, 0, 0.0));
            assertFalse(field.update(List.of(), DIM, world, 5, 5, 5, 0.016), "ohne Zonen ändert sich nichts");
            assertSameTuning(world.toSimTuning(), field.apply(world.toSimTuning()));
        }
    }

    @Test
    void sessionStartInsideAZoneSnapsWithoutBlend() {
        ZoneField field = new ZoneField();
        field.update(List.of(zeroG(1, 10)), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        assertEquals(0.0, field.gravityPercent());
        assertEquals(0.0, field.wind());
        assertFalse(field.rules().propWash());
        assertFalse(field.blending());
    }

    @Test
    void gravityAndWindBlendOverTheTransitionWhileSwitchesChangeAtTheBorder() {
        ZoneField field = new ZoneField();
        List<Zone> zones = List.of(zeroG(1, 10));
        field.update(zones, DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.0);
        assertTrue(field.update(zones, DIM, PhysicsRules.DEFAULT, 1, 5, 5, 0.25));
        assertFalse(field.rules().propWash(), "Schalter gilt sofort ab dem Rand");
        assertEquals(75.0, field.gravityPercent(), 1.0E-9);
        assertEquals(0.75, field.wind(), 1.0E-9);
        assertTrue(field.blending());
        field.update(zones, DIM, PhysicsRules.DEFAULT, 2, 5, 5, 0.5);
        assertEquals(25.0, field.gravityPercent(), 1.0E-9);
        field.update(zones, DIM, PhysicsRules.DEFAULT, 3, 5, 5, 0.5);
        assertEquals(0.0, field.gravityPercent());
        assertFalse(field.blending());
        field.update(zones, DIM, PhysicsRules.DEFAULT, 20, 5, 5, 0.4);
        assertTrue(field.rules().propWash());
        assertEquals(40.0, field.gravityPercent(), 1.0E-9, "beim Verlassen blendet die Schwerkraft zurück");
        field.update(zones, DIM, PhysicsRules.DEFAULT, 20, 5, 5, 1.0);
        assertEquals(100.0, field.gravityPercent());
        assertFalse(field.update(zones, DIM, PhysicsRules.DEFAULT, 21, 5, 5, 0.1));
    }

    @Test
    void zeroTransitionSwitchesInstantly() {
        ZoneField field = new ZoneField();
        List<Zone> zones = List.of(zeroG(1, 0));
        field.update(zones, DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.0);
        field.update(zones, DIM, PhysicsRules.DEFAULT, 1, 5, 5, 0.001);
        assertEquals(0.0, field.gravityPercent());
    }

    @Test
    void deletedZoneFadesOutInsteadOfJumping() {
        ZoneField field = new ZoneField();
        field.update(List.of(zeroG(1, 10)), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        field.update(List.of(), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.5);
        assertEquals(50.0, field.gravityPercent(), 1.0E-9);
        assertTrue(field.rules().propWash());
    }

    @Test
    void editingAZoneWhileInsideBlendsInsteadOfJumping() {
        ZoneField field = new ZoneField();
        Zone zone = zeroG(1, 10);
        field.update(List.of(zone), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.0);
        Zone edited = zone.without(WorldRuleSpecs.GRAVITY_SCALE);
        field.update(List.of(edited), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.5);
        assertEquals(50.0, field.gravityPercent(), 1.0E-9);
        assertTrue(field.blending());
        field.update(List.of(edited), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.6);
        assertEquals(100.0, field.gravityPercent());
        Zone half = edited.withOverride(WorldRuleSpecs.GRAVITY_SCALE, 50);
        field.update(List.of(half), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.25);
        double mid = field.gravityPercent();
        assertTrue(mid > 50.0 && mid < 100.0, "Zwischenwert " + mid);
        field.update(List.of(half), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 1.0);
        assertEquals(50.0, field.gravityPercent());
        assertFalse(field.blending());
        Zone moved = half.withBox(half.box().moved(1, 0, 0));
        assertFalse(field.update(List.of(moved), DIM, PhysicsRules.DEFAULT, 5, 5, 5, 0.1), "Verschieben ohne neue Werte ändert nichts");
    }

    @Test
    void overlappingZonesBlendInPriorityOrder() {
        Zone course = new Zone(1, DIM, "kurs", ZoneShape.BOX, new ZoneBox(-50, 0, -50, 50, 10, 50), 0, true, 0, Map.of("gravity_scale", 50));
        Zone passage = new Zone(2, DIM, "tunnel", ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 3, true, 10, Map.of("gravity_scale", 0));
        List<Zone> zones = new ArrayList<>(List.of(passage, course));
        ZoneField field = new ZoneField();
        field.update(zones, DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.0);
        assertEquals(50.0, field.gravityPercent());
        field.update(zones, DIM, PhysicsRules.DEFAULT, 1, 5, 5, 0.5);
        assertEquals(25.0, field.gravityPercent(), 1.0E-9);
        assertEquals(0, field.rules().gravityPercent());
        assertFalse(field.assisted());
    }

    @Test
    void appliedTuningUsesTheBlendedValues() {
        ZoneField field = new ZoneField();
        List<Zone> zones = List.of(zeroG(1, 10));
        field.update(zones, DIM, PhysicsRules.DEFAULT, -5, 5, 5, 0.0);
        field.update(zones, DIM, PhysicsRules.DEFAULT, 1, 5, 5, 0.5);
        SimTuning tuning = field.apply(field.rules().toSimTuning());
        assertEquals(0.5, tuning.gravityScale, 1.0E-9);
        assertEquals(0.5, tuning.windScale, 1.0E-9);
        assertFalse(tuning.propWash);
    }
}
