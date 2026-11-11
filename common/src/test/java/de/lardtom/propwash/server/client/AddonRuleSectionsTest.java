package de.lardtom.propwash.server.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonRuleSectionsTest {
    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void oneSectionPerAddonNamedAfterTheModInRegistrationOrder() {
        List<Identifier> ids = List.of(id("mobdefense", "cooldown_ticks"), id("camdrones", "max_waypoints"), id("mobdefense", "allow_buckets"));
        Map<String, String> names = Map.of("mobdefense", "Mob Defense", "camdrones", "Camera Drones");
        Map<String, List<Identifier>> sections = AddonRuleSections.sections(ids, names::get, Set.of(WorldRulesTab.OTHER_GROUP));
        assertEquals(List.of("Camera Drones", "Mob Defense"), List.copyOf(sections.keySet()));
        assertEquals(List.of(id("mobdefense", "cooldown_ticks"), id("mobdefense", "allow_buckets")), sections.get("Mob Defense"));
        assertEquals(List.of(id("camdrones", "max_waypoints")), sections.get("Camera Drones"));
    }

    @Test
    void equalNamesAndTakenKeysGetTheNamespace() {
        List<Identifier> ids = List.of(id("alpha", "a"), id("beta", "b"), id("gamma", "c"));
        Map<String, String> names = Map.of("alpha", "Rules", "beta", "Rules", "gamma", "propwash.server.world_rules.group.other");
        Map<String, List<Identifier>> sections = AddonRuleSections.sections(ids, names::get, Set.of(WorldRulesTab.OTHER_GROUP));
        assertEquals(List.of("propwash.server.world_rules.group.other (gamma)", "Rules", "Rules (beta)"), List.copyOf(sections.keySet()));
    }

    @Test
    void percentSignsSurviveTheSectionTitle() {
        Map<String, List<Identifier>> sections = AddonRuleSections.sections(List.of(id("hundred", "x")), namespace -> "100% Drones",
                Set.of());
        assertEquals(List.of("100%% Drones"), List.copyOf(sections.keySet()));
        assertEquals("50%%", AddonRuleSections.escape("50%"));
    }
}
