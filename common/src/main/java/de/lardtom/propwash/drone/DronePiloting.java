package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.bind.BindAccess;
import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.drone.net.DroneResetS2C;
import de.lardtom.propwash.drone.net.DroneStateC2S;
import de.lardtom.propwash.drone.net.PilotRequestC2S;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.drone.net.CameraChargeC2S;
import de.lardtom.propwash.drone.net.CraftNameC2S;
import de.lardtom.propwash.drone.net.TuneC2S;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.ReturnPlan;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DronePiloting {
    public static final double TUNE_REACH = 8.0;
    public static final double PICKUP_REACH = 8.0;
    public static final int REJECT_COOLDOWN_TICKS = 10;
    public static final double INTERACTION_REACH_BUFFER = 1.0;
    public static final int CEILING_MARGIN = 1024;
    public static final int REMOTE_DEBOUNCE_TICKS = 10;
    public static final int REMOTE_AWAY_TICKS = 10;

    private static final Map<UUID, QuadcopterEntity> BY_PILOT = new HashMap<>();
    private static final Map<UUID, Long> LAST_REMOTE_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> REMOTE_AWAY = new HashMap<>();
    private static final Map<UUID, Long> DIED_AT = new HashMap<>();
    private static final Map<UUID, PilotRules.Decision> VIEW = new HashMap<>();

    private DronePiloting() {
    }

    static void init() {
        QuadcopterEntity.PILOT_CHANGED.register(DronePiloting::onPilotChanged);
        ServerBus.TICK_END.register(DronePiloting::onServerTick);
        ServerBus.PLAYER_LEAVE.register(DronePiloting::onPlayerLeave);
        ServerBus.STOPPED.register(server -> {
            BY_PILOT.clear();
            LAST_REMOTE_ACTION.clear();
            REMOTE_AWAY.clear();
            DIED_AT.clear();
            VIEW.clear();
        });
        PropwashNet.registerC2S(PilotRequestC2S.TYPE, PilotRequestC2S.CODEC, DronePiloting::onPilotRequest);
        PropwashNet.registerC2S(DroneStateC2S.TYPE, DroneStateC2S.CODEC, DronePiloting::onState);
        PropwashNet.registerC2S(TuneC2S.TYPE, TuneC2S.CODEC, DronePiloting::onTune);
        PropwashNet.registerC2S(CraftNameC2S.TYPE, CraftNameC2S.CODEC, CraftNames::onCraftName);
        PropwashNet.registerC2S(CameraChargeC2S.TYPE, CameraChargeC2S.CODEC, CameraCharging::onCameraCharge);
        PropwashNet.registerS2C(DroneResetS2C.TYPE, DroneResetS2C.CODEC);
    }

    public enum StartResult {
        STARTED(null),
        ALREADY_PILOT(null),
        UNAVAILABLE(null),
        NO_BATTERY("propwash.drone.pilot.no_battery"),
        OCCUPIED("propwash.drone.pilot.occupied"),
        GLIDING("propwash.drone.pilot.gliding"),
        TOO_FAR("propwash.drone.pilot.too_far"),
        DENIED("propwash.drone.pilot.denied"),
        RETURNING("propwash.rescue.autonomous.land_now");

        private final @Nullable String translationKey;

        StartResult(@Nullable String translationKey) {
            this.translationKey = translationKey;
        }

        public boolean success() {
            return this == STARTED || this == ALREADY_PILOT;
        }

        public @Nullable String translationKey() {
            return this.translationKey;
        }
    }

    public static Optional<QuadcopterEntity> pilotedBy(UUID player) {
        QuadcopterEntity drone = BY_PILOT.get(player);
        return drone != null && !drone.isRemoved() && drone.isFlownBy(player) ? Optional.of(drone) : Optional.empty();
    }

    public static StartResult tryStart(ServerPlayer player, QuadcopterEntity drone, PilotMode mode) {
        if (drone.isRemoved() || player.isRemoved() || !player.isAlive() || player.isSpectator() || player.level() != drone.level()) {
            return StartResult.UNAVAILABLE;
        }
        Optional<UUID> current = drone.pilot();
        if (current.isPresent()) {
            return current.get().equals(player.getUUID()) ? StartResult.ALREADY_PILOT : StartResult.OCCUPIED;
        }
        if (!drone.hasBattery()) {
            return StartResult.NO_BATTERY;
        }
        double maxDistance = RadioLink.startRange(ServerRules.maxPilotDistance(player), freeRange(player, drone));
        if (!(player.position().distanceTo(drone.position()) <= maxDistance)) {
            return StartResult.TOO_FAR;
        }
        if (!ServerRules.mayPilot(player, drone)) {
            return StartResult.DENIED;
        }
        ReturnPlan plan = drone.returnPlan();
        if (plan != null) {
            if (!plan.landNow()) {
                drone.setReturnPlan(plan.withLandNow());
            }
            return StartResult.RETURNING;
        }
        if (!PilotBodyRules.mayStart(player.isFallFlying())) {
            return StartResult.GLIDING;
        }
        QuadcopterEntity previous = BY_PILOT.get(player.getUUID());
        if (previous != null && previous != drone && previous.isFlownBy(player.getUUID())) {
            release(previous);
        }
        drone.setPilotMode(mode == PilotMode.NONE ? PilotMode.LINE_OF_SIGHT : mode);
        drone.setPilot(player);
        REMOTE_AWAY.remove(player.getUUID());
        return StartResult.STARTED;
    }

    public static boolean freeRange(ServerPlayer player, QuadcopterEntity drone) {
        return RadioLink.freeRange(player) || WorldRules.infiniteRange(player.level().getServer(), drone.build().video().system);
    }

    public static PilotRules.Decision decideMode(ServerPlayer player, QuadcopterEntity drone) {
        return PilotRules.decide(ContentIds.wornGogglesSystem(player).orElse(null), drone.build().video().system);
    }

    public static void dropLegacyBind(ItemStack goggles) {
        if (ContentIds.isGoggles(goggles) && goggles.has(CoreComponents.BOUND_DRONE.get())) {
            goggles.remove(CoreComponents.BOUND_DRONE.get());
        }
    }

    public static boolean remoteBoundTo(ItemStack stack, QuadcopterEntity drone) {
        return ContentIds.isRemote(stack) && ContentIds.boundDrone(stack).filter(b -> b.drone().equals(drone.getUUID())).isPresent();
    }

    public static boolean debounced(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = LAST_REMOTE_ACTION.get(player.getUUID());
        if (last != null && now - last < REMOTE_DEBOUNCE_TICKS && now >= last) {
            return true;
        }
        LAST_REMOTE_ACTION.put(player.getUUID(), now);
        return false;
    }

    public static void useRemote(ServerPlayer player, ItemStack remote) {
        if (debounced(player)) {
            return;
        }
        Optional<QuadcopterEntity> active = pilotedBy(player.getUUID());
        if (active.isPresent()) {
            release(active.get());
            player.sendOverlayMessage(Component.translatable("propwash.drone.remote.stopped").withStyle(ChatFormatting.AQUA));
            return;
        }
        dropLegacyBind(ContentIds.wornGoggles(player));
        Optional<BoundDrone> bound = ContentIds.boundDrone(remote);
        Entity entity = bound.map(b -> player.level().getEntity(b.drone())).orElse(null);
        QuadcopterEntity drone = entity instanceof QuadcopterEntity d && !d.isRemoved() ? d : null;
        if (player.isSecondaryUseActive()) {
            if (bound.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("propwash.drone.remote.unbound").withStyle(ChatFormatting.GOLD));
            } else if (drone == null) {
                player.sendOverlayMessage(Component.translatable("propwash.drone.remote.status_missing", bound.get().displayName())
                        .withStyle(ChatFormatting.GRAY));
            } else if (drone.returnPlan() != null) {
                int home = (int) Math.round(drone.returnPlan().horizontalDistance(drone.getX(), drone.getZ()));
                player.sendOverlayMessage(Component.translatable("propwash.rescue.autonomous.status", DroneInteractions.droneName(drone), home)
                        .withStyle(ChatFormatting.AQUA));
            } else {
                int blocks = (int) Math.round(player.position().distanceTo(drone.position()));
                player.sendOverlayMessage(Component.translatable("propwash.drone.remote.status", DroneInteractions.droneName(drone), blocks)
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        boolean viaReceiver = false;
        if (drone == null) {
            Optional<QuadcopterEntity> found = ReceiverBinds.nearestBound(player);
            if (found.isEmpty()) {
                player.sendOverlayMessage(bound.isEmpty()
                        ? Component.translatable("propwash.drone.remote.unbound").withStyle(ChatFormatting.GOLD)
                        : Component.translatable("propwash.drone.remote.not_found", bound.get().displayName()).withStyle(ChatFormatting.RED));
                return;
            }
            drone = found.get();
            viaReceiver = true;
        } else {
            BindAccess access = ReceiverBinds.access(player, drone);
            if (!access.connects()) {
                String key = access == BindAccess.CLEARED ? "propwash.drone.bind.denied_cleared" : "propwash.drone.bind.denied_other";
                player.sendOverlayMessage(Component.translatable(key, DroneInteractions.droneName(drone)).withStyle(ChatFormatting.GOLD));
                return;
            }
        }
        PilotRules.Decision decision = decideMode(player, drone);
        if (!decision.connects()) {
            player.sendOverlayMessage(videoMismatch("propwash.drone.session.refused_video", player, drone).withStyle(ChatFormatting.RED));
            return;
        }
        if (viaReceiver) {
            ReceiverBinds.point(remote, drone);
        }
        StartResult result = tryStart(player, drone, decision.mode());
        if (result == StartResult.STARTED) {
            Component message = sessionMessage(decision, drone);
            player.sendOverlayMessage(viaReceiver ? Component.translatable("propwash.drone.bind.via_receiver", message).withStyle(message.getStyle())
                    : message);
        } else if (result == StartResult.RETURNING) {
            player.sendOverlayMessage(Component.translatable("propwash.rescue.autonomous.land_now", DroneInteractions.droneName(drone))
                    .withStyle(ChatFormatting.AQUA));
        } else if (result == StartResult.DENIED) {
            QuadcopterEntity target = drone;
            Component reason = ServerRules.pilotDenialReason(player, drone)
                    .<Component>map(text -> text.copy().withStyle(ChatFormatting.RED))
                    .orElseGet(() -> startMessage(result, target));
            player.sendOverlayMessage(reason);
        } else if (!result.success() && result.translationKey() != null) {
            player.sendOverlayMessage(startMessage(result, drone));
        }
    }

    public static Component sessionMessage(PilotRules.Decision decision, QuadcopterEntity drone) {
        String key = decision == PilotRules.Decision.FPV ? "propwash.drone.session.fpv" : "propwash.drone.session.los";
        return Component.translatable(key, DroneInteractions.droneName(drone)).withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent videoMismatch(String key, ServerPlayer player, QuadcopterEntity drone) {
        Component goggles = ContentIds.wornGogglesSystem(player).map(DronePiloting::videoSystemName).orElse(Component.empty());
        return Component.translatable(key, goggles, DroneInteractions.droneName(drone), videoSystemName(drone.build().video().system));
    }

    public static Component startMessage(StartResult result, QuadcopterEntity drone) {
        String key = result.translationKey();
        if (key == null) {
            return Component.empty();
        }
        return Component.translatable(key).withStyle(ChatFormatting.RED);
    }

    public static Component videoSystemName(VideoSystem system) {
        return Component.translatable("propwash.drone.video_system." + system.name().toLowerCase(Locale.ROOT));
    }

    static void pickUp(ServerPlayer player, QuadcopterEntity drone) {
        if (drone.isRemoved()) {
            return;
        }
        Optional<UUID> pilot = drone.pilot();
        if (pilot.isPresent() && !pilot.get().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("propwash.drone.pilot.occupied").withStyle(ChatFormatting.RED));
            return;
        }
        if (player.position().distanceTo(drone.position()) > PICKUP_REACH) {
            return;
        }
        ItemStack stack = drone.toStack();
        if (stack.isEmpty()) {
            return;
        }
        if (pilot.isPresent()) {
            release(drone);
        }
        ServerLevel level = player.level();
        level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.4F);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            drone.spawnAtLocation(level, stack);
        }
        drone.discard();
    }

    public static void release(QuadcopterEntity drone) {
        if (drone.level().isClientSide()) {
            return;
        }
        drone.setArmed(false);
        drone.setMotorOutputsPacked(0);
        if (drone.pilot().isPresent()) {
            drone.setPilot(null);
        }
    }

    static void broadcastReset(QuadcopterEntity drone) {
        Quaternionf q = new Quaternionf(drone.orientation());
        DroneResetS2C payload = new DroneResetS2C(drone.getId(), drone.resetEpoch(), drone.getX(), drone.getY(), drone.getZ(),
                q.x, q.y, q.z, q.w);
        PropwashNet.sendToTrackingAndSelf(drone, payload);
        Optional<UUID> pilot = drone.pilot();
        if (pilot.isPresent() && drone.level() instanceof ServerLevel level) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pilot.get());
            if (player != null) {
                PropwashNet.sendToPlayer(player, payload);
            }
        }
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        previous.ifPresent(uuid -> {
            if (BY_PILOT.remove(uuid, drone)) {
                DIED_AT.remove(uuid);
                VIEW.remove(uuid);
            }
        });
        current.ifPresent(uuid -> {
            VIEW.remove(uuid);
            QuadcopterEntity other = BY_PILOT.put(uuid, drone);
            if (other != null && other != drone && !other.isRemoved() && other.isFlownBy(uuid)) {
                release(other);
                BY_PILOT.put(uuid, drone);
            }
        });
    }

    private static void onServerTick(MinecraftServer server) {
        if (BY_PILOT.isEmpty()) {
            return;
        }
        List<Map.Entry<UUID, QuadcopterEntity>> entries = new ArrayList<>(BY_PILOT.entrySet());
        for (Map.Entry<UUID, QuadcopterEntity> entry : entries) {
            UUID uuid = entry.getKey();
            QuadcopterEntity drone = entry.getValue();
            if (drone.isRemoved() || !drone.isFlownBy(uuid)) {
                BY_PILOT.remove(uuid, drone);
                DIED_AT.remove(uuid);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || player.isRemoved() || player.level() != drone.level()) {
                DIED_AT.remove(uuid);
                release(drone);
                continue;
            }
            long now = server.overworld().getGameTime();
            PilotBodyRules.AfterDeath afterDeath = PilotBodyRules.afterDeath(player.isAlive(),
                    DIED_AT.getOrDefault(uuid, PilotBodyRules.NOT_DEAD), now);
            if (afterDeath == PilotBodyRules.AfterDeath.WAIT) {
                DIED_AT.putIfAbsent(uuid, now);
                continue;
            }
            if (afterDeath == PilotBodyRules.AfterDeath.RELEASE) {
                DIED_AT.remove(uuid);
                release(drone);
                continue;
            }
            double maxDistance = RadioLink.releaseRange(ServerRules.maxPilotDistance(player), freeRange(player, drone));
            if (!(player.position().distanceTo(drone.position()) <= maxDistance)) {
                release(drone);
                player.sendOverlayMessage(Component.translatable("propwash.drone.pilot.lost_range").withStyle(ChatFormatting.RED));
                continue;
            }
            if (!drone.hasBattery()) {
                release(drone);
                continue;
            }
            if (drone.pilotMode() == PilotMode.NONE || remoteBoundTo(player.getMainHandItem(), drone)) {
                REMOTE_AWAY.remove(uuid);
            } else {
                int away = REMOTE_AWAY.merge(uuid, 1, Integer::sum);
                if (away >= REMOTE_AWAY_TICKS) {
                    REMOTE_AWAY.remove(uuid);
                    release(drone);
                    player.sendOverlayMessage(Component.translatable("propwash.drone.remote.put_away").withStyle(ChatFormatting.RED));
                    continue;
                }
            }
            if (drone.pilotMode() != PilotMode.NONE) {
                recheckView(player, drone);
            }
        }
    }

    private static void recheckView(ServerPlayer player, QuadcopterEntity drone) {
        PilotRules.Decision decision = decideMode(player, drone);
        PilotRules.Decision before = VIEW.put(player.getUUID(), decision);
        boolean modeChanged = drone.pilotMode() != decision.mode();
        if (modeChanged) {
            drone.setPilotMode(decision.mode());
        }
        if (!PilotRules.changed(before, decision) && !(before == null && modeChanged)) {
            return;
        }
        switch (decision) {
            case FPV -> player.sendOverlayMessage(Component.translatable("propwash.drone.session.switched_fpv").withStyle(ChatFormatting.AQUA));
            case LINE_OF_SIGHT -> player.sendOverlayMessage(Component.translatable("propwash.drone.session.switched_los")
                    .withStyle(ChatFormatting.GOLD));
            case NO_VIDEO -> player.sendOverlayMessage(videoMismatch("propwash.drone.session.no_video", player, drone)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    public static Optional<PilotRules.Decision> viewOf(UUID player) {
        return Optional.ofNullable(VIEW.get(player));
    }

    private static void onPlayerLeave(ServerPlayer player) {
        VIEW.remove(player.getUUID());
        LAST_REMOTE_ACTION.remove(player.getUUID());
        REMOTE_AWAY.remove(player.getUUID());
        DIED_AT.remove(player.getUUID());
        QuadcopterEntity drone = BY_PILOT.remove(player.getUUID());
        if (drone != null && !drone.isRemoved() && drone.isFlownBy(player.getUUID())) {
            release(drone);
        }
    }

    private static void onPilotRequest(PilotRequestC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (payload.start()) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        LAST_REMOTE_ACTION.put(player.getUUID(), player.level().getGameTime());
        if (entity instanceof QuadcopterEntity drone && drone.isFlownBy(player.getUUID())) {
            release(drone);
            return;
        }
        pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
    }

    private static void onState(DroneStateC2S state, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(state.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || state.epoch() != drone.resetEpoch()) {
            return;
        }
        long now = level.getGameTime();
        if (!drone.moveBudget().admitPacket(now)) {
            return;
        }
        if (!StateValidation.finite(state.x(), state.y(), state.z())
                || !StateValidation.orientationUsable(state.qx(), state.qy(), state.qz(), state.qw())
                || state.y() > level.getMaxY() + CEILING_MARGIN) {
            reject(drone, now);
            return;
        }
        double maxSpeed = StateValidation.effectiveMaxSpeed(ServerRules.maxDroneSpeed(player));
        Vec3 delta = new Vec3(state.x() - drone.getX(), state.y() - drone.getY(), state.z() - drone.getZ());
        if (!StateValidation.speedPlausible(state.vx(), state.vy(), state.vz(), maxSpeed)
                || !drone.moveBudget().tryConsume(delta.length(), maxSpeed, Util.getNanos())) {
            reject(drone, now);
            return;
        }
        if (!level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(Mth.floor(state.x())), SectionPos.blockToSectionCoord(Mth.floor(state.z())))) {
            reject(drone, now);
            return;
        }
        AABB box = drone.getBoundingBox();
        AABB target = box.move(delta);
        double shrink = Math.min(0.02, Math.min(target.getXsize(), target.getYsize()) * 0.25);
        if (!level.noBlockCollision(drone, target.deflate(shrink)) || !PathCheck.clear(level, drone, box, delta)) {
            reject(drone, now);
            return;
        }
        double maxDistance = RadioLink.releaseRange(ServerRules.maxPilotDistance(player), freeRange(player, drone));
        if (!(player.position().distanceTo(new Vec3(state.x(), state.y(), state.z())) <= maxDistance)) {
            release(drone);
            player.sendOverlayMessage(Component.translatable("propwash.drone.pilot.lost_range").withStyle(ChatFormatting.RED));
            return;
        }
        Quaternionf orientation = new Quaternionf(state.qx(), state.qy(), state.qz(), state.qw()).normalize();
        float battery = StateValidation.acceptBattery(state.battery(), drone.batteryCharge());
        Vec3 from = drone.position();
        drone.applyPilotState(state, orientation, battery, now);
        if (QuadcopterEntity.SERVER_MOVED.hasListeners()) {
            Vec3 to = drone.position();
            QuadcopterEntity.SERVER_MOVED.fire(listener -> listener.onServerMoved(drone, player, from, to));
        }
    }

    private static void reject(QuadcopterEntity drone, long now) {
        long last = drone.lastRejectTick();
        if (last != Long.MIN_VALUE && now - last < REJECT_COOLDOWN_TICKS) {
            return;
        }
        drone.markRejected(now);
        drone.resetTo(drone.position(), drone.orientation());
    }

    private static void onTune(TuneC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (payload.entityId() == TuneC2S.HELD_ITEM) {
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!ContentIds.is(stack, ContentIds.DRONE)) {
                return;
            }
            DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            FlightTune stored = stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
            FlightTune result = sanitize(stored, payload.tune(), build);
            if (result.values().isEmpty()) {
                stack.remove(CoreComponents.FLIGHT_TUNE.get());
            } else {
                stack.set(CoreComponents.FLIGHT_TUNE.get(), result);
            }
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        boolean pilot = drone.isFlownBy(player.getUUID());
        boolean nearbyFree = drone.pilot().isEmpty() && player.position().distanceTo(drone.position()) <= TUNE_REACH;
        if (!pilot && !nearbyFree) {
            return;
        }
        drone.setTune(sanitize(drone.tune(), payload.tune(), drone.build()));
    }

    public static FlightTune sanitize(FlightTune stored, FlightTune edited, DroneBuild build) {
        return new FlightTune(CameraMount.sanitize(TuneMapping.applyEdit(stored.values(), edited.values(), AirframeSpec.of(build)), build));
    }
}
