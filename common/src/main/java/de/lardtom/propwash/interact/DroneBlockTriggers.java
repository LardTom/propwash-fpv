package de.lardtom.propwash.interact;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class DroneBlockTriggers {
    public static final TagKey<Block> DRONE_TRIGGERS = TagKey.create(Registries.BLOCK, Constants.id("drone_triggers"));
    public static final double MOVE_THRESHOLD = 1.0E-4;
    public static final double REACH = 1.0E-3;

    private static final Map<QuadcopterEntity, Vec3> LAST = new WeakHashMap<>();

    private DroneBlockTriggers() {
    }

    public static void tick(QuadcopterEntity drone, ServerLevel level) {
        if (drone.isRemoved() || drone.noPhysics) {
            return;
        }
        Vec3 now = drone.position();
        if (!moved(LAST.get(drone), now)) {
            return;
        }
        LAST.put(drone, now);
        AABB box = drone.getBoundingBox().inflate(REACH);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);
        for (int x = Mth.floor(box.minX); x <= maxX; x++) {
            for (int y = Mth.floor(box.minY); y <= maxY; y++) {
                for (int z = Mth.floor(box.minZ); z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(DRONE_TRIGGERS) && touches(state, level, cursor, drone, box)) {
                        state.entityInside(level, cursor.immutable(), drone, InsideBlockEffectApplier.NOOP, true);
                        if (drone.isRemoved()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    public static boolean moved(@Nullable Vec3 previous, Vec3 now) {
        return previous == null || previous.distanceToSqr(now) > MOVE_THRESHOLD * MOVE_THRESHOLD;
    }

    private static boolean touches(BlockState state, ServerLevel level, BlockPos pos, QuadcopterEntity drone, AABB box) {
        VoxelShape shape = state.getEntityInsideCollisionShape(level, pos, drone);
        if (shape == Shapes.block()) {
            return true;
        }
        for (AABB part : shape.toAabbs()) {
            if (part.move(pos).intersects(box)) {
                return true;
            }
        }
        return false;
    }
}
