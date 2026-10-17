package de.lardtom.propwash.interact;

import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.interact.net.DroneBellC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.server.ServerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class DroneBells {
    public static final Event<BellListener> RUNG = new Event<>("drone_bell_rung");

    private static final Map<QuadcopterEntity, Tracker> TRACKERS = new WeakHashMap<>();

    private DroneBells() {
    }

    static void reset() {
        TRACKERS.clear();
    }

    static void onBell(DroneBellC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            return;
        }
        ring(level, player, drone, payload.pos(), payload);
    }

    static BellRules.Decision ring(ServerLevel level, ServerPlayer player, QuadcopterEntity drone, BlockPos pos, DroneBellC2S payload) {
        if (!level.isLoaded(pos)) {
            return BellRules.Decision.OUT_OF_REACH;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BellBlock bell)) {
            return BellRules.Decision.OUT_OF_REACH;
        }
        if (!level.mayInteract(player, pos)) {
            return BellRules.Decision.PROTECTED;
        }
        long now = level.getGameTime();
        Tracker tracker = TRACKERS.computeIfAbsent(drone, d -> new Tracker());
        double maxSpeed = StateValidation.effectiveMaxSpeed(ServerRules.maxDroneSpeed(player));
        double accepted = StrikeRules.acceptedSpeed(payload.speed(), DroneStrikes.recentPeak(drone, now), maxSpeed);
        AABB a = drone.getBoundingBox();
        double gap = StrikeRules.gap(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ, pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        BellRules.Decision decision = BellRules.decide(payload.speed(), tracker.cooledDown(pos, now), tracker.withinBudget(now), gap, accepted);
        if (decision != BellRules.Decision.RING) {
            return decision;
        }
        tracker.mark(pos, now);
        double s = BellRules.HIT_SLACK;
        Vec3 location = new Vec3(BellRules.clampInto(payload.hitX(), pos.getX() - s, pos.getX() + 1.0 + s),
                BellRules.clampInto(payload.hitY(), pos.getY(), pos.getY() + 1.0),
                BellRules.clampInto(payload.hitZ(), pos.getZ() - s, pos.getZ() + 1.0 + s));
        boolean proper = bell.onHit(level, state, new BlockHitResult(location, payload.face(), pos, false), player, true);
        if (proper) {
            RUNG.fire(listener -> listener.onRung(drone, player, pos.immutable()));
        }
        return decision;
    }

    @FunctionalInterface
    public interface BellListener {
        void onRung(QuadcopterEntity drone, ServerPlayer pilot, BlockPos pos);
    }

    private static final class Tracker {
        private final Map<Long, Long> lastRing = new HashMap<>();
        private long windowStart = Long.MIN_VALUE;
        private int used;

        boolean cooledDown(BlockPos pos, long now) {
            return BellRules.cooledDown(this.lastRing.getOrDefault(pos.asLong(), Long.MIN_VALUE), now);
        }

        boolean withinBudget(long now) {
            if (this.windowStart == Long.MIN_VALUE || now - this.windowStart >= 20 || now < this.windowStart) {
                this.windowStart = now;
                this.used = 0;
            }
            return this.used < BellRules.RINGS_PER_SECOND;
        }

        void mark(BlockPos pos, long now) {
            this.used++;
            this.lastRing.put(pos.asLong(), now);
            if (this.lastRing.size() > 32) {
                this.lastRing.values().removeIf(tick -> now - tick > BellRules.COOLDOWN_TICKS);
            }
        }
    }
}
