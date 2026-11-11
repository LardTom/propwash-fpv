package de.lardtom.propwash.race;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceRulesTest {
    private static final Set<String> RELEVANT = Set.of("gravity_scale", "prop_wash", "ground_effect", "hardware_limits", "wind", "turbulence",
            "battery_scale", "battery_sag", "crash_damage", "crash_damage_scale", "crash_tolerance", "prop_strikes", "infinite_range_analog",
            "infinite_range_digital", "drone_hits_entities", "drones_break_blocks", "break_impact_scale", "waterproof_drones",
            "drones_boss_interactions");
    private static final Set<String> NEUTRAL = Set.of("crash_disarm", "max_video_channels", "signal_repeaters", "allow_3d_mode", "part_compatibility",
            "autonomous_rescue", "freeform_gate_max_size", "drones_generate_chunks", "save_assisted_laps", "drone_trails", "invulnerable_pilots",
            "allow_reset", "allow_freecam", "players_create_zones", "free_course_placement", "foreign_ghosts", "camera_chunks",
            "autonomous_flights_per_player", "autonomous_flights_total", "creative_fc_race_legal", "drones_trigger_sculk");
    private static final String NAMESPACE = "racerules";

    private static final class Addon {
        static final WorldRule<Integer> GUSTS = PropwashApi.worldRules().registerInt(Identifier.fromNamespaceAndPath(NAMESPACE, "gusts"), 0, 0, 5);
        static final WorldRule<Boolean> CHIME = PropwashApi.worldRules().registerBoolean(Identifier.fromNamespaceAndPath(NAMESPACE, "chime"), false);

        static {
            PropwashApi.worldRules().raceNeutral(CHIME);
        }

        private Addon() {
        }
    }

    static int changed(RuleSpec spec) {
        if (spec.kind() == RuleSpec.Kind.BOOL) {
            return spec.defaultValue() == 0 ? 1 : 0;
        }
        int up = spec.defaultValue() + spec.step();
        return up <= spec.max() ? up : spec.defaultValue() - spec.step();
    }

    private static RaceRuleSet world(RuleValues values) {
        return RaceRules.world(RaceRules.values(values, Map.of()));
    }

    @Test
    void everyPropwashRuleIsClassifiedOnPurpose() {
        Set<String> seen = new HashSet<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            seen.add(spec.name());
            assertTrue(RELEVANT.contains(spec.name()) ^ NEUTRAL.contains(spec.name()), "neue Welt-Regel ohne Einordnung: " + spec.name());
            assertEquals(RELEVANT.contains(spec.name()), spec.raceRelevant(), spec.name());
            assertEquals(spec.raceRelevant(), RaceRules.find(spec.id()).orElseThrow().relevant(), spec.name());
            assertEquals(spec.raceRelevant(), RaceRules.find(spec.name()).orElseThrow().relevant(), spec.name());
        }
        Set<String> listed = new HashSet<>(RELEVANT);
        listed.addAll(NEUTRAL);
        assertEquals(listed, seen);
    }

    @Test
    void crashDisarmAndOtherNeutralRulesNeverMakeALapAssisted() {
        assertFalse(WorldRuleSpecs.CRASH_DISARM.raceRelevant());
        for (String name : NEUTRAL) {
            RuleSpec spec = WorldRuleSpecs.byName(name).orElseThrow();
            RuleValues values = RuleValues.DEFAULTS.with(spec, changed(spec));
            RaceRuleSet rules = world(values);
            assertTrue(rules.isDefault(), name);
            assertTrue(rules.matches(RaceStandard.DEFAULTS), name);
            assertTrue(AssistCheck.physics(PhysicsRules.of(values), false).none(), name);
            assertTrue(AssistCheck.evaluate(AssistCheck.Setup.clean().withPhysics(PhysicsRules.of(values), false)).none(), name);
            assertTrue(AssistCheck.evaluate(AssistCheck.Setup.clean(), rules, RaceStandard.DEFAULTS).none(), name);
            assertTrue(new AssistDetail(LapAssist.WORLD_PHYSICS, name, changed(spec), spec.defaultValue()).neutral(), name);
        }
    }

    @Test
    void everyRelevantRuleNamesItselfWithValueAndStandard() {
        for (String name : RELEVANT) {
            RuleSpec spec = WorldRuleSpecs.byName(name).orElseThrow();
            int value = changed(spec);
            RaceRuleSet rules = world(RuleValues.DEFAULTS.with(spec, value));
            assertEquals(List.of(new RaceRuleSet.Seen(spec.id(), value, RaceRuleSet.WORLD, false)), rules.seen(), name);
            assertFalse(rules.matches(RaceStandard.DEFAULTS), name);
            AssistReasons reasons = AssistCheck.evaluate(AssistCheck.Setup.clean(), rules, RaceStandard.DEFAULTS);
            assertEquals(LapAssist.WORLD_PHYSICS.bit(), reasons.mask(), name);
            assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, name, value, spec.defaultValue())), reasons.details(), name);
            RaceStandard standard = RaceStandard.DEFAULTS.with(name, value);
            assertTrue(rules.matches(standard), name);
            assertTrue(AssistCheck.evaluate(AssistCheck.Setup.clean(), rules, standard).none(), name);
            assertFalse(world(RuleValues.DEFAULTS).matches(standard), name);
        }
    }

    @Test
    void neutralRulesStayNeutralEvenWhenTheStandardMentionsThem() {
        RaceStandard standard = RaceStandard.DEFAULTS.with("crash_disarm", 0).with("save_assisted_laps", 1);
        assertTrue(standard.isDefault());
        assertTrue(standard.values().isEmpty());
    }

    @Test
    void addonRulesCountByDefaultAndCanBeMarkedNeutral() {
        WorldRules api = PropwashApi.worldRules();
        assertTrue(api.raceRelevant(Addon.GUSTS));
        assertFalse(api.raceRelevant(Addon.CHIME));
        RaceRules.Rule gusts = RaceRules.find(Addon.GUSTS.id().toString()).orElseThrow();
        RaceRules.Rule chime = RaceRules.find(Addon.CHIME.id().toString()).orElseThrow();
        assertTrue(gusts.relevant());
        assertTrue(gusts.addon());
        assertFalse(chime.relevant());
        assertTrue(RaceRules.relevant().contains(gusts));
        assertFalse(RaceRules.relevant().contains(chime));
        Map<String, Integer> values = RaceRules.values(RuleValues.DEFAULTS, Map.of(Addon.GUSTS.id(), "3", Addon.CHIME.id(), "true"));
        RaceRuleSet rules = RaceRules.world(values);
        assertEquals(List.of(new RaceRuleSet.Seen(gusts.id(), 3, RaceRuleSet.WORLD, false)), rules.seen());
        AssistReasons reasons = rules.reasons(RaceStandard.DEFAULTS);
        assertEquals(List.of(new AssistDetail(LapAssist.WORLD_PHYSICS, gusts.id(), 3, 0)), reasons.details());
        assertTrue(rules.matches(RaceStandard.DEFAULTS.with(gusts.id(), 3)));
        assertTrue(RaceRules.world(RaceRules.values(RuleValues.DEFAULTS, Map.of(Addon.CHIME.id(), "true"))).isDefault());
    }

    @Test
    void onlyRegisteredAddonRulesCanBeMarkedNeutral() {
        WorldRule<?> foreign = new WorldRule<Boolean>() {
            @Override
            public Identifier id() {
                return Addon.CHIME.id();
            }

            @Override
            public Type type() {
                return Type.BOOLEAN;
            }

            @Override
            public Boolean defaultValue() {
                return false;
            }

            @Override
            public int min() {
                return 0;
            }

            @Override
            public int max() {
                return 1;
            }

            @Override
            public Boolean get(Level level) {
                return false;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().raceNeutral(foreign));
        assertThrows(NullPointerException.class, () -> PropwashApi.worldRules().raceNeutral(null));
        assertFalse(PropwashApi.worldRules().raceRelevant(Addon.CHIME));
    }

    @Test
    void freeCoursePlacementIsANeutralGameplayRuleOffByDefault() {
        RuleSpec spec = WorldRuleSpecs.FREE_COURSE_PLACEMENT;
        assertEquals("free_course_placement", spec.name());
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertEquals(0, spec.defaultValue());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertFalse(spec.raceRelevant());
        assertFalse(RuleValues.DEFAULTS.on(spec));
    }
}
