package de.lardtom.propwash.interact;

import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.interact.net.DroneBreakC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearC2S;
import de.lardtom.propwash.interact.net.DroneBreakClearS2C;
import de.lardtom.propwash.interact.net.DroneBreakDeniedS2C;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class DroneBreaks {
    public static final Event<BreakListener> BROKEN = new Event<>("drone_broke_block");
    public static final Event<DenyListener> DENIED = new Event<>("drone_break_denied");
    public static final Event<Guard> GUARDS = new Event<>("drone_break_guard");
    public static final double CLEAR_RANGE = 32.0;
    public static final double MAX_CLEAR_RANGE = 64.0;
    public static final double CLEAR_LAG_SECONDS = 0.5;
    public static final int CLEARED_BLOCKS_PER_SECOND = 640;

    private static final double RESYNC_RANGE = 16.0;
    private static final Direction.Axis[] AXES = Direction.Axis.values();
    private static final Map<QuadcopterEntity, Budget> BUDGETS = new WeakHashMap<>();
    private static final Map<QuadcopterEntity, Budget> CLEAR_BUDGETS = new WeakHashMap<>();

    private DroneBreaks() {
    }

    static void reset() {
        BUDGETS.clear();
        CLEAR_BUDGETS.clear();
    }

    static void onClear(DroneBreakClearC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        QuadcopterEntity drone = level.getEntity(payload.droneId()) instanceof QuadcopterEntity found && !found.isRemoved() ? found : null;
        if (drone == null || !drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch() || payload.positions().isEmpty()) {
            return;
        }
        Budget budget = CLEAR_BUDGETS.computeIfAbsent(drone, d -> new Budget());
        int granted = budget.take(level.getGameTime(), CLEARED_BLOCKS_PER_SECOND, payload.positions().size());
        if (granted <= 0) {
            return;
        }
        PropwashNet.sendToPlayer(player, new DroneBreakClearS2C(clear(level, player, drone, payload.positions().subList(0, granted))));
    }

    public static double clearRange(double speed) {
        double lag = Double.isFinite(speed) ? Math.max(0.0, speed) * CLEAR_LAG_SECONDS : 0.0;
        return Math.min(MAX_CLEAR_RANGE, CLEAR_RANGE + lag);
    }

    public static List<DroneBreakClearS2C.Entry> clear(ServerLevel level, ServerPlayer pilot, QuadcopterEntity drone, List<BlockPos> positions) {
        List<DroneBreakClearS2C.Entry> entries = new ArrayList<>(positions.size());
        BlockPos guarded = null;
        for (BlockPos pos : positions) {
            BreakRules.Outcome outcome;
            if (guarded != null && guarded.distSqr(pos) <= BreakRules.PROTECTED_RADIUS * BreakRules.PROTECTED_RADIUS) {
                outcome = BreakRules.Outcome.PROTECTED;
            } else {
                outcome = clearance(level, pilot, drone, pos);
                if (outcome == BreakRules.Outcome.PROTECTED) {
                    guarded = pos;
                }
            }
            entries.add(new DroneBreakClearS2C.Entry(pos, outcome));
        }
        return entries;
    }

    public static BreakRules.Outcome clearance(ServerLevel level, ServerPlayer pilot, QuadcopterEntity drone, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return BreakRules.Outcome.TOO_FAR;
        }
        double range = clearRange(drone.getDeltaMovement().length() * 20.0);
        boolean near = drone.distanceToSqr(Vec3.atCenterOf(pos)) <= range * range;
        BlockState state = level.getBlockState(pos);
        boolean breakable = BreakableBlocks.candidate(level, pos, state);
        boolean frame = breakable && BreakableBlocks.courseFrame(level, pos);
        BreakRules.Outcome outcome = BreakRules.decide(InteractRules.dronesBreakBlocks(level.getServer()), breakable, true, near, true, frame,
                pilot.blockActionRestricted(level, pos, pilot.gameMode()), level.mayInteract(pilot, pos));
        if (outcome != BreakRules.Outcome.BREAK) {
            return outcome;
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (!guarded(level, pilot, pos) || !Services.PLATFORM.probeBlockBreak(level, pilot, pos, state, blockEntity)) {
            return BreakRules.Outcome.PROTECTED;
        }
        return BreakRules.Outcome.BREAK;
    }

    private static boolean guarded(ServerLevel level, ServerPlayer pilot, BlockPos pos) {
        boolean[] allowed = {true};
        GUARDS.fire(guard -> allowed[0] &= guard.allows(level, pilot, pos));
        return allowed[0];
    }

    static void onBreak(DroneBreakC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        QuadcopterEntity drone = level.getEntity(payload.droneId()) instanceof QuadcopterEntity found && !found.isRemoved() ? found : null;
        boolean near = player.distanceToSqr(center) <= RESYNC_RANGE * RESYNC_RANGE
                || drone != null && drone.distanceToSqr(center) <= RESYNC_RANGE * RESYNC_RANGE;
        if (drone == null || !drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            if (near) {
                resync(player, level, pos);
            }
            return;
        }
        if (!near) {
            return;
        }
        BreakRules.Outcome outcome = attempt(level, player, drone, pos, new Vec3(payload.vx(), payload.vy(), payload.vz()), payload.axis(),
                payload.positive());
        if (outcome != BreakRules.Outcome.BREAK) {
            resync(player, level, pos);
            PropwashNet.sendToPlayer(player, new DroneBreakDeniedS2C(pos, outcome));
        }
    }

    public static BreakRules.Outcome attempt(ServerLevel level, ServerPlayer pilot, QuadcopterEntity drone, BlockPos pos, Vec3 claimed, int axis,
                                             boolean positive) {
        BlockState state = level.getBlockState(pos);
        long now = level.getGameTime();
        Budget budget = BUDGETS.computeIfAbsent(drone, d -> new Budget());
        boolean rule = InteractRules.dronesBreakBlocks(level.getServer());
        boolean breakable = BreakableBlocks.candidate(level, pos, state);
        double claimedSpeed = claimed.length();
        double maxSpeed = StateValidation.effectiveMaxSpeed(ServerRules.maxDroneSpeed(pilot));
        double speed = Double.isFinite(claimedSpeed) ? StrikeRules.acceptedSpeed(claimedSpeed, DroneStrikes.recentPeak(drone, now), maxSpeed) : 0.0;
        Vec3 velocity = claimedSpeed > 1.0E-6 && Double.isFinite(claimedSpeed) ? claimed.scale(speed / claimedSpeed) : Vec3.ZERO;
        AABB box = drone.getBoundingBox();
        double gap = StrikeRules.gap(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        Direction.Axis along = AXES[Math.max(0, Math.min(2, axis))];
        double closing = (positive ? 1.0 : -1.0) * velocity.get(along);
        double massKg = AirframeSpec.of(drone.build()).massKg + drone.cargoGrams() / 1000.0;
        double threshold = breakable ? BreakableBlocks.threshold(level, pos, state, along) : Double.POSITIVE_INFINITY;
        BreakMath.Impact impact = BreakMath.of(massKg, closing, threshold, BreakMath.SERVER_SLACK);
        boolean frame = breakable && BreakableBlocks.courseFrame(level, pos);
        BreakRules.Outcome outcome = BreakRules.decide(rule, breakable, budget.available(now, BreakRules.BREAKS_PER_SECOND), StrikeRules.withinReach(gap, speed), impact.breaks(),
                frame, pilot.blockActionRestricted(level, pos, pilot.gameMode()), level.mayInteract(pilot, pos));
        if (outcome == BreakRules.Outcome.BREAK) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            if (!guarded(level, pilot, pos) || !Services.PLATFORM.allowBlockBreak(level, pilot, pos, state, blockEntity)) {
                outcome = BreakRules.Outcome.PROTECTED;
            }
        }
        if (outcome != BreakRules.Outcome.BREAK) {
            BreakRules.Outcome denied = outcome;
            DENIED.fire(listener -> listener.onDenied(drone, pilot, pos, denied));
            return outcome;
        }
        budget.use();
        shatter(level, pilot, drone, pos, state);
        BROKEN.fire(listener -> listener.onBroken(drone, pilot, pos, state, impact));
        return outcome;
    }

    private static void shatter(ServerLevel level, ServerPlayer pilot, QuadcopterEntity drone, BlockPos pos, BlockState state) {
        BlockState broken = state;
        if (state.hasProperty(BlockStateProperties.CRACKED) && !state.getValue(BlockStateProperties.CRACKED)) {
            broken = state.setValue(BlockStateProperties.CRACKED, true);
            level.setBlock(pos, broken, Block.UPDATE_NONE);
        }
        BlockEntity blockEntity = broken.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        level.levelEvent(pilot, LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos, Block.getId(broken));
        if (!pilot.preventsBlockDrops()) {
            Block.dropResources(broken, level, pos, blockEntity, pilot, ItemStack.EMPTY);
        }
        if (level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), Block.UPDATE_ALL)) {
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(drone, broken));
            Services.PLATFORM.afterBlockBreak(level, pilot, pos, broken, blockEntity);
        }
    }

    private static void resync(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Packet<ClientGamePacketListener> data = blockEntity == null ? null : blockEntity.getUpdatePacket();
        if (data != null) {
            player.connection.send(data);
        }
    }

    @FunctionalInterface
    public interface BreakListener {
        void onBroken(QuadcopterEntity drone, ServerPlayer pilot, BlockPos pos, BlockState state, BreakMath.Impact impact);
    }

    @FunctionalInterface
    public interface Guard {
        boolean allows(ServerLevel level, ServerPlayer pilot, BlockPos pos);
    }

    @FunctionalInterface
    public interface DenyListener {
        void onDenied(QuadcopterEntity drone, ServerPlayer pilot, BlockPos pos, BreakRules.Outcome outcome);
    }

    private static final class Budget {
        private long windowStart = Long.MIN_VALUE;
        private int used;

        boolean available(long now, int perSecond) {
            if (this.windowStart == Long.MIN_VALUE || now - this.windowStart >= 20 || now < this.windowStart) {
                this.windowStart = now;
                this.used = 0;
            }
            return this.used < perSecond;
        }

        void use() {
            this.used++;
        }

        int take(long now, int perSecond, int wanted) {
            if (!this.available(now, perSecond)) {
                return 0;
            }
            int granted = Math.max(0, Math.min(wanted, perSecond - this.used));
            this.used += granted;
            return granted;
        }
    }
}
