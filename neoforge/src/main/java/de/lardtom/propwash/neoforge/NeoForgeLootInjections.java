package de.lardtom.propwash.neoforge;

import de.lardtom.propwash.apiimpl.LootInjectionsImpl;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.LootTableLoadEvent;

final class NeoForgeLootInjections {
    private NeoForgeLootInjections() {
    }

    static void register(IEventBus bus) {
        bus.addListener(LootTableLoadEvent.class, NeoForgeLootInjections::onLoad);
    }

    private static void onLoad(LootTableLoadEvent event) {
        for (ResourceKey<LootTable> table : LootInjectionsImpl.INSTANCE.tablesFor(event.getKey())) {
            event.getTable().addPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(event.getRegistries().getOrThrow(table))).build());
        }
    }
}
