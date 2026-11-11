package de.lardtom.propwash.join;

import de.lardtom.propwash.platform.ModMeta;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalJoinInfoFilterTest {
    private static final ModMeta TRAILS = meta("trails_server", true);
    private static final ModMeta OPTIONAL = meta("fancy_hud", true);
    private static final ModMeta NORMAL = meta("justmoreparts", false);
    private static final ModMeta OPTIONAL_WITH_PARTS = meta("mixed_addon", true);

    private static ModMeta meta(String id, boolean clientOptional) {
        return new ModMeta(id, id, "1.0.0", "", clientOptional);
    }

    private static Map<String, TreeSet<String>> parts(String id) {
        return Map.of(id, new TreeSet<>(List.of(id)));
    }

    @Test
    void addonWithoutClientNeedStaysOutOfTheOverview() {
        Map<String, AddonInfo> addons = LocalJoinInfo.dependents(List.of(TRAILS, OPTIONAL), Map.of());
        assertFalse(addons.containsKey(TRAILS.id()), "Server-Mod steht in der Übersicht: " + addons.keySet());
        assertFalse(addons.containsKey(OPTIONAL.id()), "client-optionales Add-on steht in der Übersicht: " + addons.keySet());
    }

    @Test
    void normalAddonStaysListed() {
        Map<String, AddonInfo> addons = LocalJoinInfo.dependents(List.of(NORMAL), Map.of());
        assertEquals(NORMAL.name(), addons.get(NORMAL.id()).name());
    }

    @Test
    void clientOptionalAddonWithPartsStaysListed() {
        Map<String, AddonInfo> addons = LocalJoinInfo.dependents(List.of(OPTIONAL_WITH_PARTS), parts(OPTIONAL_WITH_PARTS.id()));
        assertTrue(addons.containsKey(OPTIONAL_WITH_PARTS.id()), "Add-on mit Teilen fehlt: " + addons.keySet());
        assertEquals(List.of(OPTIONAL_WITH_PARTS.id()), addons.get(OPTIONAL_WITH_PARTS.id()).namespaces());
    }

    @Test
    void onlyAddonsWithoutClientNeedAreDropped() {
        Map<String, AddonInfo> addons = LocalJoinInfo.dependents(List.of(TRAILS, NORMAL, OPTIONAL, OPTIONAL_WITH_PARTS), parts(OPTIONAL_WITH_PARTS.id()));
        assertEquals(List.of(NORMAL.id(), OPTIONAL_WITH_PARTS.id()), List.copyOf(addons.keySet()));
    }
}
