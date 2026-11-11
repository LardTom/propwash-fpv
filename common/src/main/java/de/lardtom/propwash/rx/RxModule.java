package de.lardtom.propwash.rx;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class RxModule {
    public static final Identifier ID = Constants.id("rx_block");

    public static final PropwashRegistries.BlockWithItem<RxBlock> RX_BLOCK = PropwashRegistries.blockWithItem(
            ID.getPath(),
            RxBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion(),
            RxBlockItem::new,
            p -> p);

    public static final Identifier CREATIVE_ID = Constants.id("creative_rx_block");
    public static final float CREATIVE_EXPLOSION_RESISTANCE = 3600000.0F;

    public static final PropwashRegistries.BlockWithItem<CreativeRxBlock> CREATIVE_RX_BLOCK = PropwashRegistries.blockWithItem(
            CREATIVE_ID.getPath(),
            CreativeRxBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, CREATIVE_EXPLOSION_RESISTANCE)
                    .noLootTable()
                    .sound(SoundType.METAL)
                    .noOcclusion(),
            RxBlockItem::new,
            p -> p.rarity(Rarity.EPIC));

    public static final RegHolder<BlockEntityType<RxBlockEntity>> RX_BLOCK_BE = PropwashRegistries.blockEntity(
            ID.getPath(), RxBlockEntity::new, RX_BLOCK.block(), CREATIVE_RX_BLOCK.block());

    private static boolean initialized;

    private RxModule() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RxServer.init();
    }
}
