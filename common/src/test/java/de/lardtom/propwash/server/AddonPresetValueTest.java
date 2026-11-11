package de.lardtom.propwash.server;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleValues;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonPresetValueTest {
    private static final String NAMESPACE = "presetvalue";

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    private static final class Rules {
        static final WorldRule<Integer> COOLDOWN = PropwashApi.worldRules().registerInt(id("cooldown_ticks"), 10, 0, 200);
        static final WorldRule<Boolean> REFILL = PropwashApi.worldRules().registerBoolean(id("refill"), false);
        static final WorldRule<Integer> RANGE = PropwashApi.worldRules().registerInt(id("range"), 50, 1, 100);
        static final WorldRule<Integer> OVERWRITE = PropwashApi.worldRules().registerInt(id("overwrite"), 3, 0, 9);
        static final List<WorldRule<?>> ALL = List.of(COOLDOWN, REFILL, RANGE);

        static {
            WorldRules api = PropwashApi.worldRules();
            api.presetValue(COOLDOWN, WorldRules.PRESET_REALISTIC, 20);
            api.presetValue(COOLDOWN, WorldRules.PRESET_TRAINING, 4);
            api.presetValue(COOLDOWN, WorldRules.PRESET_ARCADE, 0);
            api.presetValue(REFILL, WorldRules.PRESET_ARCADE, true);
        }

        private Rules() {
        }
    }

    private static Map<Identifier, String> defaults() {
        Map<Identifier, String> out = new HashMap<>();
        for (WorldRule<?> rule : Rules.ALL) {
            out.put(rule.id(), AddonWorldRules.serialize(rule, rule.defaultValue()));
        }
        return out;
    }

    private static Map<Identifier, String> apply(String preset, Map<Identifier, String> current) {
        Map<Identifier, String> out = new HashMap<>(current);
        out.putAll(AddonWorldRules.presetChanges(preset, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, current));
        return out;
    }

    @Test
    void presetIdsOfTheApiAreTheBuiltInPresets() {
        assertEquals(WorldRules.PRESETS, Arrays.stream(RulePreset.values()).map(RulePreset::id).toList());
    }

    @Test
    void presetValueStoresRealisticTrainingAndArcade() {
        assertEquals(Map.of(WorldRules.PRESET_REALISTIC, 20, WorldRules.PRESET_TRAINING, 4, WorldRules.PRESET_ARCADE, 0),
                Api17Registries.WORLD_RULES.presetValues(Rules.COOLDOWN.id()));
        assertEquals(Map.of(WorldRules.PRESET_ARCADE, true), Api17Registries.WORLD_RULES.presetValues(Rules.REFILL.id()));
        assertEquals(Map.of(), Api17Registries.WORLD_RULES.presetValues(Rules.RANGE.id()));
        PropwashApi.worldRules().presetValue(Rules.OVERWRITE, WorldRules.PRESET_TRAINING, 8);
        PropwashApi.worldRules().presetValue(Rules.OVERWRITE, WorldRules.PRESET_TRAINING, 5);
        assertEquals(Map.of(WorldRules.PRESET_TRAINING, 5), Api17Registries.WORLD_RULES.presetValues(Rules.OVERWRITE.id()));
    }

    @Test
    void unknownPresetIdFailsWithText() {
        for (String unknown : List.of("hardcore", "Training", "", "realistic ")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> PropwashApi.worldRules().presetValue(Rules.COOLDOWN, unknown, 1));
            assertTrue(error.getMessage().contains("'" + unknown + "'"), error.getMessage());
            assertTrue(error.getMessage().contains(Rules.COOLDOWN.id().toString()), error.getMessage());
            assertTrue(error.getMessage().contains("realistic, training, arcade"), error.getMessage());
        }
        assertThrows(NullPointerException.class, () -> PropwashApi.worldRules().presetValue(Rules.COOLDOWN, null, 1));
        assertEquals(4, Api17Registries.WORLD_RULES.presetValues(Rules.COOLDOWN.id()).get(WorldRules.PRESET_TRAINING));
    }

    @Test
    void foreignRuleFailsWithText() {
        WorldRule<Integer> foreign = new ForeignRule(id("foreign"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PropwashApi.worldRules().presetValue(foreign, WorldRules.PRESET_TRAINING, 1));
        assertTrue(error.getMessage().contains(NAMESPACE + ":foreign"), error.getMessage());
        assertTrue(error.getMessage().contains("PropwashApi.worldRules()"), error.getMessage());
        WorldRule<Integer> lookalike = new ForeignRule(Rules.COOLDOWN.id());
        IllegalArgumentException copy = assertThrows(IllegalArgumentException.class,
                () -> PropwashApi.worldRules().presetValue(lookalike, WorldRules.PRESET_TRAINING, 1));
        assertTrue(copy.getMessage().contains(Rules.COOLDOWN.id().toString()), copy.getMessage());
        assertEquals(Map.of(), Api17Registries.WORLD_RULES.presetValues(foreign.id()));
        assertEquals(4, Api17Registries.WORLD_RULES.presetValues(Rules.COOLDOWN.id()).get(WorldRules.PRESET_TRAINING));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void valuesOutsideTheRuleFailWithText() {
        IllegalArgumentException high = assertThrows(IllegalArgumentException.class,
                () -> PropwashApi.worldRules().presetValue(Rules.COOLDOWN, WorldRules.PRESET_ARCADE, 201));
        assertTrue(high.getMessage().contains("201") && high.getMessage().contains(Rules.COOLDOWN.id().toString()), high.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().presetValue(Rules.RANGE, WorldRules.PRESET_ARCADE, 0));
        WorldRule raw = Rules.REFILL;
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().presetValue(raw, WorldRules.PRESET_TRAINING, 1));
        assertThrows(NullPointerException.class, () -> PropwashApi.worldRules().presetValue(Rules.REFILL, WorldRules.PRESET_TRAINING, null));
        assertEquals(0, Api17Registries.WORLD_RULES.presetValues(Rules.COOLDOWN.id()).get(WorldRules.PRESET_ARCADE));
    }

    @Test
    void applyingAPresetSetsTheAddonValueAndLeavesRulesWithoutEntry() {
        Map<Identifier, String> training = apply(WorldRules.PRESET_TRAINING, defaults());
        assertEquals("4", training.get(Rules.COOLDOWN.id()));
        assertEquals("false", training.get(Rules.REFILL.id()));
        assertEquals("50", training.get(Rules.RANGE.id()));
        assertEquals(Map.of(Rules.COOLDOWN.id(), "4"),
                AddonWorldRules.presetChanges(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, defaults()));
        assertEquals(Map.of(), AddonWorldRules.presetChanges(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues,
                training));

        Map<Identifier, String> arcade = apply(WorldRules.PRESET_ARCADE, training);
        assertEquals("0", arcade.get(Rules.COOLDOWN.id()));
        assertEquals("true", arcade.get(Rules.REFILL.id()));
        assertEquals("50", arcade.get(Rules.RANGE.id()));

        Map<Identifier, String> realistic = apply(WorldRules.PRESET_REALISTIC, arcade);
        assertEquals("20", realistic.get(Rules.COOLDOWN.id()));
        assertEquals("true", realistic.get(Rules.REFILL.id()), "ohne Eintrag lässt die Voreinstellung die Regel unverändert");

        Map<Identifier, String> tuned = new HashMap<>(defaults());
        tuned.put(Rules.RANGE.id(), "75");
        assertEquals("75", apply(WorldRules.PRESET_ARCADE, tuned).get(Rules.RANGE.id()));
        assertEquals("4", apply(WorldRules.PRESET_TRAINING, Map.of()).get(Rules.COOLDOWN.id()));
        assertFalse(apply(WorldRules.PRESET_TRAINING, Map.of()).containsKey(Rules.REFILL.id()));
    }

    @Test
    void activePresetCountsTheAddonValues() {
        Map<Identifier, String> training = apply(WorldRules.PRESET_TRAINING, defaults());
        assertTrue(AddonWorldRules.presetMatches(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, training));
        assertFalse(AddonWorldRules.presetMatches(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, defaults()));
        assertFalse(AddonWorldRules.presetMatches(WorldRules.PRESET_ARCADE, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, training));
        Map<Identifier, String> changed = new HashMap<>(training);
        changed.put(Rules.COOLDOWN.id(), "5");
        assertFalse(AddonWorldRules.presetMatches(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, changed));
        changed.put(Rules.RANGE.id(), "99");
        changed.put(Rules.COOLDOWN.id(), "4");
        assertTrue(AddonWorldRules.presetMatches(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues, changed),
                "Regel ohne Eintrag zählt nicht");
        Map<Identifier, String> unknownOnServer = new HashMap<>(training);
        unknownOnServer.remove(Rules.COOLDOWN.id());
        assertTrue(AddonWorldRules.presetMatches(WorldRules.PRESET_ARCADE, Rules.ALL, Api17Registries.WORLD_RULES::presetValues,
                Map.of(Rules.REFILL.id(), "true")), "Regeln, die der Server nicht kennt, zählen nicht");
        assertTrue(AddonWorldRules.presetMatches(WorldRules.PRESET_TRAINING, Rules.ALL, Api17Registries.WORLD_RULES::presetValues,
                unknownOnServer));
    }

    @Test
    void propwashPresetsStayUnchangedWithoutWorld() {
        assertTrue(Rules.ALL.size() > 0);
        assertEquals(Optional.of(RulePreset.REALISTIC), RulePreset.match(RuleValues.DEFAULTS));
        for (RulePreset preset : RulePreset.values()) {
            assertEquals(Optional.of(preset), RulePreset.match(preset.ruleValues()), preset.id());
        }
        assertTrue(AddonWorldRules.presetActive(WorldRules.PRESET_TRAINING));
    }

    @Test
    void valuesParseLikeGamerules() {
        assertEquals(10, AddonWorldRules.parse(Rules.COOLDOWN, null));
        assertEquals(7, AddonWorldRules.parse(Rules.COOLDOWN, " 7 "));
        assertEquals(10, AddonWorldRules.parse(Rules.COOLDOWN, "201"));
        assertEquals(10, AddonWorldRules.parse(Rules.COOLDOWN, "-1"));
        assertEquals(10, AddonWorldRules.parse(Rules.COOLDOWN, "x"));
        assertEquals(true, AddonWorldRules.parse(Rules.REFILL, "true"));
        assertEquals(false, AddonWorldRules.parse(Rules.REFILL, "TRUE"));
        assertEquals("true", AddonWorldRules.serialize(Rules.REFILL, true));
        assertEquals("200", AddonWorldRules.serialize(Rules.COOLDOWN, 200));
        assertEquals(10, Rules.COOLDOWN.get(null));
        assertEquals(false, Rules.REFILL.get(null));
        assertSame(Rules.COOLDOWN, PropwashApi.worldRules().find(Rules.COOLDOWN.id()).orElseThrow());
        assertTrue(AddonWorldRules.isAddonRule(Rules.COOLDOWN.id()));
        assertFalse(AddonWorldRules.isAddonRule(Identifier.fromNamespaceAndPath("propwash", "wind")));
        assertEquals("gamerule.presetvalue.cooldown_ticks", Rules.COOLDOWN.nameKey());
    }

    @Test
    void registeringTwiceFailsBeforeAnythingIsStored() {
        assertTrue(Rules.ALL.size() > 0);
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
                () -> PropwashApi.worldRules().registerInt(Rules.COOLDOWN.id(), 1, 0, 2));
        assertTrue(twice.getMessage().contains(Rules.COOLDOWN.id().toString()), twice.getMessage());
        assertSame(Rules.COOLDOWN, PropwashApi.worldRules().find(Rules.COOLDOWN.id()).orElseThrow());
        assertEquals(10, Rules.COOLDOWN.defaultValue());
    }

    private record ForeignRule(Identifier id) implements WorldRule<Integer> {
        @Override
        public Type type() {
            return Type.INTEGER;
        }

        @Override
        public Integer defaultValue() {
            return 1;
        }

        @Override
        public int min() {
            return 0;
        }

        @Override
        public int max() {
            return 9;
        }

        @Override
        public Integer get(Level level) {
            return 1;
        }
    }
}
