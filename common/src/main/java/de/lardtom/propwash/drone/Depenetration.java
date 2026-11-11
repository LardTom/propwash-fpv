package de.lardtom.propwash.drone;

import de.lardtom.propwash.interact.SnagBlocks;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Depenetration {
    public static final double TOLERANCE = 1.0E-3;
    public static final double REACH = 1.5;
    public static final double CLEARANCE = 1.0E-4;
    static final int MAX_BLOCKS = 4096;
    private static final double TIE = 1.0E-6;
    private static final Vec3[] DIRECTIONS = {
            new Vec3(0.0, 1.0, 0.0),
            new Vec3(1.0, 0.0, 0.0),
            new Vec3(-1.0, 0.0, 0.0),
            new Vec3(0.0, 0.0, 1.0),
            new Vec3(0.0, 0.0, -1.0),
            new Vec3(0.0, -1.0, 0.0)};

    @FunctionalInterface
    public interface Solids {
        boolean collect(AABB area, List<AABB> out);
    }

    private Depenetration() {
    }

    public static boolean escape(Entity entity, DroneSim sim) {
        if (entity.noPhysics || sim.telemetry().sleeping) {
            return false;
        }
        Vec3 out = resolve(entity.level(), entity, entity.getBoundingBox());
        if (out == null) {
            return false;
        }
        entity.setPos(entity.getX() + out.x, entity.getY() + out.y, entity.getZ() + out.z);
        sim.pushOut(out.x, out.y, out.z);
        return true;
    }

    public static @Nullable Vec3 resolve(Level level, Entity entity, AABB box) {
        for (int x = SectionPos.blockToSectionCoord(Mth.floor(box.minX)); x <= SectionPos.blockToSectionCoord(Mth.floor(box.maxX)); x++) {
            for (int z = SectionPos.blockToSectionCoord(Mth.floor(box.minZ)); z <= SectionPos.blockToSectionCoord(Mth.floor(box.maxZ)); z++) {
                if (!level.hasChunk(x, z)) {
                    return null;
                }
            }
        }
        return resolve(box, (area, out) -> collect(level, entity, area, out));
    }

    public static @Nullable Vec3 resolve(AABB box, Solids world) {
        if (box.hasNaN()) {
            return null;
        }
        AABB core = box.deflate(TOLERANCE);
        List<AABB> touched = new ArrayList<>();
        if (!world.collect(core, touched) || free(core, touched)) {
            return null;
        }
        AABB area = box.inflate(REACH);
        List<AABB> solids = new ArrayList<>();
        if (!world.collect(area, solids)) {
            return null;
        }
        List<AABB> inside = new ArrayList<>();
        for (AABB solid : solids) {
            if (solid.intersects(core)) {
                inside.add(solid);
            }
        }
        Vec3 best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Vec3 direction : DIRECTIONS) {
            double distance = exit(box, direction, REACH, solids, inside);
            if (distance < bestDistance - TIE) {
                bestDistance = distance;
                best = direction;
            }
        }
        return best == null ? null : best.scale(bestDistance);
    }

    static double exit(AABB box, Vec3 direction, double limit, List<AABB> solids, List<AABB> inside) {
        List<Double> steps = new ArrayList<>();
        for (AABB solid : solids) {
            double step = clearing(box, direction, solid);
            if (step > 0.0 && step + CLEARANCE <= limit) {
                steps.add(step + CLEARANCE);
            }
        }
        steps.sort(Double::compare);
        for (double step : steps) {
            Vec3 shift = direction.scale(step);
            if (!free(box.move(shift), solids)) {
                continue;
            }
            if (crosses(box.expandTowards(shift).deflate(TOLERANCE), solids, inside)) {
                return Double.POSITIVE_INFINITY;
            }
            return step;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean crosses(AABB swept, List<AABB> solids, List<AABB> inside) {
        for (AABB solid : solids) {
            if (solid.intersects(swept) && !inside.contains(solid)) {
                return true;
            }
        }
        return false;
    }

    private static double clearing(AABB box, Vec3 direction, AABB solid) {
        if (direction.x > 0.0) {
            return solid.maxX - box.minX;
        }
        if (direction.x < 0.0) {
            return box.maxX - solid.minX;
        }
        if (direction.y > 0.0) {
            return solid.maxY - box.minY;
        }
        if (direction.y < 0.0) {
            return box.maxY - solid.minY;
        }
        if (direction.z > 0.0) {
            return solid.maxZ - box.minZ;
        }
        return box.maxZ - solid.minZ;
    }

    static boolean free(AABB box, List<AABB> solids) {
        for (AABB solid : solids) {
            if (solid.intersects(box)) {
                return false;
            }
        }
        return true;
    }

    static boolean collect(Level level, Entity entity, AABB area, List<AABB> out) {
        int x0 = Mth.floor(area.minX);
        int y0 = Mth.floor(area.minY);
        int z0 = Mth.floor(area.minZ);
        int x1 = Mth.floor(area.maxX);
        int y1 = Mth.floor(area.maxY);
        int z1 = Mth.floor(area.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > MAX_BLOCKS) {
            return false;
        }
        CollisionContext context = CollisionContext.of(entity);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean loaded = level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
                for (int y = y0; y <= y1; y++) {
                    if (!loaded || level.isOutsideBuildHeight(y)) {
                        if (!loaded) {
                            out.add(new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0));
                        }
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(SnagBlocks.POWDER_SNOW)) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(level, cursor, context);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (AABB part : shape.toAabbs()) {
                        AABB placed = part.move(x, y, z);
                        if (placed.intersects(area)) {
                            out.add(placed);
                        }
                    }
                }
            }
        }
        return true;
    }
}
