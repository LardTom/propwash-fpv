package de.lardtom.propwash.interact;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.FreeformBlocks;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BreakableBlocks {
    public static final TagKey<Block> DRONE_BREAKABLE = TagKey.create(Registries.BLOCK, Constants.id("drone_breakable"));

    private BreakableBlocks() {
    }

    public static boolean candidate(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.is(DRONE_BREAKABLE) || state.getBlock() instanceof GameMasterBlock) {
            return false;
        }
        return BreakMath.fragile(state.getDestroySpeed(level, pos));
    }

    public static double depth(Level level, BlockPos pos, BlockState state, Direction.Axis axis) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return 1.0;
        }
        return shape.max(axis) - shape.min(axis);
    }

    public static double threshold(Level level, BlockPos pos, BlockState state, Direction.Axis axis) {
        if (!candidate(level, pos, state)) {
            return Double.POSITIVE_INFINITY;
        }
        return BreakMath.threshold(state.getDestroySpeed(level, pos), depth(level, pos, state, axis));
    }

    public static boolean courseFrame(Level level, BlockPos pos) {
        int[] cell = {pos.getX(), pos.getY(), pos.getZ()};
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, new AABB(pos).inflate(1.0), GateEntity::freeform)) {
            GateGeometry geometry = gate.geometry();
            FreeformRegion region = geometry.region();
            if (region == null || cell[region.axis()] != FreeformBlocks.plane(geometry)) {
                continue;
            }
            int a = cell[FreeformRegion.aAxis(region.axis())] - FreeformBlocks.minA(geometry);
            int b = cell[FreeformRegion.bAxis(region.axis())] - FreeformBlocks.minB(geometry);
            if (BreakRules.frame(region, a, b)) {
                return true;
            }
        }
        return false;
    }
}
