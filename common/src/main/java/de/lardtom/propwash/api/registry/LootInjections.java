package de.lardtom.propwash.api.registry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public interface LootInjections {
    void inject(ResourceKey<LootTable> target, ResourceKey<LootTable> addonTable);

    List<ResourceKey<LootTable>> tablesFor(ResourceKey<LootTable> target);
}
