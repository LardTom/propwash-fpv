package de.lardtom.propwash.drone;

import de.lardtom.propwash.core.ExtraCollider;
import de.lardtom.propwash.core.OrientedBox;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.SimEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class DroneGround {
    public static final double START = 0.01;
    private static final double MARGIN = 0.05;
    static final int TALL_SHAPE_REACH = 1;

    private final List<OrientedBox> colliders = new ArrayList<>();
    private final List<AABB> aligned = new ArrayList<>();
    private final List<AABB> soft = new ArrayList<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Vector3f point = new Vector3f();

    public void collect(Level level, Entity drone, double x, double y, double z, double halfWidth, double depth) {
        this.colliders.clear();
        this.aligned.clear();
        if (drone.noPhysics) {
            return;
        }
        double reach = halfWidth + MARGIN;
        AABB area = new AABB(x - reach, y - depth, z - reach, x + reach, y + MARGIN, z + reach);
        List<Entity> found = level.getEntities(drone, area.inflate(ExtraCollider.reach()), e -> e instanceof ExtraCollider);
        for (Entity entity : found) {
            ((ExtraCollider) entity).collectOrientedColliders(area, this.aligned, this.colliders);
        }
        for (AABB box : this.aligned) {
            this.colliders.add(OrientedBox.ofBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
    }

    public double colliderDistance(double x, double y, double z, double range) {
        double top = this.colliderTop(x, y, z, range);
        return top > Double.NEGATIVE_INFINITY ? Math.max(0.0, y - top) : Double.POSITIVE_INFINITY;
    }

    private double colliderTop(double x, double y, double z, double range) {
        double best = Double.NEGATIVE_INFINITY;
        for (OrientedBox box : this.colliders) {
            double t = box.clip(x, y, z, x, y - range, z);
            if (t < 0.0) {
                continue;
            }
            if (box.contains(x, y, z)) {
                return Double.NaN;
            }
            best = Math.max(best, y - t * range);
        }
        return best;
    }

    public void fillSupport(Level level, Entity drone, double x, double bottom, double z, double width, SimEnvironment env) {
        int side = SimEnvironment.SUPPORT_SIDE;
        double spacing = SimEnvironment.supportSpacing(width);
        double start = bottom + START;
        double range = SimEnvironment.SUPPORT_RANGE;
        double half = 0.5 * width;
        this.soft.clear();
        SoftGround.collect(level, new AABB(x - half, start - range, z - half, x + half, start, z + half), this.soft);
        env.supportSpacing = spacing;
        env.supportX = x;
        env.supportZ = z;
        CollisionContext context = CollisionContext.of(drone);
        Function<BlockPos, VoxelShape> shapes = pos -> level.getBlockState(pos).getCollisionShape(level, pos, context);
        for (int iz = 0; iz < side; iz++) {
            for (int ix = 0; ix < side; ix++) {
                double cx = x + SimEnvironment.supportOffset(ix, spacing);
                double cz = z + SimEnvironment.supportOffset(iz, spacing);
                env.support[SimEnvironment.supportCell(ix, iz)] = this.column(shapes, cx, start, cz, range);
            }
        }
    }

    public void fillPoints(Level level, Entity drone, AirframeSpec spec, Quaternionfc orientation, double x, double bottom, double z,
                           SimEnvironment env) {
        int count = Math.min(spec.standPointCount(), SimEnvironment.POINT_SUPPORT);
        env.pointCount = 0;
        if (count <= 0 || !(env.supportSpacing > 0.0)) {
            return;
        }
        double start = bottom + START;
        double range = SimEnvironment.SUPPORT_RANGE;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        Vector3f p = this.point;
        for (int j = 0; j < count; j++) {
            int k = AirframeSpec.ACCESSORY_GROUND + j;
            p.set((float) -spec.groundX(k), (float) spec.groundY(k), (float) -spec.groundZ(k));
            orientation.transform(p);
            env.pointX[j] = x + p.x;
            env.pointZ[j] = z + p.z;
            minX = Math.min(minX, env.pointX[j]);
            minZ = Math.min(minZ, env.pointZ[j]);
            maxX = Math.max(maxX, env.pointX[j]);
            maxZ = Math.max(maxZ, env.pointZ[j]);
        }
        this.soft.clear();
        SoftGround.collect(level, new AABB(minX, start - range, minZ, maxX, start, maxZ).inflate(MARGIN, 0.0, MARGIN), this.soft);
        CollisionContext context = CollisionContext.of(drone);
        Function<BlockPos, VoxelShape> shapes = pos -> level.getBlockState(pos).getCollisionShape(level, pos, context);
        for (int j = 0; j < count; j++) {
            env.pointSupport[j] = this.column(shapes, env.pointX[j], start, env.pointZ[j], range);
        }
        env.pointCount = count;
    }

    private double column(Function<BlockPos, VoxelShape> shapes, double x, double start, double z, double range) {
        double best = blockTop(x, start, z, range, shapes, this.cursor);
        if (Double.isNaN(best)) {
            return Double.NaN;
        }
        double gate = this.colliderTop(x, start, z, range);
        if (Double.isNaN(gate)) {
            return Double.NaN;
        }
        best = Math.max(best, gate);
        for (AABB box : this.soft) {
            if (x <= box.minX || x >= box.maxX || z <= box.minZ || z >= box.maxZ) {
                continue;
            }
            if (box.minY < start && box.maxY > start) {
                return Double.NaN;
            }
            if (box.maxY <= start && box.maxY >= start - range) {
                best = Math.max(best, box.maxY);
            }
        }
        return best;
    }

    static double blockTop(double x, double start, double z, double range, Function<BlockPos, VoxelShape> shapes,
                           BlockPos.MutableBlockPos pos) {
        Vec3 from = new Vec3(x, start, z);
        Vec3 to = new Vec3(x, start - range, z);
        pos.set(Mth.floor(x), Mth.floor(start), Mth.floor(z));
        int lowest = Mth.floor(start - range) - TALL_SHAPE_REACH;
        double best = Double.NEGATIVE_INFINITY;
        for (int y = pos.getY(); y >= lowest; y--) {
            pos.setY(y);
            VoxelShape shape = shapes.apply(pos);
            BlockHitResult hit = shape.isEmpty() ? null : shape.clip(from, to, pos);
            if (hit == null) {
                continue;
            }
            if (hit.isInside()) {
                return Double.NaN;
            }
            best = Math.max(best, hit.getLocation().y);
        }
        return best;
    }
}
