package de.lardtom.propwash.rescue;

import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.net.HomeResetC2S;
import de.lardtom.propwash.server.AnchorRules;
import de.lardtom.propwash.server.GeneratedArea;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerModule;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HomeResets {
    public static final int TICKET_RADIUS = 2;
    public static final int LOAD_TIMEOUT_TICKS = 200;
    public static final int TRACK_GRACE_TICKS = 100;
    private static final Map<QuadcopterEntity, Pending> PENDING = new IdentityHashMap<>();

    private HomeResets() {
    }

    static void init() {
        PropwashNet.registerC2S(HomeResetC2S.TYPE, HomeResetC2S.CODEC, HomeResets::onRequest);
        ServerBus.TICK_END.register(HomeResets::tick);
        ServerBus.STOPPED.register(server -> PENDING.clear());
    }

    static void onRequest(HomeResetC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        tell(player, reset(player, drone));
    }

    public static boolean allowed(ServerLevel level) {
        return WorldRules.on(level.getServer(), WorldRuleSpecs.ALLOW_RESET);
    }

    public static boolean keepTracked(Entity entity, ServerPlayer player) {
        if (!(entity instanceof QuadcopterEntity drone) || !drone.isFlownBy(player.getUUID())) {
            return false;
        }
        long last = drone.lastHomeResetTick();
        long now = drone.level().getGameTime();
        return last != Long.MIN_VALUE && now >= last && now - last < TRACK_GRACE_TICKS;
    }

    public static boolean pending(QuadcopterEntity drone) {
        return PENDING.containsKey(drone);
    }

    public static HomeResetRules.Outcome reset(ServerPlayer player, QuadcopterEntity drone) {
        if (!(drone.level() instanceof ServerLevel level) || drone.level() != player.level() || drone.isRemoved()) {
            return HomeResetRules.Outcome.NOT_PILOT;
        }
        long now = level.getGameTime();
        String dimension = HomePointCodecs.dimension(level);
        HomeResetRules.Target target = HomeResetRules.choose(allowed(level), drone.isFlownBy(player.getUUID()), now,
                drone.lastHomeResetTick(), drone.homePoint(), drone.launchPoint(), dimension);
        if (!target.outcome().moves()) {
            return target.outcome();
        }
        if (!HomeResetRules.inside(target.y(), level.getMinY(), level.getMaxY(), level.getWorldBorder().isWithinBounds(target.x(), target.z()))) {
            return HomeResetRules.Outcome.OUTSIDE;
        }
        drone.markHomeReset(now);
        PENDING.remove(drone);
        HomeResetRules.Outcome outcome = step(level, player, drone, target);
        if (outcome == HomeResetRules.Outcome.LOADING) {
            PENDING.put(drone, new Pending(player.getUUID(), target, now + LOAD_TIMEOUT_TICKS));
        }
        return outcome;
    }

    private static HomeResetRules.Outcome step(ServerLevel level, ServerPlayer player, QuadcopterEntity drone, HomeResetRules.Target target) {
        Vec3 at = new Vec3(target.x(), target.y(), target.z());
        BlockPos pos = BlockPos.containing(at);
        ChunkPos chunk = ChunkPos.containing(pos);
        double range = RadioLink.releaseRange(ServerRules.maxPilotDistance(player), DronePiloting.freeRange(player, drone))
                + AnchorRules.RELEASE_TOLERANCE;
        boolean mayGenerate = ServerRules.mayGenerateChunks(player);
        GeneratedArea.Status generated = mayGenerate ? GeneratedArea.Status.GENERATED : RangeManager.generatedStatus(level, chunk.x(), chunk.z(),
                TICKET_RADIUS + 1);
        HomeResetRules.Outcome restriction = HomeResetRules.restriction(player.position().distanceTo(at) <= range,
                ServerRules.forbiddenSpawnArea(player, level, pos), generated, mayGenerate);
        if (restriction != null) {
            return restriction;
        }
        level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_TICKET.get(), chunk, TICKET_RADIUS);
        if (!level.isPositionEntityTicking(pos)) {
            return HomeResetRules.Outcome.LOADING;
        }
        return place(level, player, drone, target);
    }

    private static HomeResetRules.Outcome place(ServerLevel level, ServerPlayer player, QuadcopterEntity drone, HomeResetRules.Target target) {
        double x = target.x();
        double z = target.z();
        AABB box = drone.getBoundingBox();
        Vec3 from = drone.position();
        double y = HomeResetRules.clearY(target.y(),
                candidate -> level.noBlockCollision(drone, box.move(x - from.x, candidate - from.y, z - from.z)));
        if (Double.isNaN(y)) {
            return HomeResetRules.Outcome.BLOCKED;
        }
        drone.markHomeReset(level.getGameTime());
        QuadcopterEntity.HOME_RESET.fire(listener -> listener.accept(drone, player));
        drone.setArmed(false);
        drone.setMotorOutputsPacked(0);
        drone.resetTo(new Vec3(x, y, z), HomeResetRules.level(drone.orientation()), true);
        return target.outcome();
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Map.Entry<QuadcopterEntity, Pending>> entries = new ArrayList<>(PENDING.entrySet());
        for (Map.Entry<QuadcopterEntity, Pending> entry : entries) {
            QuadcopterEntity drone = entry.getKey();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.pilot());
            if (drone.isRemoved() || !(drone.level() instanceof ServerLevel level) || player == null || player.level() != level
                    || !drone.isFlownBy(pending.pilot()) || !allowed(level)) {
                PENDING.remove(drone);
                continue;
            }
            HomeResetRules.Outcome outcome = step(level, player, drone, pending.target());
            if (outcome != HomeResetRules.Outcome.LOADING) {
                PENDING.remove(drone);
                tell(player, outcome);
            } else if (level.getGameTime() > pending.deadline()) {
                PENDING.remove(drone);
                tell(player, HomeResetRules.Outcome.UNLOADED);
            }
        }
    }

    private static void tell(ServerPlayer player, HomeResetRules.Outcome outcome) {
        if (!outcome.silent()) {
            player.sendOverlayMessage(Component.translatable(outcome.messageKey())
                    .withStyle(outcome.moves() ? ChatFormatting.AQUA : ChatFormatting.RED));
        }
    }

    private record Pending(UUID pilot, HomeResetRules.Target target, long deadline) {
    }
}
