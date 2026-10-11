package de.lardtom.propwash.server.rules;

import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.sim.SimTuning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulePresetTest {
    private static final List<RuleSpec> UNTOUCHED = List.of(WorldRuleSpecs.GRAVITY_SCALE, WorldRuleSpecs.GROUND_EFFECT,
            WorldRuleSpecs.CRASH_DAMAGE_SCALE, WorldRuleSpecs.MAX_VIDEO_CHANNELS, WorldRuleSpecs.ALLOW_3D_MODE, WorldRuleSpecs.AUTONOMOUS_RESCUE,
            WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE, WorldRuleSpecs.DRONES_GENERATE_CHUNKS);

    @Test
    void realisticIsExactlyTheDefaults() {
        RulePreset realistic = RulePreset.REALISTIC;
        assertTrue(realistic.differences().isEmpty());
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            assertEquals(spec.defaultValue(), realistic.value(spec), spec.name());
        }
        assertEquals(PhysicsRules.DEFAULT, PhysicsRules.of(realistic.ruleValues()));
        assertFalse(PhysicsRules.of(realistic.ruleValues()).modified());
        assertEquals(Optional.of(RulePreset.REALISTIC), RulePreset.match(RuleValues.DEFAULTS));
    }

    @Test
    void trainingTakesAwayTheConsequencesButKeepsTheHandling() {
        RuleValues training = RulePreset.TRAINING.ruleValues();
        assertFalse(RuleEffects.crashDamage(training));
        assertEquals(0.0F, RuleEffects.crashDamageStrength(training));
        assertFalse(training.on(WorldRuleSpecs.PROP_STRIKES));
        assertEquals(0, training.get(WorldRuleSpecs.BATTERY_SCALE));
        assertFalse(training.on(WorldRuleSpecs.BATTERY_SAG));
        assertFalse(training.on(WorldRuleSpecs.HARDWARE_LIMITS));
        assertEquals(WorldRuleSpecs.WIND.defaultValue(), training.get(WorldRuleSpecs.WIND));
        assertTrue(training.on(WorldRuleSpecs.TURBULENCE));
        assertTrue(training.on(WorldRuleSpecs.PROP_WASH));
        assertTrue(training.on(WorldRuleSpecs.CRASH_DISARM));
        assertEquals(100, training.get(WorldRuleSpecs.CRASH_TOLERANCE));
        assertEquals(PartCompatibility.ENFORCED, RuleEffects.partCompatibility(training));
        assertFalse(RuleEffects.infiniteRange(training, VideoSystem.ANALOG));
        assertTrue(training.on(WorldRuleSpecs.DRONE_HITS_ENTITIES));
        assertTrue(training.on(WorldRuleSpecs.SAVE_ASSISTED_LAPS));
        assertFalse(RulePreset.REALISTIC.ruleValues().on(WorldRuleSpecs.SAVE_ASSISTED_LAPS));
        assertTrue(training.on(WorldRuleSpecs.DRONE_TRAILS));
        assertFalse(RulePreset.REALISTIC.ruleValues().on(WorldRuleSpecs.DRONE_TRAILS));
        assertFalse(PhysicsRules.of(RuleValues.DEFAULTS.with(WorldRuleSpecs.DRONE_TRAILS, 1)).modified());
        SimTuning tuning = PhysicsRules.of(training).toSimTuning();
        SimTuning defaults = new SimTuning();
        assertTrue(tuning.infiniteBattery);
        assertFalse(tuning.batterySag);
        assertFalse(tuning.propStrikes);
        assertFalse(tuning.thermalLimits);
        assertEquals(defaults.windScale, tuning.windScale);
        assertEquals(defaults.turbulenceScale, tuning.turbulenceScale);
        assertEquals(defaults.gravityScale, tuning.gravityScale);
        assertEquals(defaults.crashSpeedScale, tuning.crashSpeedScale);
        assertEquals(defaults.propWash, tuning.propWash);
        assertEquals(defaults.groundEffect, tuning.groundEffect);
        assertTrue(PhysicsRules.of(training).modified());
    }

    @Test
    void arcadeIsTrainingPlusFreedomCalmAirAndForgivingCrashes() {
        for (RuleSpec spec : RulePreset.TRAINING.differences()) {
            assertEquals(RulePreset.TRAINING.value(spec), RulePreset.ARCADE.value(spec), spec.name());
        }
        RuleValues arcade = RulePreset.ARCADE.ruleValues();
        assertEquals(PartCompatibility.LIFTED, RuleEffects.partCompatibility(arcade));
        assertEquals(0, arcade.get(WorldRuleSpecs.WIND));
        assertFalse(arcade.on(WorldRuleSpecs.TURBULENCE));
        assertFalse(arcade.on(WorldRuleSpecs.PROP_WASH));
        assertTrue(arcade.get(WorldRuleSpecs.CRASH_TOLERANCE) >= 2 * WorldRuleSpecs.CRASH_TOLERANCE.defaultValue());
        assertFalse(arcade.on(WorldRuleSpecs.CRASH_DISARM));
        for (VideoSystem system : VideoSystem.values()) {
            assertTrue(RuleEffects.infiniteRange(arcade, system), system.name());
        }
        assertFalse(arcade.on(WorldRuleSpecs.DRONE_HITS_ENTITIES));
        assertTrue(arcade.on(WorldRuleSpecs.DRONE_TRAILS));
        SimTuning tuning = PhysicsRules.of(arcade).toSimTuning();
        assertEquals(0.0, tuning.windScale);
        assertEquals(0.0, tuning.turbulenceScale);
        assertFalse(tuning.propWash);
        assertFalse(tuning.crashDisarm);
        assertEquals(3.0, tuning.crashSpeedScale, 1.0E-9);
        assertTrue(RulePreset.ARCADE.differences().containsAll(RulePreset.TRAINING.differences()));
        assertTrue(RulePreset.ARCADE.differences().size() > RulePreset.TRAINING.differences().size());
    }

    @Test
    void presetsLeaveServerOrganisationAndGravityAtTheirDefaults() {
        for (RulePreset preset : RulePreset.values()) {
            for (RuleSpec spec : UNTOUCHED) {
                assertEquals(spec.defaultValue(), preset.value(spec), preset.id() + " " + spec.name());
            }
        }
    }

    @Test
    void everyPresetSetsEveryRuleToAValidValue() {
        for (RulePreset preset : RulePreset.values()) {
            RuleValues values = preset.ruleValues();
            assertEquals(WorldRuleSpecs.all().size(), values.asMap().size(), preset.id());
            for (RuleSpec spec : WorldRuleSpecs.all()) {
                assertTrue(spec.valid(preset.value(spec)), preset.id() + " " + spec.name());
                assertEquals(preset.value(spec), values.get(spec), preset.id() + " " + spec.name());
            }
        }
    }

    @Test
    void eachPresetMatchesItselfAndOnlyItself() {
        Set<RuleValues> seen = new HashSet<>();
        for (RulePreset preset : RulePreset.values()) {
            assertTrue(seen.add(preset.ruleValues()), preset.id() + " doppelt");
            assertEquals(Optional.of(preset), RulePreset.match(preset.ruleValues()));
            assertTrue(preset.changes(preset.ruleValues()).isEmpty());
            for (RulePreset other : RulePreset.values()) {
                if (other != preset) {
                    assertFalse(other.matches(preset.ruleValues()), other.id() + " passt auf " + preset.id());
                    assertFalse(other.changes(preset.ruleValues()).isEmpty());
                }
            }
        }
    }

    @Test
    void oneChangedRuleAfterAPresetIsCustom() {
        RuleValues tuned = RulePreset.TRAINING.ruleValues().with(WorldRuleSpecs.WIND, 2);
        assertEquals(Optional.empty(), RulePreset.match(tuned));
        assertEquals(List.of(WorldRuleSpecs.WIND), RulePreset.TRAINING.changes(tuned));
        RuleValues back = tuned.with(WorldRuleSpecs.WIND, 1);
        assertEquals(Optional.of(RulePreset.TRAINING), RulePreset.match(back));
        RuleValues gravity = RuleValues.DEFAULTS.with(WorldRuleSpecs.GRAVITY_SCALE, 120);
        assertEquals(Optional.empty(), RulePreset.match(gravity));
    }

    @Test
    void missingValuesCountAsDefaults() {
        Map<String, Integer> partial = Map.of(WorldRuleSpecs.BATTERY_SCALE.name(), 0, WorldRuleSpecs.BATTERY_SAG.name(), 0,
                WorldRuleSpecs.CRASH_DAMAGE.name(), 0, WorldRuleSpecs.PROP_STRIKES.name(), 0, WorldRuleSpecs.HARDWARE_LIMITS.name(), 0,
                WorldRuleSpecs.SAVE_ASSISTED_LAPS.name(), 1, WorldRuleSpecs.DRONE_TRAILS.name(), 1);
        assertEquals(Optional.of(RulePreset.TRAINING), RulePreset.match(new RuleValues(partial)));
    }

    @Test
    void idsAreShortLowerCaseAndFoundAgain() {
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(preset.name().toLowerCase(Locale.ROOT), preset.id());
            assertTrue(preset.id().length() <= RulePreset.MAX_ID_LENGTH);
            assertTrue(preset.id().matches("[a-z_]+"));
            assertEquals(Optional.of(preset), RulePreset.byId(preset.id()));
        }
        assertEquals(Optional.empty(), RulePreset.byId("TRAINING"));
        assertEquals(Optional.empty(), RulePreset.byId("hardcore"));
        assertEquals(Optional.empty(), RulePreset.byId(null));
        assertNotEquals(RulePreset.TRAINING.nameKey(), RulePreset.TRAINING.descriptionKey());
    }

    @Test
    void everyPresetHasNameAndDescriptionInBothLanguages() throws IOException {
        Map<String, Map<String, String>> languages = WorldRulesExport.languages();
        List<String> keys = List.of("propwash.server.world_rules.presets", "propwash.server.world_rules.preset.instant",
                "propwash.server.world_rules.preset.all_default", "propwash.server.world_rules.preset.differences",
                "propwash.server.world_rules.preset.line", "propwash.server.world_rules.preset.current",
                "propwash.server.world_rules.preset.custom", "propwash.server.world_rules.preset.unsaved",
                "propwash.server.world_rules.preset.applying", "propwash.server.world_rules.preset.none",
                "propwash.server.world_rules.preset_applied", "propwash.server.command.rules.preset.unknown",
                "propwash.server.command.rules.preset.done");
        for (String language : WorldRulesExport.LANGUAGES) {
            Map<String, String> texts = languages.get(language);
            for (RulePreset preset : RulePreset.values()) {
                assertFalse(texts.getOrDefault(preset.nameKey(), "").isBlank(), language + " " + preset.nameKey());
                assertFalse(texts.getOrDefault(preset.descriptionKey(), "").isBlank(), language + " " + preset.descriptionKey());
                assertTrue(texts.get(preset.nameKey()).length() <= 16, language + " Name zu lang: " + preset.id());
            }
            for (String key : keys) {
                assertFalse(texts.getOrDefault(key, "").isBlank(), language + " " + key);
            }
        }
        assertEquals("Realistisch", languages.get("de_de").get(RulePreset.REALISTIC.nameKey()));
    }
}
