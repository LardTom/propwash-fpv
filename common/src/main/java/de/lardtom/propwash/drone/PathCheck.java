package de.lardtom.propwash.drone;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class PathCheck {
    static final double CORE_FRACTION = 0.5;
    static final double MIN_CORE = 0.02;
    static final int MAX_SAMPLES = 4096;
    static final double DETOUR_DISTANCE = 4.0;
    private static final double REACH_EPSILON = 1.0E-4;
    private static final List<Direction.Axis[]> ORDERS = List.of(
            new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z},
            new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z, Direction.Axis.Y},
            new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z},
            new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X},
            new Direction.Axis[]{Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Y},
            new Direction.Axis[]{Direction.Axis.Z, Direction.Axis.Y, Direction.Axis.X});

    private PathCheck() {
    }

    static boolean clear(Level level, @Nullable Entity entity, AABB box, Vec3 delta) {
        if (delta.lengthSqr() == 0.0) {
            return true;
        }
        AABB core = core(box);
        if (straightClear(level, entity, core, delta)) {
            return true;
        }
        if (delta.lengthSqr() > DETOUR_DISTANCE * DETOUR_DISTANCE) {
            return false;
        }
        List<VoxelShape> shapes = new ArrayList<>();
        for (VoxelShape shape : level.getBlockCollisions(entity, core.expandTowards(delta))) {
            shapes.add(shape);
        }
        for (Direction.Axis[] order : ORDERS) {
            if (sweepReaches(core, delta, shapes, order)) {
                return true;
            }
        }
        return false;
    }

    static AABB core(AABB box) {
        return AABB.ofSize(box.getCenter(), Math.max(MIN_CORE, box.getXsize() * CORE_FRACTION),
                Math.max(MIN_CORE, box.getYsize() * CORE_FRACTION), Math.max(MIN_CORE, box.getZsize() * CORE_FRACTION));
    }

    static int sampleCount(AABB core, Vec3 delta) {
        double steps = Math.max(Math.abs(delta.x) / core.getXsize(),
                Math.max(Math.abs(delta.y) / core.getYsize(), Math.abs(delta.z) / core.getZsize()));
        if (!Double.isFinite(steps) || steps > MAX_SAMPLES) {
            return -1;
        }
        return Math.max(1, (int) Math.ceil(steps));
    }

    private static boolean straightClear(Level level, @Nullable Entity entity, AABB core, Vec3 delta) {
        int count = sampleCount(core, delta);
        if (count < 0) {
            return false;
        }
        for (int i = 1; i <= count; i++) {
            double t = (double) i / count;
            if (!level.noBlockCollision(entity, core.move(delta.x * t, delta.y * t, delta.z * t))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sweepReaches(AABB core, Vec3 delta, List<VoxelShape> shapes, Direction.Axis[] order) {
        AABB moving = core;
        for (Direction.Axis axis : order) {
            double wanted = delta.get(axis);
            if (wanted == 0.0) {
                continue;
            }
            double allowed = shapes.isEmpty() ? wanted : Shapes.collide(axis, moving, shapes, wanted);
            if (Math.abs(allowed - wanted) > REACH_EPSILON) {
                return false;
            }
            moving = moving.move(Vec3.ZERO.with(axis, wanted));
        }
        return true;
    }
}
