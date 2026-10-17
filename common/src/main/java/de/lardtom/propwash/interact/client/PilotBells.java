package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.BellRules;
import de.lardtom.propwash.interact.net.DroneBellC2S;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PilotBells {
    public static final PilotBells INSTANCE = new PilotBells();
    private static final double HIT_EPSILON = 1.0E-7;
    private static final int MAX_PENDING = 4;

    private final Vector3d velocity = new Vector3d();
    private final List<Hit> pending = new ArrayList<>();
    private final Map<Long, Long> lastHit = new HashMap<>();
    private Vec3 requested = Vec3.ZERO;
    private double fromX;
    private double fromY;
    private double fromZ;
    private boolean armed;
    private int sent;

    private PilotBells() {
    }

    public void reset() {
        this.pending.clear();
        this.lastHit.clear();
        this.armed = false;
    }

    public void beforeMove(QuadcopterEntity drone, DroneSim sim, Vec3 delta) {
        sim.getVelocity(this.velocity);
        this.requested = delta;
        this.fromX = drone.getX();
        this.fromY = drone.getY();
        this.fromZ = drone.getZ();
        this.armed = true;
    }

    public void afterMove(QuadcopterEntity drone) {
        if (!this.armed) {
            return;
        }
        this.armed = false;
        if (!drone.horizontalCollision && !drone.verticalCollision) {
            return;
        }
        Level level = drone.level();
        AABB box = drone.getBoundingBox();
        double[] actual = {drone.getX() - this.fromX, drone.getY() - this.fromY, drone.getZ() - this.fromZ};
        double[] wanted = {this.requested.x, this.requested.y, this.requested.z};
        double[] speed = {this.velocity.x, this.velocity.y, this.velocity.z};
        for (Direction.Axis axis : Direction.Axis.values()) {
            int a = axis.ordinal();
            if (wanted[a] == 0.0 || Math.abs(actual[a] - wanted[a]) <= HIT_EPSILON) {
                continue;
            }
            boolean positive = wanted[a] > 0.0;
            double closing = positive ? speed[a] : -speed[a];
            if (closing < BellRules.MIN_SPEED) {
                continue;
            }
            this.probe(level, drone, box, axis, positive, closing);
        }
    }

    private void probe(Level level, QuadcopterEntity drone, AABB box, Direction.Axis axis, boolean positive, double closing) {
        double slack = BellRules.CONTACT_SLACK;
        AABB slab = switch (axis) {
            case X -> positive ? new AABB(box.maxX, box.minY, box.minZ, box.maxX + slack, box.maxY, box.maxZ)
                    : new AABB(box.minX - slack, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            case Y -> positive ? new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + slack, box.maxZ)
                    : new AABB(box.minX, box.minY - slack, box.minZ, box.maxX, box.minY, box.maxZ);
            case Z -> positive ? new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + slack)
                    : new AABB(box.minX, box.minY, box.minZ - slack, box.maxX, box.maxY, box.minZ);
        };
        Direction face = Direction.fromAxisAndDirection(axis, positive ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        long now = level.getGameTime();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(slab.minX, slab.minY, slab.minZ),
                BlockPos.containing(slab.maxX, slab.maxY, slab.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BellBlock)) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.of(drone));
            AABB touched = null;
            for (AABB part : shape.toAabbs()) {
                AABB moved = part.move(pos);
                if (moved.intersects(slab)) {
                    touched = moved;
                    break;
                }
            }
            if (touched == null) {
                continue;
            }
            long key = pos.asLong();
            Long last = this.lastHit.get(key);
            if (!BellRules.cooledDown(last == null ? Long.MIN_VALUE : last, now)) {
                continue;
            }
            this.lastHit.put(key, now);
            Vec3 center = box.getCenter();
            double hx = axis == Direction.Axis.X ? (positive ? touched.minX : touched.maxX) : BellRules.clampInto(center.x, touched.minX, touched.maxX);
            double hy = axis == Direction.Axis.Y ? (positive ? touched.minY : touched.maxY) : BellRules.clampInto(center.y, touched.minY, touched.maxY);
            double hz = axis == Direction.Axis.Z ? (positive ? touched.minZ : touched.maxZ) : BellRules.clampInto(center.z, touched.minZ, touched.maxZ);
            if (this.pending.size() < MAX_PENDING) {
                this.pending.add(new Hit(pos.immutable(), face, hx, hy, hz, closing));
            }
        }
        if (this.lastHit.size() > 32) {
            this.lastHit.values().removeIf(tick -> now - tick > BellRules.COOLDOWN_TICKS);
        }
    }

    public void afterFrame(QuadcopterEntity drone, int epoch) {
        if (this.pending.isEmpty()) {
            return;
        }
        if (ClientBus.inSession()) {
            for (Hit hit : this.pending) {
                if (ClientNet.sendToServer(new DroneBellC2S(drone.getId(), epoch, hit.pos(), hit.face(), (float) hit.x(), (float) hit.y(),
                        (float) hit.z(), (float) hit.speed()))) {
                    this.sent++;
                }
            }
        }
        this.pending.clear();
    }

    public int sent() {
        return this.sent;
    }

    private record Hit(BlockPos pos, Direction face, double x, double y, double z, double speed) {
    }
}
