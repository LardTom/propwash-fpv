package de.lardtom.propwash.fabric;

import de.lardtom.propwash.apiimpl.LootInjectionsImpl;
import de.lardtom.propwash.content.ChestLoot;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

final class FabricChestLoot {
    private FabricChestLoot() {
    }

    static void register() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            ChestLoot.tableFor(key).ifPresent(table -> builder.withPool(pool(registries, table)));
            for (ResourceKey<LootTable> table : LootInjectionsImpl.INSTANCE.tablesFor(key)) {
                builder.withPool(pool(registries, table));
            }
        });
    }

    private static LootPool.Builder pool(HolderLookup.Provider registries, ResourceKey<LootTable> table) {
        return LootPool.lootPool().add(NestedLootTable.lootTableReference(registries.getOrThrow(table)));
    }
}
