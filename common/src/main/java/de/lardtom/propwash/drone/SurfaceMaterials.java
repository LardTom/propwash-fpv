package de.lardtom.propwash.drone;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.barrier.DroneBarriers;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.SurfaceResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class SurfaceMaterials {
    public static final TagKey<Block> BOUNCY = TagKey.create(Registries.BLOCK, Constants.id("drone_bouncy"));
    public static final TagKey<Block> CUSHION = TagKey.create(Registries.BLOCK, Constants.id("drone_cushion"));
    public static final TagKey<Block> STICKY = TagKey.create(Registries.BLOCK, Constants.id("drone_sticky"));
    static final double BELOW_REACH = 1.0;
    static final double FACE_REACH = 0.25;
    static final double INSIDE = 1.0E-3;
    static final double EDGE = 1.0E-7;
    static final double OBLIQUE_AXIS = 0.35;
    static final double STICKY_GAP = 0.03;
    static final double STICKY_DEPTH = 0.01;

    private SurfaceMaterials() {
    }

    public static SurfaceResponse response(BlockState state) {
        if (state.is(CUSHION)) {
            return SurfaceResponse.CUSHION;
        }
        if (state.is(BOUNCY)) {
            return SurfaceResponse.BOUNCY;
        }
        if (state.is(STICKY)) {
            return SurfaceResponse.STICKY;
        }
        return SurfaceResponse.normal(state.getBlock().getFriction());
    }

    public static void fill(Level level, AABB hitbox, MoveReport report, boolean grounded) {
        double sx = side(report.hitX, report.requestedX, report.contactNormalX);
        double sy = side(report.hitY, report.requestedY, report.contactNormalY);
        double sz = side(report.hitZ, report.requestedZ, report.contactNormalZ);
        if (sy == 0.0 && (grounded || sx == 0.0 && sz == 0.0)) {
            sy = -1.0;
        }
        SurfaceResponse response = at(level, hitbox, sx, sy, sz);
        if (response != null) {
            response.writeTo(report);
        }
    }

    static double side(boolean hit, double requested, double normal) {
        if (hit && requested != 0.0) {
            return Math.signum(requested);
        }
        if (Double.isFinite(normal) && Math.abs(normal) > OBLIQUE_AXIS) {
            return -Math.signum(normal);
        }
        return 0.0;
    }

    public static @Nullable SurfaceResponse at(Level level, AABB hitbox, double hitX, double hitY, double hitZ) {
        if (hitbox.hasNaN()) {
            return null;
        }
        SurfaceResponse.Mix mix = new SurfaceResponse.Mix();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] min = {hitbox.minX, hitbox.minY, hitbox.minZ};
        double[] max = {hitbox.maxX, hitbox.maxY, hitbox.maxZ};
        face(level, mix, pos, min, max, 0, hitX, FACE_REACH);
        face(level, mix, pos, min, max, 1, hitY, hitY < 0.0 ? BELOW_REACH : FACE_REACH);
        face(level, mix, pos, min, max, 2, hitZ, FACE_REACH);
        return mix.result();
    }

    private static void face(Level level, SurfaceResponse.Mix mix, BlockPos.MutableBlockPos pos, double[] min, double[] max, int axis,
                             double side, double reach) {
        if (side == 0.0 || !Double.isFinite(side)) {
            return;
        }
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        boolean outward = side > 0.0;
        double plane = outward ? max[axis] : min[axis];
        int first = Mth.floor(outward ? plane - INSIDE : plane + INSIDE);
        int last = Mth.floor(outward ? plane + reach : plane - reach);
        int step = outward ? 1 : -1;
        int[] cell = new int[3];
        for (int i = Mth.floor(min[u] + EDGE); i <= Mth.floor(max[u] - EDGE); i++) {
            double du = Math.min(max[u], i + 1.0) - Math.max(min[u], i);
            for (int j = Mth.floor(min[v] + EDGE); j <= Mth.floor(max[v] - EDGE); j++) {
                double dv = Math.min(max[v], j + 1.0) - Math.max(min[v], j);
                if (!(du > 0.0) || !(dv > 0.0)) {
                    continue;
                }
                cell[u] = i;
                cell[v] = j;
                for (int c = first; outward ? c <= last : c >= last; c += step) {
                    cell[axis] = c;
                    BlockState state = touching(level, pos, cell, axis, outward, plane, reach);
                    if (state != null) {
                        mix.add(response(state), du * dv);
                        break;
                    }
                }
            }
        }
    }

    private static @Nullable BlockState touching(Level level, BlockPos.MutableBlockPos pos, int[] cell, int axis, boolean outward,
                                                 double plane, double reach) {
        pos.set(cell[0], cell[1], cell[2]);
        if (level.isOutsideBuildHeight(pos)
                || !level.hasChunk(SectionPos.blockToSectionCoord(cell[0]), SectionPos.blockToSectionCoord(cell[2]))) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        VoxelShape shape = DroneBarriers.droneShape(state, level, pos);
        if (shape.isEmpty()) {
            return null;
        }
        double base = cell[axis];
        if (outward) {
            double near = base + shape.min(axis(axis));
            return near >= plane - INSIDE && near <= plane + reach ? state : null;
        }
        double near = base + shape.max(axis(axis));
        return near <= plane + INSIDE && near >= plane - reach ? state : null;
    }

    public static void sticky(Level level, AABB hitbox, MoveReport report) {
        report.stickySides = stickySides(level, hitbox);
    }

    public static int stickySides(Level level, AABB hitbox) {
        if (hitbox.hasNaN()) {
            return 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] min = {hitbox.minX, hitbox.minY, hitbox.minZ};
        double[] max = {hitbox.maxX, hitbox.maxY, hitbox.maxZ};
        int sides = 0;
        for (int axis = 0; axis < 3; axis++) {
            for (int s = 0; s < 2; s++) {
                boolean outward = s == 1;
                if (stickyFace(level, pos, min, max, axis, outward)) {
                    sides |= MoveReport.stickyBit(axis, outward);
                }
            }
        }
        return sides;
    }

    private static boolean stickyFace(Level level, BlockPos.MutableBlockPos pos, double[] min, double[] max, int axis, boolean outward) {
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        double plane = outward ? max[axis] : min[axis];
        int first = Mth.floor(outward ? plane - STICKY_DEPTH : plane + STICKY_DEPTH);
        int last = Mth.floor(outward ? plane + STICKY_GAP : plane - STICKY_GAP);
        int step = outward ? 1 : -1;
        int[] cell = new int[3];
        for (int i = Mth.floor(min[u] + EDGE); i <= Mth.floor(max[u] - EDGE); i++) {
            for (int j = Mth.floor(min[v] + EDGE); j <= Mth.floor(max[v] - EDGE); j++) {
                cell[u] = i;
                cell[v] = j;
                for (int c = first; outward ? c <= last : c >= last; c += step) {
                    cell[axis] = c;
                    pos.set(cell[0], cell[1], cell[2]);
                    if (level.isOutsideBuildHeight(pos)
                            || !level.hasChunk(SectionPos.blockToSectionCoord(cell[0]), SectionPos.blockToSectionCoord(cell[2]))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(STICKY)) {
                        continue;
                    }
                    if (stickyShape(state.getCollisionShape(level, pos), c, axis, outward, plane)
                            || stickyShape(state.getShape(level, pos), c, axis, outward, plane)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean stickyShape(VoxelShape shape, int base, int axis, boolean outward, double plane) {
        if (shape.isEmpty()) {
            return false;
        }
        if (outward) {
            double near = base + shape.min(axis(axis));
            return near >= plane - STICKY_DEPTH && near <= plane + STICKY_GAP;
        }
        double near = base + shape.max(axis(axis));
        return near <= plane + STICKY_DEPTH && near >= plane - STICKY_GAP;
    }

    private static Direction.Axis axis(int axis) {
        return axis == 0 ? Direction.Axis.X : axis == 1 ? Direction.Axis.Y : Direction.Axis.Z;
    }
}
