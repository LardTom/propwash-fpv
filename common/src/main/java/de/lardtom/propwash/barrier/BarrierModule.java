package de.lardtom.propwash.barrier;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.HelpBlockItem;
import de.lardtom.propwash.registry.PropwashRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

public final class BarrierModule {
    public static final Identifier DRONE_PASS_ID = Constants.id(BarrierKind.DRONE_PASS.path());
    public static final Identifier DRONE_BLOCK_ID = Constants.id(BarrierKind.DRONE_BLOCK.path());
    public static final List<Identifier> IDS = List.of(DRONE_PASS_ID, DRONE_BLOCK_ID);
    public static final float EXPLOSION_RESISTANCE = 3600000.8F;

    public static final PropwashRegistries.BlockWithItem<DroneBarrierBlock> DRONE_PASS = register(BarrierKind.DRONE_PASS);
    public static final PropwashRegistries.BlockWithItem<DroneBarrierBlock> DRONE_BLOCK = register(BarrierKind.DRONE_BLOCK);

    private BarrierModule() {
    }

    public static void init() {
    }

    public static PropwashRegistries.BlockWithItem<DroneBarrierBlock> of(BarrierKind kind) {
        return kind == BarrierKind.DRONE_PASS ? DRONE_PASS : DRONE_BLOCK;
    }

    private static PropwashRegistries.BlockWithItem<DroneBarrierBlock> register(BarrierKind kind) {
        return PropwashRegistries.blockWithItem(kind.path(), properties -> new DroneBarrierBlock(kind, properties), BarrierModule::properties,
                HelpBlockItem::new, item -> item.rarity(Rarity.EPIC));
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .strength(-1.0F, EXPLOSION_RESISTANCE)
                .mapColor(state -> state.getValue(BarrierBlock.WATERLOGGED) ? MapColor.WATER : MapColor.NONE)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .noTerrainParticles()
                .pushReaction(PushReaction.IMMOVEABLE)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false);
    }
}
