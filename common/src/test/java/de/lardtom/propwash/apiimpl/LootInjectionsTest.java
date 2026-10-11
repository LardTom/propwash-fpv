package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootInjectionsTest {
    private static final ResourceKey<LootTable> DUNGEON = key("minecraft", "chests/simple_dungeon");
    private static final ResourceKey<LootTable> MINESHAFT = key("minecraft", "chests/abandoned_mineshaft");
    private static final ResourceKey<LootTable> ADDON_A = key("meinaddon", "inject/dungeon_parts");
    private static final ResourceKey<LootTable> ADDON_B = key("anderesaddon", "inject/dungeon");

    @Test
    void injectionsAreCollectedPerTargetInOrder() {
        LootInjectionsImpl loot = new LootInjectionsImpl();
        loot.inject(DUNGEON, ADDON_A);
        loot.inject(DUNGEON, ADDON_B);
        loot.inject(MINESHAFT, ADDON_A);
        assertEquals(List.of(ADDON_A, ADDON_B), loot.tablesFor(DUNGEON));
        assertEquals(List.of(ADDON_A), loot.tablesFor(MINESHAFT));
        assertEquals(List.of(), loot.tablesFor(key("minecraft", "chests/end_city_treasure")));
        assertEquals(3, loot.size());
        assertThrows(UnsupportedOperationException.class, () -> loot.tablesFor(DUNGEON).add(ADDON_A));
    }

    @Test
    void invalidInjectionsAreRejectedWithClearMessages() {
        LootInjectionsImpl loot = new LootInjectionsImpl();
        loot.inject(DUNGEON, ADDON_A);
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class, () -> loot.inject(DUNGEON, ADDON_A));
        assertTrue(twice.getMessage().contains("doppelt"), twice.getMessage());
        assertThrows(IllegalArgumentException.class, () -> loot.inject(DUNGEON, MINESHAFT));
        assertThrows(IllegalArgumentException.class, () -> loot.inject(DUNGEON, key("propwash", "inject/simple_dungeon")));
        assertThrows(IllegalArgumentException.class, () -> loot.inject(ADDON_A, ADDON_A));
        assertThrows(NullPointerException.class, () -> loot.inject(null, ADDON_A));
        assertThrows(NullPointerException.class, () -> loot.inject(DUNGEON, null));
        assertEquals(1, loot.size());
    }

    @Test
    void lateInjectionsAreRefused() {
        LootInjectionsImpl loot = new LootInjectionsImpl();
        loot.inject(DUNGEON, ADDON_A);
        loot.close();
        IllegalStateException late = assertThrows(IllegalStateException.class, () -> loot.inject(MINESHAFT, ADDON_B));
        assertTrue(late.getMessage().contains("zu spät"), late.getMessage());
        assertEquals(List.of(ADDON_A), loot.tablesFor(DUNGEON));
    }

    @Test
    void apiReturnsTheSharedInstance() {
        assertSame(LootInjectionsImpl.INSTANCE, PropwashApi.loot());
    }

    private static ResourceKey<LootTable> key(String namespace, String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(namespace, path));
    }
}
