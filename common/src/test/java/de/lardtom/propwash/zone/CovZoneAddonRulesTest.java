package de.lardtom.propwash.zone;

import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.server.AddonWorldRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovZoneAddonRulesTest {
    @BeforeAll
    static void register() {
        ExampleRules.register();
    }

    @Test
    void zoneFieldsNeverContainAddonRules() {
        List<WorldRule<?>> addon = AddonWorldRules.registered();
        assertTrue(addon.stream().anyMatch(rule -> rule.id().equals(ExampleRules.LAUNCHER_COOLDOWN)), "Beispiel-Regel angemeldet");
        assertTrue(addon.stream().anyMatch(rule -> rule.id().equals(ExampleRules.LAUNCHER_REFILL)), "Beispiel-Regel angemeldet");
        for (RuleSpec spec : PhysicsRules.SPECS) {
            assertTrue(Zone.overridable(spec));
            assertTrue(WorldRuleSpecs.all().contains(spec), spec.id() + " ist eine Propwash-Regel");
            assertFalse(AddonWorldRules.isAddonRule(net.minecraft.resources.Identifier.parse(spec.id())), spec.id());
            for (WorldRule<?> rule : addon) {
                assertFalse(spec.id().equals(rule.id().toString()) || spec.name().equals(rule.id().getPath()), spec.id() + " gleicht " + rule.id());
            }
        }
        ZoneDraft draft = new ZoneDraft(ZoneDraft.fresh(Zone.OVERWORLD, "Cov", 0.0, 64.0, 0.0));
        for (ZoneDraft.Row row : draft.rows()) {
            if (row.kind() == ZoneDraft.Kind.RULE) {
                assertTrue(row.spec() != null && PhysicsRules.SPECS.contains(row.spec()), "Zeile " + row.spec());
            } else {
                assertEquals(null, row.spec());
            }
        }
        long rules = draft.rows().stream().filter(row -> row.kind() == ZoneDraft.Kind.RULE).count();
        assertEquals(PhysicsRules.SPECS.size(), rules, "Zonen-Editor zeigt genau die Physik-Regeln");
    }

    @Test
    void addonRuleIdsAreDroppedFromZoneOverrides() {
        Map<String, Integer> overrides = new LinkedHashMap<>();
        overrides.put(ExampleRules.LAUNCHER_COOLDOWN.toString(), 5);
        overrides.put(ExampleRules.LAUNCHER_COOLDOWN.getPath(), 5);
        overrides.put(ExampleRules.LAUNCHER_REFILL.toString(), 1);
        overrides.put(WorldRuleSpecs.GRAVITY_SCALE.name(), 50);
        Map<String, Integer> kept = Zone.normalize(overrides);
        assertEquals(Map.of(WorldRuleSpecs.GRAVITY_SCALE.name(), 50), kept);
        Zone zone = new Zone(7, Zone.OVERWORLD, "Cov", ZoneShape.BOX, new ZoneBox(0, 0, 0, 10, 10, 10), 0, true, 5, overrides);
        assertFalse(zone.overrides().containsKey(ExampleRules.LAUNCHER_COOLDOWN.toString()));
        assertFalse(zone.overrides().containsKey(ExampleRules.LAUNCHER_COOLDOWN.getPath()));
        assertTrue(zone.overrides(WorldRuleSpecs.GRAVITY_SCALE));
    }
}
