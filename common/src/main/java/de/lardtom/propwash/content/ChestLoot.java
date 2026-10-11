package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ChestLoot {
    public static final String INJECT_PREFIX = "inject/";
    public static final float FOUND_CHARGE = 0.4F;

    private static final Map<ResourceKey<LootTable>, ResourceKey<LootTable>> BY_TARGET = new LinkedHashMap<>();

    static {
        inject("village_house", "chests/village/village_plains_house", "chests/village/village_desert_house",
                "chests/village/village_savanna_house", "chests/village/village_snowy_house", "chests/village/village_taiga_house");
        inject("village_toolsmith", "chests/village/village_toolsmith");
        inject("village_cartographer", "chests/village/village_cartographer");
        inject("abandoned_mineshaft", "chests/abandoned_mineshaft");
        inject("simple_dungeon", "chests/simple_dungeon");
        inject("temple", "chests/desert_pyramid", "chests/jungle_temple");
        inject("pillager_outpost", "chests/pillager_outpost");
        inject("stronghold", "chests/stronghold_corridor", "chests/stronghold_crossing");
        inject("shipwreck_supply", "chests/shipwreck_supply");
        inject("trial_chambers", "chests/trial_chambers/supply", "chests/trial_chambers/intersection");
        inject("vault", "chests/trial_chambers/reward");
        inject("vault_ominous", "chests/trial_chambers/reward_ominous");
        inject("ancient_city", "chests/ancient_city");
        inject("end_city", "chests/end_city_treasure");
    }

    private ChestLoot() {
    }

    public record Injection(ResourceKey<LootTable> target, ResourceKey<LootTable> table) {
    }

    public static Optional<ResourceKey<LootTable>> tableFor(ResourceKey<LootTable> target) {
        return Optional.ofNullable(BY_TARGET.get(target));
    }

    public static List<Injection> injections() {
        List<Injection> list = new ArrayList<>();
        BY_TARGET.forEach((target, table) -> list.add(new Injection(target, table)));
        return Collections.unmodifiableList(list);
    }

    public static Set<ResourceKey<LootTable>> tables() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(BY_TARGET.values()));
    }

    private static void inject(String table, String... vanillaTargets) {
        ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, Constants.id(INJECT_PREFIX + table));
        for (String target : vanillaTargets) {
            ResourceKey<LootTable> targetKey = ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(target));
            if (BY_TARGET.put(targetKey, tableKey) != null) {
                throw new IllegalStateException("Doppelte Truhen-Beute für " + target);
            }
        }
    }
}
