package de.lardtom.propwash.rescue;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.StateValidation;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.net.RescueStatusC2S;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AutonomousReturns {
    public static final int MAX_TICKS = 12_000;
    public static final int GRACE_TICKS = 1_200;
    public static final double MAX_HOME_DISTANCE = 1500.0;
    public static final double DESCENT_PER_TICK = 0.075;
    private static final int STALE_TICKS = 40;
    private static final double HOME_SLACK = 32.0;
    private static final float DESCENT_MOTORS = 0.3F;

    private static final Map<UUID, Report> REPORTS = new HashMap<>();
    private static final Map<QuadcopterEntity, ServerReturnFlight> FLIGHTS = new IdentityHashMap<>();
    private static final Set<QuadcopterEntity> DESCENTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final AutonomyBudget BUDGET = new AutonomyBudget();
    private static final ServerRescueWorld.ChunkGate GATE = (level, x, z) ->
            level.getChunkSource().getChunkNow(x, z) != null && RangeManager.generatedAround(level, x, z);

    private record Report(UUID pilot, ReturnPlan plan) {
    }

    private AutonomousReturns() {
    }

    static void init() {
        PropwashNet.registerC2S(RescueStatusC2S.TYPE, RescueStatusC2S.CODEC, AutonomousReturns::onStatus);
        QuadcopterEntity.PILOT_CHANGED.register(AutonomousReturns::onPilotChanged);
        QuadcopterEntity.setServerAutopilot(AutonomousReturns::tick);
        ServerBus.TICK_END.register(AutonomousReturns::onTickEnd);
        ServerBus.STOPPED.register(server -> clear());
    }

    public static int flights() {
        return FLIGHTS.size();
    }

    public static int descents() {
        return DESCENTS.size();
    }

    public static boolean flying(QuadcopterEntity drone) {
        return FLIGHTS.containsKey(drone);
    }

    public static @Nullable RescuePhase phase(QuadcopterEntity drone) {
        ServerReturnFlight flight = FLIGHTS.get(drone);
        return flight == null ? null : flight.autopilot.phase();
    }

    public static boolean reported(QuadcopterEntity drone) {
        return REPORTS.containsKey(drone.getUUID());
    }

    static void clear() {
        REPORTS.clear();
        FLIGHTS.clear();
        DESCENTS.clear();
        BUDGET.clear();
    }

    private static void onStatus(RescueStatusC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()
                || !drone.isFlownBy(player.getUUID())) {
            return;
        }
        if (!payload.active()) {
            REPORTS.remove(drone.getUUID());
            return;
        }
        if (!Double.isFinite(payload.homeX()) || !Double.isFinite(payload.homeY()) || !Double.isFinite(payload.homeZ())
                || payload.homeY() < level.getMinY() - 64 || payload.homeY() > level.getMaxY() + 64) {
            return;
        }
        Vec3 home = new Vec3(payload.homeX(), payload.homeY(), payload.homeZ());
        double reach = RadioLink.releaseRange(ServerRules.maxPilotDistance(player), DronePiloting.freeRange(player, drone)) + HOME_SLACK;
        if (!(home.distanceTo(player.position()) <= reach) || ServerRules.forbiddenSpawnArea(player, level, BlockPos.containing(home))) {
            REPORTS.remove(drone.getUUID());
            return;
        }
        RescueSettings settings = new RescueSettings();
        settings.altitude = payload.altitude();
        settings.speed = payload.speed();
        settings.descentRate = payload.descentRate();
        settings.obstacleAvoidance = payload.obstacleAvoidance();
        settings.clearance = payload.clearance();
        settings.minDistance = payload.minDistance();
        ReturnPlan plan = ReturnPlan.of(player.getUUID(), payload.homeX(), payload.homeY(), payload.homeZ(), payload.followPilot(), settings);
        REPORTS.put(drone.getUUID(), new Report(player.getUUID(), plan));
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (current.isPresent()) {
            REPORTS.remove(drone.getUUID());
            stopFlight(drone);
            return;
        }
        if (previous.isEmpty()) {
            return;
        }
        Report report = REPORTS.remove(drone.getUUID());
        if (report == null || !report.pilot().equals(previous.get()) || drone.isRemoved() || !drone.hasBattery()
                || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!WorldRules.on(server, WorldRuleSpecs.AUTONOMOUS_RESCUE)) {
            tell(server, previous.get(), Component.translatable("propwash.rescue.autonomous.rule_off", DroneInteractions.droneName(drone))
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        ReturnPlan plan = report.plan();
        if (plan.horizontalDistance(drone.getX(), drone.getZ()) > MAX_HOME_DISTANCE) {
            plan = plan.withLandNow();
        }
        drone.setReturnPlan(plan);
    }

    static boolean tick(QuadcopterEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return false;
        }
        ReturnPlan plan = drone.returnPlan();
        if (plan == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        boolean allowed = WorldRules.on(server, WorldRuleSpecs.AUTONOMOUS_RESCUE);
        if (!drone.hasBattery() || !allowed) {
            boolean running = FLIGHTS.containsKey(drone) || DESCENTS.contains(drone);
            finish(drone, null, false);
            if (running && !allowed) {
                tell(server, plan.owner(), Component.translatable("propwash.rescue.autonomous.rule_off", DroneInteractions.droneName(drone))
                        .withStyle(ChatFormatting.GOLD));
            }
            return false;
        }
        if (DESCENTS.contains(drone)) {
            return descend(drone, level, plan);
        }
        ServerReturnFlight flight = FLIGHTS.get(drone);
        if (flight == null) {
            AutonomyBudget.Admission admission = BUDGET.admit(drone, plan.owner());
            if (admission != AutonomyBudget.Admission.ADMITTED) {
                DESCENTS.add(drone);
                tell(server, plan.owner(), Component.translatable("propwash.rescue.autonomous.busy", DroneInteractions.droneName(drone))
                        .withStyle(ChatFormatting.GOLD));
                return descend(drone, level, plan);
            }
            flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld(GATE));
            FLIGHTS.put(drone, flight);
            Component name = DroneInteractions.droneName(drone);
            if (plan.ticks() == 0) {
                tell(server, plan.owner(), Component.translatable(plan.landNow() ? "propwash.rescue.autonomous.too_far"
                        : "propwash.rescue.autonomous.started", name).withStyle(ChatFormatting.AQUA));
            }
        }
        keepLoaded(drone, level);
        int ticks = plan.ticks() + 1;
        if (ticks >= MAX_TICKS && !plan.landNow()) {
            plan = plan.withLandNow();
            drone.setReturnPlan(plan);
            tell(server, plan.owner(), Component.translatable("propwash.rescue.autonomous.timeout", DroneInteractions.droneName(drone),
                    MAX_TICKS / 1200).withStyle(ChatFormatting.GOLD));
        }
        if (ticks >= MAX_TICKS + GRACE_TICKS) {
            finish(drone, flight, false);
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(plan.owner());
        ServerReturnFlight.Outcome outcome = flight.tick(drone, level, plan, owner);
        keepLoaded(drone, level);
        if (flight.autopilot.gaveUp() && !flight.announcedStuck) {
            flight.announcedStuck = true;
            tell(server, plan.owner(), Component.translatable("propwash.rescue.autonomous.stuck", DroneInteractions.droneName(drone))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (outcome != ServerReturnFlight.Outcome.FLYING) {
            finish(drone, flight, outcome == ServerReturnFlight.Outcome.LANDED);
            return true;
        }
        ReturnPlan current = drone.returnPlan();
        if (current != null) {
            RescueAutopilot autopilot = flight.autopilot;
            drone.setReturnPlan(current.withTicks(ticks).withHome(autopilot.homeX(), autopilot.homeY(), autopilot.homeZ()));
        }
        return true;
    }

    private static void keepLoaded(QuadcopterEntity drone, ServerLevel level) {
        ChunkPos chunk = drone.chunkPosition();
        if (RangeManager.generatedAround(level, chunk.x(), chunk.z())) {
            RangeManager.keepSimulated(level, chunk);
        }
    }

    private static boolean descend(QuadcopterEntity drone, ServerLevel level, ReturnPlan plan) {
        if (plan.ticks() >= MAX_TICKS) {
            DESCENTS.remove(drone);
            finish(drone, null, false);
            return false;
        }
        drone.setReturnPlan(plan.withTicks(plan.ticks() + 1));
        Quaternionf level0 = new Quaternionf().rotationY((float) Math.toRadians(-drone.getYRot()));
        drone.setAttitude(level0);
        drone.setArmed(true);
        drone.setMotorOutputsPacked(StateValidation.packMotors(DESCENT_MOTORS, DESCENT_MOTORS, DESCENT_MOTORS, DESCENT_MOTORS));
        double before = drone.getY();
        drone.move(MoverType.SELF, new Vec3(0.0, -DESCENT_PER_TICK, 0.0));
        drone.setDeltaMovement(Vec3.ZERO);
        boolean down = drone.onGround() || drone.isInWater() || drone.isInLava() || before - drone.getY() < 1e-4
                || drone.getY() <= level.getMinY();
        if (down) {
            DESCENTS.remove(drone);
            drone.setReturnPlan(null);
            drone.setArmed(false);
            drone.setMotorOutputsPacked(0);
            tell(level.getServer(), plan.owner(), landedMessage(drone, plan));
        }
        return true;
    }

    private static void finish(QuadcopterEntity drone, @Nullable ServerReturnFlight flight, boolean landed) {
        ReturnPlan plan = drone.returnPlan();
        drone.setReturnPlan(null);
        stopFlight(drone);
        drone.setArmed(false);
        drone.setMotorOutputsPacked(0);
        if (plan == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        if (flight != null) {
            Component message = landed ? landedMessage(drone, plan)
                    : Component.translatable("propwash.rescue.autonomous.lost", DroneInteractions.droneName(drone)).withStyle(ChatFormatting.RED);
            tell(level.getServer(), plan.owner(), message);
        }
        Constants.LOG.debug("Heimflug ohne Pilot beendet: Drohne {} gelandet {}", drone.getId(), landed);
    }

    private static Component landedMessage(QuadcopterEntity drone, ReturnPlan plan) {
        int distance = (int) Math.round(plan.horizontalDistance(drone.getX(), drone.getZ()));
        return Component.translatable("propwash.rescue.autonomous.landed", DroneInteractions.droneName(drone), distance)
                .withStyle(ChatFormatting.AQUA);
    }

    private static void stopFlight(QuadcopterEntity drone) {
        FLIGHTS.remove(drone);
        DESCENTS.remove(drone);
        BUDGET.release(drone);
    }

    private static void tell(MinecraftServer server, UUID player, Component message) {
        ServerPlayer target = server.getPlayerList().getPlayer(player);
        if (target != null && !target.hasDisconnected()) {
            target.sendSystemMessage(message);
        }
    }

    private static void onTickEnd(MinecraftServer server) {
        if (FLIGHTS.isEmpty() && DESCENTS.isEmpty()) {
            return;
        }
        List<QuadcopterEntity> gone = new ArrayList<>();
        for (Map.Entry<QuadcopterEntity, ServerReturnFlight> entry : FLIGHTS.entrySet()) {
            QuadcopterEntity drone = entry.getKey();
            boolean stale = drone.level().getGameTime() - entry.getValue().lastTick > STALE_TICKS;
            if (drone.isRemoved() || drone.returnPlan() == null || drone.pilot().isPresent() || stale) {
                gone.add(drone);
            }
        }
        for (QuadcopterEntity drone : DESCENTS) {
            if (drone.isRemoved() || drone.returnPlan() == null || drone.pilot().isPresent()) {
                gone.add(drone);
            }
        }
        for (QuadcopterEntity drone : gone) {
            stopFlight(drone);
        }
    }
}
