package de.lardtom.propwash.bando;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

public final class BandoModule {
    public static final TagKey<Structure> TAG = TagKey.create(Registries.STRUCTURE, Constants.id("bandos"));
    public static final TagKey<Biome> KEEP_AWAY = TagKey.create(Registries.BIOME, Constants.id("bando/keep_away"));
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Constants.id("chests/bando"));
    public static final String SET = "bandos";
    public static final String MAP_NAME = "bando_map";
    public static final ResourceKey<LootItemFunction> MAP_MODIFIER = ResourceKey.create(Registries.ITEM_MODIFIER, Constants.id(MAP_NAME));
    public static final ResourceKey<VillagerTrade> MAP_TRADE = ResourceKey.create(Registries.VILLAGER_TRADE,
            Constants.id("cartographer/3/emerald_and_compass_" + MAP_NAME));

    public static final RegHolder<StructureType<BandoStructure>> STRUCTURE_TYPE = PropwashRegistries.list(Registries.STRUCTURE_TYPE)
            .<StructureType<BandoStructure>>add("bando", key -> () -> BandoStructure.CODEC);
    public static final RegHolder<StructurePieceType> PIECE = PropwashRegistries.list(Registries.STRUCTURE_PIECE)
            .<StructurePieceType>add("bando", key -> (StructurePieceType.ContextlessType) BandoPiece::new);
    public static final RegHolder<MapDecorationType> MAP_DECORATION = PropwashRegistries.list(Registries.MAP_DECORATION_TYPE)
            .<MapDecorationType>add("bando", key -> new MapDecorationType(Constants.id("bando"), true, false));
    public static final RegHolder<BandoMapItem> MAP = PropwashRegistries.item(MAP_NAME, BandoMapItem::new,
            p -> p.component(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY));

    private static boolean initialized;

    private BandoModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
    }
}
