package de.lardtom.propwash.race;

import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public final class FreeformBlocks {
    private FreeformBlocks() {
    }

    public static FreeformFill.Probe probe(Level level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            if (y < level.getMinY() || y > level.getMaxY()) {
                return FreeformFill.Cell.OUTSIDE;
            }
            pos.set(x, y, z);
            if (!level.isLoaded(pos)) {
                return FreeformFill.Cell.UNLOADED;
            }
            if (!level.getWorldBorder().isWithinBounds(pos)) {
                return FreeformFill.Cell.OUTSIDE;
            }
            return passable(level, pos, level.getBlockState(pos)) ? FreeformFill.Cell.OPEN : FreeformFill.Cell.SOLID;
        };
    }

    public static boolean passable(Level level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    public static int limit(Level level) {
        return WorldRules.values(level).get(WorldRuleSpecs.FREEFORM_GATE_MAX_SIZE);
    }

    public static FreeformFill.Aim aim(Level level, BlockPos clicked, Direction face, Vec3 look, boolean reverse) {
        boolean open = passable(level, clicked, level.getBlockState(clicked));
        return FreeformFill.aim(clicked.getX(), clicked.getY(), clicked.getZ(), face.getStepX(), face.getStepY(), face.getStepZ(), open, look.x, look.y,
                look.z, reverse);
    }

    public static FreeformFill.Result fill(Level level, FreeformFill.Aim aim) {
        return FreeformFill.fill(probe(level), aim.axis(), aim.seedX(), aim.seedY(), aim.seedZ(), limit(level));
    }

    public static FreeformFill.Result rescan(Level level, GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        if (region == null) {
            return new FreeformFill.Result(FreeformFill.Status.START_BLOCKED, null, FreeformRegion.AXIS_Z, 0, 0, 0, 1, 1, limit(level));
        }
        int[] seed = seed(geometry);
        return FreeformFill.fill(probe(level), region.axis(), seed[0], seed[1], seed[2], limit(level));
    }

    public static int plane(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        int axis = region == null ? FreeformRegion.AXIS_Z : region.axis();
        return (int) Math.floor(component(geometry.x(), geometry.y(), geometry.z(), axis));
    }

    public static int minA(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        int axis = region == null ? FreeformRegion.AXIS_Z : region.axis();
        return (int) Math.round(component(geometry.originX(), geometry.originY(), geometry.originZ(), FreeformRegion.aAxis(axis)));
    }

    public static int minB(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        int axis = region == null ? FreeformRegion.AXIS_Z : region.axis();
        return (int) Math.round(component(geometry.originX(), geometry.originY(), geometry.originZ(), FreeformRegion.bAxis(axis)));
    }

    public static int[] seed(GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        if (region == null) {
            return new int[]{(int) Math.floor(geometry.x()), (int) Math.floor(geometry.y()), (int) Math.floor(geometry.z())};
        }
        return world(region.axis(), plane(geometry), minA(geometry) + region.seedA(), minB(geometry) + region.seedB());
    }

    public static int[] world(int axis, int plane, int a, int b) {
        return switch (axis) {
            case FreeformRegion.AXIS_X -> new int[]{plane, b, a};
            case FreeformRegion.AXIS_Y -> new int[]{a, plane, b};
            default -> new int[]{a, b, plane};
        };
    }

    public static double component(double x, double y, double z, int axis) {
        return switch (axis) {
            case FreeformRegion.AXIS_X -> x;
            case FreeformRegion.AXIS_Y -> y;
            default -> z;
        };
    }

    public static FreeformFill.Integrity integrity(Level level, GateGeometry geometry) {
        FreeformRegion region = geometry.region();
        if (region == null) {
            return FreeformFill.Integrity.INTACT;
        }
        return FreeformFill.check(probe(level), region, plane(geometry), minA(geometry), minB(geometry));
    }

    public static boolean covers(GateGeometry geometry, int x, int y, int z) {
        FreeformRegion region = geometry.region();
        if (region == null) {
            return false;
        }
        int axis = region.axis();
        int[] at = {x, y, z};
        if (at[axis] != plane(geometry)) {
            return false;
        }
        return region.contains(at[FreeformRegion.aAxis(axis)] - minA(geometry), at[FreeformRegion.bAxis(axis)] - minB(geometry));
    }

    public static @Nullable GateEntity occupant(Level level, int x, int y, int z, @Nullable Entity ignore) {
        AABB area = new AABB(x, y, z, x + 1, y + 1, z + 1).inflate(FreeformReach.reach() + 2.0);
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, area, gate -> gate != ignore && !gate.isRemoved() && gate.freeform())) {
            if (covers(gate.geometry(), x, y, z)) {
                return gate;
            }
        }
        return null;
    }
}
