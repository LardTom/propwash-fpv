package de.lardtom.propwash.server.rules;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.SimTuning;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRuleSpecsTest {
    static final List<RuleSpec> PHYSICS = List.of(WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.PROP_WASH, WorldRuleSpecs.GROUND_EFFECT,
            WorldRuleSpecs.HARDWARE_LIMITS, WorldRuleSpecs.WIND, WorldRuleSpecs.TURBULENCE, WorldRuleSpecs.BATTERY_SCALE, WorldRuleSpecs.BATTERY_SAG,
            WorldRuleSpecs.CRASH_TOLERANCE, WorldRuleSpecs.CRASH_DISARM, WorldRuleSpecs.PROP_STRIKES);

    @Test
    void catalogHasTheRequiredRulesWithSaneDefaults() {
        Set<String> names = new HashSet<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            assertTrue(names.add(spec.name()), "doppelt: " + spec.name());
            assertTrue(spec.valid(spec.defaultValue()), spec.name());
            assertEquals("propwash:" + spec.name(), spec.id());
            assertEquals("gamerule.propwash." + spec.name(), spec.translationKey());
            assertEquals(spec, WorldRuleSpecs.byName(spec.name()).orElseThrow());
        }
        for (String required : List.of("gravity_scale", "battery_scale", "wind", "max_video_channels", "crash_damage", "infinite_range_analog",
                "infinite_range_digital")) {
            assertTrue(names.contains(required), "Pflicht-Regel fehlt: " + required);
        }
        assertEquals(100, WorldRuleSpecs.GRAVITY_SCALE.defaultValue());
        assertEquals(100, WorldRuleSpecs.BATTERY_SCALE.defaultValue());
        assertEquals(0, WorldRuleSpecs.BATTERY_SCALE.min());
        assertEquals(0, WorldRuleSpecs.WIND.min());
        assertEquals(3, WorldRuleSpecs.WIND.max());
        assertTrue(WorldRuleSpecs.CRASH_DAMAGE.defaultOn());
        assertFalse(WorldRuleSpecs.INFINITE_RANGE_ANALOG.defaultOn());
        assertFalse(WorldRuleSpecs.INFINITE_RANGE_DIGITAL.defaultOn());
        assertEquals(0, WorldRuleSpecs.MAX_VIDEO_CHANNELS.defaultValue());
        assertEquals(SimTuning.GRAVITY_MIN, WorldRuleSpecs.GRAVITY_SCALE.min() / 100.0, 0.0);
        assertEquals(SimTuning.GRAVITY_MAX, WorldRuleSpecs.GRAVITY_SCALE.max() / 100.0, 0.0);
    }

    @Test
    void specsRejectBrokenDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> RuleSpec.percent("Gross", 1, 0, 2, 1, false, RuleSpec.Group.PHYSICS));
        assertThrows(IllegalArgumentException.class, () -> RuleSpec.percent("outside", 5, 0, 2, 1, false, RuleSpec.Group.PHYSICS));
        assertThrows(IllegalArgumentException.class, () -> RuleSpec.percent("zero", 5, 1, 9, 1, true, RuleSpec.Group.PHYSICS));
        assertThrows(IllegalArgumentException.class, () -> new RuleSpec("flag", RuleSpec.Kind.BOOL, 1, 0, 2, RuleSpec.Group.PHYSICS,
                RuleSpec.Display.TOGGLE, 1, false));
    }

    @Test
    void valuesParseAndSerializeLikeTheGameruleCommand() {
        RuleSpec wind = WorldRuleSpecs.WIND;
        assertEquals("2", wind.serialize(2));
        assertEquals(2, wind.parse("2"));
        assertThrows(IllegalArgumentException.class, () -> wind.parse("4"));
        assertThrows(IllegalArgumentException.class, () -> wind.parse("stark"));
        RuleSpec damage = WorldRuleSpecs.CRASH_DAMAGE;
        assertEquals("false", damage.serialize(0));
        assertEquals("true", damage.serialize(1));
        assertEquals(1, damage.parse("true"));
        assertEquals(0, damage.parse("false"));
        assertThrows(IllegalArgumentException.class, () -> damage.parse("1"));
        assertTrue(WorldRuleSpecs.BATTERY_SCALE.labelled(0));
        assertFalse(WorldRuleSpecs.BATTERY_SCALE.labelled(100));
        assertTrue(WorldRuleSpecs.WIND.labelled(1));
    }

    @Test
    void ruleValuesFallBackToDefaultsAndClamp() {
        RuleValues values = new RuleValues(Map.of("gravity_scale", 5000, "wind", 2));
        assertEquals(100, values.get(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(2, values.get(WorldRuleSpecs.WIND));
        assertEquals(1000, RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 5000).get(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(0, RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, -1).get(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(1, RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 1).get(WorldRuleSpecs.GRAVITY_SCALE));
        assertEquals(999, RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 999).get(WorldRuleSpecs.GRAVITY_SCALE));
        assertTrue(RuleValues.DEFAULTS.isDefault(WorldRuleSpecs.WIND));
        assertEquals(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, 3), new RuleValues(Map.of("wind", 3)));
    }

    @Test
    void defaultRulesGiveExactlyTheDefaultTuning() throws IllegalAccessException {
        SimTuning fromRules = PhysicsRules.DEFAULT.toSimTuning();
        SimTuning plain = new SimTuning();
        int compared = 0;
        for (Field field : SimTuning.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Object a = field.get(fromRules);
            Object b = field.get(plain);
            if (a instanceof Double da && b instanceof Double db) {
                assertEquals(Double.doubleToLongBits(db), Double.doubleToLongBits(da), field.getName());
            } else {
                assertEquals(b, a, field.getName());
            }
            compared++;
        }
        assertTrue(compared >= 20, "zu wenige Felder verglichen: " + compared);
        assertFalse(PhysicsRules.DEFAULT.modified());
        assertEquals(PhysicsRules.DEFAULT, PhysicsRules.of(RuleValues.DEFAULTS));
    }

    @Test
    void everyPhysicsRuleCountsAsModifiedPhysicsOthersDoNot() {
        for (RuleSpec spec : PHYSICS) {
            int other = spec.defaultValue() == spec.max() ? spec.max() - 1 : spec.defaultValue() + 1;
            assertTrue(PhysicsRules.of(RuleValues.DEFAULTS.with(spec, other)).modified(), spec.name());
        }
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            if (PHYSICS.contains(spec)) {
                continue;
            }
            int other = spec.defaultValue() == spec.max() ? spec.min() : spec.max();
            assertFalse(PhysicsRules.of(RuleValues.DEFAULTS.with(spec, other)).modified(), spec.name());
        }
    }

    @Test
    void physicsRulesMapOntoTheFlightModel() {
        RuleValues values = RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 120).with(WorldRuleSpecs.BATTERY_SCALE, 250)
                .with(WorldRuleSpecs.WIND, 3).with(WorldRuleSpecs.CRASH_TOLERANCE, 150).with(WorldRuleSpecs.HARDWARE_LIMITS, 0)
                .with(WorldRuleSpecs.TURBULENCE, 0);
        SimTuning t = PhysicsRules.of(values).toSimTuning();
        assertEquals(1.2, t.gravityScale, 0.0);
        assertEquals(2.5, t.batteryScale, 0.0);
        assertFalse(t.infiniteBattery);
        assertEquals(3.0, t.windScale, 0.0);
        assertEquals(0.0, t.turbulenceScale, 0.0);
        assertEquals(1.5, t.crashSpeedScale, 0.0);
        assertFalse(t.thermalLimits);
        assertFalse(t.escOvervoltage);
        for (int level = 0; level <= 3; level++) {
            assertEquals(level, PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.WIND, level)).toSimTuning().windScale, 0.0);
        }
        SimTuning infinite = PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.BATTERY_SCALE, 0)).toSimTuning();
        assertTrue(infinite.infiniteBattery);
        assertEquals(1.0, infinite.batteryScale, 0.0);
    }

    @Test
    void effectsForDamageRangeAndChannels() {
        assertEquals(1.0F, RuleEffects.crashDamageStrength(RuleValues.DEFAULTS), 0.0F);
        assertEquals(0.0F, RuleEffects.crashDamageStrength(RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DAMAGE, 0)), 0.0F);
        assertEquals(3.0F, RuleEffects.crashDamageStrength(RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DAMAGE_SCALE, 300)), 0.0F);
        assertEquals(0.25F, RuleEffects.crashDamageStrength(RuleValues.DEFAULTS.with(WorldRuleSpecs.CRASH_DAMAGE_SCALE, 25)), 0.0F);
        RuleValues analog = RuleValues.DEFAULTS.with(WorldRuleSpecs.INFINITE_RANGE_ANALOG, 1);
        assertTrue(RuleEffects.infiniteRange(analog, VideoSystem.ANALOG));
        assertTrue(RuleEffects.infiniteRange(analog, VideoSystem.CREATIVE));
        assertFalse(RuleEffects.infiniteRange(analog, VideoSystem.DIGITAL));
        RuleValues digital = RuleValues.DEFAULTS.with(WorldRuleSpecs.INFINITE_RANGE_DIGITAL, 1);
        assertTrue(RuleEffects.infiniteRange(digital, VideoSystem.DIGITAL));
        assertFalse(RuleEffects.infiniteRange(digital, VideoSystem.ANALOG));
        assertFalse(RuleEffects.infiniteRange(RuleValues.DEFAULTS, VideoSystem.ANALOG));
        assertFalse(RuleEffects.channelsFull(RuleValues.DEFAULTS, 500));
        RuleValues four = RuleValues.DEFAULTS.with(WorldRuleSpecs.MAX_VIDEO_CHANNELS, 4);
        assertFalse(RuleEffects.channelsFull(four, 3));
        assertTrue(RuleEffects.channelsFull(four, 4));
        assertTrue(RuleEffects.channelsFull(four, 9));
    }
}
