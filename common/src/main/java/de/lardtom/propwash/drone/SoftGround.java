package de.lardtom.propwash.drone;

import de.lardtom.propwash.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public final class SoftGround {
    public static final TagKey<Block> SURFACE = TagKey.create(Registries.BLOCK, Constants.id("drone_surface"));
    private static final double EDGE = 1.0E-7;
    private static final int MAX_BLOCKS = 4096;

    private static volatile boolean enabled = true;

    private SoftGround() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void collect(Level level, AABB swept, List<AABB> out) {
        if (!enabled || swept.hasNaN()) {
            return;
        }
        int x0 = Mth.floor(swept.minX - EDGE);
        int y0 = Mth.floor(swept.minY - EDGE);
        int z0 = Mth.floor(swept.minZ - EDGE);
        int x1 = Mth.floor(swept.maxX + EDGE);
        int y1 = Mth.floor(swept.maxY + EDGE);
        int z1 = Mth.floor(swept.maxZ + EDGE);
        long count = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        if (count <= 0L || count > MAX_BLOCKS) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockGetter chunk = level.getChunkForCollisions(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.isAir() || !state.is(SURFACE)) {
                        continue;
                    }
                    VoxelShape visual = state.getShape(level, pos);
                    if (visual.isEmpty()) {
                        continue;
                    }
                    for (AABB part : visual.toAabbs()) {
                        AABB placed = part.move(x, y, z);
                        if (placed.intersects(swept)) {
                            out.add(placed);
                        }
                    }
                }
            }
        }
    }
}
