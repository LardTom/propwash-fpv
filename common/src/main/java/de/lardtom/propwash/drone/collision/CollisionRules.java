package de.lardtom.propwash.drone.collision;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.BlockContact;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.apiimpl.Api17Registries.Registered;
import de.lardtom.propwash.barrier.DroneBarriers;
import de.lardtom.propwash.sim.MoveReport;
import de.lardtom.propwash.sim.SurfaceResponse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class CollisionRules {
    public static final Evaluation SOLID = new Evaluation(CollisionFlags.SOLID, null);
    static final double BELOW_REACH = 1.0;
    static final double FACE_REACH = 0.25;
    static final double INSIDE = 1.0E-3;
    static final double EDGE = 1.0E-7;
    static final double HULL_SHRINK = 0.02;
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private CollisionRules() {
    }

    public record Evaluation(CollisionFlags flags, @Nullable Identifier rule) {
        public boolean blocks() {
            return this.flags.blocks();
        }

        public boolean solid() {
            return this.flags.solid();
        }
    }

    public record Found(BlockPos pos, BlockState state, Direction side, Vec3 normal) {
    }

    public static List<Registered<CollisionRule>> rules() {
        return Api17Registries.collisionRules();
    }

    public static List<Registered<CollisionRule>> known(List<Registered<CollisionRule>> rules, Predicate<Identifier> known) {
        if (rules.stream().allMatch(rule -> known.test(rule.id()))) {
            return rules;
        }
        return rules.stream().filter(rule -> known.test(rule.id())).toList();
    }

    public static Evaluation evaluate(List<Registered<CollisionRule>> rules, CollisionQuery query) {
        if (rules.isEmpty()) {
            return SOLID;
        }
        CollisionFlags flags = CollisionFlags.SOLID;
        Identifier first = null;
        for (Registered<CollisionRule> rule : rules) {
            CollisionFlags result;
            try {
                result = rule.value().flags(query);
            } catch (RuntimeException | LinkageError e) {
                failed(rule.id(), "flags", e);
                continue;
            }
            if (result == null) {
                continue;
            }
            if (first == null && !result.solid()) {
                first = rule.id();
            }
            flags = flags.and(result);
        }
        return first == null ? SOLID : new Evaluation(flags, first);
    }

    public static SurfaceResponse contact(List<Registered<CollisionRule>> rules, Entity drone, Found found, double impactSpeed,
                                          SurfaceResponse base, double droneRestitution) {
        BlockContact contact = new BlockContact(drone, drone.level(), found.pos(), found.state(), found.side(), found.normal(), impactSpeed,
                ContactResponse.startRestitution(base, droneRestitution), base.grip(), base.impactScale());
        for (Registered<CollisionRule> rule : rules) {
            try {
                rule.value().onBlockContact(contact);
            } catch (RuntimeException | LinkageError e) {
                failed(rule.id(), "onBlockContact", e);
            }
        }
        return ContactResponse.finish(base, contact.baseRestitution(), contact.baseGrip(), contact.baseImpactScale(), contact.restitution(),
                contact.grip(), contact.impactScale());
    }

    public static void afterSurface(List<Registered<CollisionRule>> rules, Entity drone, MoveReport report, boolean grounded,
                                    Vector3dc velocity, double droneRestitution) {
        if (rules.isEmpty()) {
            return;
        }
        Found found = find(drone.level(), drone.getBoundingBox(), report, grounded);
        if (found == null) {
            return;
        }
        Vec3 n = found.normal();
        double approach = -(velocity.x() * n.x + velocity.y() * n.y + velocity.z() * n.z);
        double speed = Double.isFinite(approach) ? Math.max(0.0, approach) : 0.0;
        SurfaceResponse base = ContactResponse.of(report);
        SurfaceResponse result = contact(rules, drone, found, speed, base, droneRestitution);
        if (result != base) {
            result.writeTo(report);
        }
    }

    public static boolean hullBlocked(Level level, Entity drone, AABB box) {
        double shrink = Math.min(HULL_SHRINK, Math.min(box.getXsize(), box.getYsize()) * 0.25);
        return !level.noBlockCollision(drone, box.deflate(shrink));
    }

    public static @Nullable Found find(Level level, AABB hull, MoveReport r, boolean grounded) {
        if (hull.hasNaN()) {
            return null;
        }
        double nx;
        double ny;
        double nz;
        if (r.obliqueContact()) {
            double length = Math.sqrt(r.contactNormalX * r.contactNormalX + r.contactNormalY * r.contactNormalY
                    + r.contactNormalZ * r.contactNormalZ);
            if (!(length > 1.0E-6)) {
                return null;
            }
            nx = r.contactNormalX / length;
            ny = r.contactNormalY / length;
            nz = r.contactNormalZ / length;
        } else {
            int axis = -1;
            double best = 0.0;
            double[] requested = {r.requestedX, r.requestedY, r.requestedZ};
            boolean[] hit = {r.hitX, r.hitY, r.hitZ};
            for (int a = 0; a < 3; a++) {
                if (hit[a] && Math.abs(requested[a]) > best) {
                    best = Math.abs(requested[a]);
                    axis = a;
                }
            }
            double[] normal = new double[3];
            if (axis < 0) {
                normal[1] = 1.0;
            } else {
                normal[axis] = -Math.signum(requested[axis]);
            }
            nx = normal[0];
            ny = normal[1];
            nz = normal[2];
        }
        Direction side = Direction.getApproximateNearest(nx, ny, nz);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        BlockState bestState = null;
        double bestArea = 0.0;
        int axis = side.getAxis().ordinal();
        int u = (axis + 1) % 3;
        int v = (axis + 2) % 3;
        double[] min = {hull.minX, hull.minY, hull.minZ};
        double[] max = {hull.maxX, hull.maxY, hull.maxZ};
        boolean outward = side.getStepX() + side.getStepY() + side.getStepZ() < 0;
        double reach = side == Direction.UP ? BELOW_REACH : FACE_REACH;
        double plane = outward ? max[axis] : min[axis];
        int first = Mth.floor(outward ? plane - INSIDE : plane + INSIDE);
        int last = Mth.floor(outward ? plane + reach : plane - reach);
        int step = outward ? 1 : -1;
        int[] cell = new int[3];
        for (int i = Mth.floor(min[u] + EDGE); i <= Mth.floor(max[u] - EDGE); i++) {
            double du = Math.min(max[u], i + 1.0) - Math.max(min[u], i);
            for (int j = Mth.floor(min[v] + EDGE); j <= Mth.floor(max[v] - EDGE); j++) {
                double dv = Math.min(max[v], j + 1.0) - Math.max(min[v], j);
                double area = du * dv;
                if (!(area > bestArea)) {
                    continue;
                }
                cell[u] = i;
                cell[v] = j;
                for (int c = first; outward ? c <= last : c >= last; c += step) {
                    cell[axis] = c;
                    BlockState state = touching(level, cursor, cell, axis, outward, plane, reach);
                    if (state != null) {
                        best = cursor.immutable();
                        bestState = state;
                        bestArea = area;
                        break;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        return new Found(best, bestState, side, new Vec3(nx, ny, nz));
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
        Direction.Axis a = Direction.Axis.values()[axis];
        if (outward) {
            double near = cell[axis] + shape.min(a);
            return near >= plane - INSIDE && near <= plane + reach ? state : null;
        }
        double near = cell[axis] + shape.max(a);
        return near <= plane + INSIDE && near >= plane - reach ? state : null;
    }

    private static void failed(Identifier id, String what, Throwable error) {
        if (FAILED.add(id + "#" + what)) {
            Constants.LOG.warn("Kollisionsregel {} ({}) fehlgeschlagen, wird ignoriert", id, what, error);
        }
    }
}
