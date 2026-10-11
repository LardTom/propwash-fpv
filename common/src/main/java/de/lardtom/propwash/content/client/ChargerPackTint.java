package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryChargerBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public record ChargerPackTint(int bay) implements BlockTintSource {
    public static final BatteryType FALLBACK = BatteryType.B6S_1300;

    public static List<BlockTintSource> layers() {
        List<BlockTintSource> layers = new ArrayList<>();
        for (int bay = 0; bay < BatteryChargerBlockEntity.SLOTS; bay++) {
            layers.add(new ChargerPackTint(bay));
        }
        return layers;
    }

    public static int colorOf(BatteryType type) {
        return ARGB.opaque(Batteries.packColor(type));
    }

    @Override
    public int color(BlockState state) {
        return colorOf(FALLBACK);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof BatteryChargerBlockEntity charger) {
            return colorOf(Batteries.typeOf(charger.getItem(this.bay)).orElse(FALLBACK));
        }
        return this.color(state);
    }

    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return -1;
    }
}
