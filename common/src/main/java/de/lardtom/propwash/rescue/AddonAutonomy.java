package de.lardtom.propwash.rescue;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.DroneQueries;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyRequest;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.ExitHandler;
import de.lardtom.propwash.api.drone.ObstacleScan;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.rescue.net.AutonomyStatusS2C;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DroneSim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AddonAutonomy {
    private static final int STALE_TICKS = 40;
    private static final Map<QuadcopterEntity, Flight> FLIGHTS = new IdentityHashMap<>();
    private static final AutonomyBudget BUDGET = AutonomyBudget.SERVER;
    private static final ServerRescueWorld.ChunkGate GATE = (level, x, z) ->
            level.getChunkSource().getChunkNow(x, z) != null && RangeManager.generatedAround(level, x, z);

    private AddonAutonomy() {
    }

    static final class Flight extends ServerFlight {
        final ServerFlightAssist logic = new ServerFlightAssist();
        final FlightFence fence = new FlightFence();
        UUID owner;
        Identifier id;
        @Nullable ReturnPlan home;
        boolean tookOff;
        boolean generates;
        String sentLabel = "";
        boolean sentActive;

        Flight(ServerRescueWorld world) {
            super(world);
        }

        @Override
        void resynced(QuadcopterEntity drone) {
            this.logic.relatch();
        }

        @Override
        void beforeSlices(QuadcopterEntity drone, ServerLevel level) {
            this.fence.update(level, drone, this.chunks);
        }

        @Override
        void steer(double dt, QuadcopterEntity drone) {
            OperatorServer.feed(drone, this.sim);
            this.logic.update(dt, this.sim, drone.getX(), drone.getY(), drone.getZ(), this.world, this.input);
            boolean held = this.fence.limit(this.sim) && this.fence.atLimit();
            this.logic.terrainLimit(held && !this.generates, dt);
        }

        @Override
        boolean wantsDisarm() {
            return this.logic.wantsDisarm();
        }

        @Override
        boolean landed() {
            return this.logic.landed();
        }
    }

    static void init() {
        PropwashNet.registerS2C(AutonomyStatusS2C.TYPE, AutonomyStatusS2C.CODEC);
        QuadcopterEntity.PILOT_CHANGED.register(AddonAutonomy::onPilotChanged);
        ServerBus.TICK_END.register(AddonAutonomy::onTickEnd);
        ServerBus.STOPPED.register(server -> FLIGHTS.clear());
    }

    public static int flights() {
        return FLIGHTS.size();
    }

    public static boolean flying(QuadcopterEntity drone) {
        return FLIGHTS.containsKey(drone);
    }

    public static @Nullable DroneSim sim(QuadcopterEntity drone) {
        Flight flight = FLIGHTS.get(drone);
        return flight == null ? null : flight.sim();
    }

    public static Optional<Identifier> active(Entity drone) {
        Objects.requireNonNull(drone, "drone");
        Flight flight = drone instanceof QuadcopterEntity quad ? FLIGHTS.get(quad) : null;
        return flight == null || !flight.logic.addonActive() ? Optional.empty() : Optional.of(flight.id);
    }

    public static Optional<String> label(Entity drone) {
        Flight flight = drone instanceof QuadcopterEntity quad ? FLIGHTS.get(quad) : null;
        return flight == null || !flight.logic.addonActive() ? Optional.empty() : Optional.of(flight.logic.label());
    }

    public static Optional<String> phase(Entity drone) {
        Flight flight = drone instanceof QuadcopterEntity quad ? FLIGHTS.get(quad) : null;
        return flight == null ? Optional.empty() : Optional.of(flight.logic.phase().name());
    }

    public static AutonomyResult start(Entity drone, ServerPlayer owner, FlightAssist assist, AutonomyOptions options) {
        Objects.requireNonNull(drone, "drone");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(assist, "assist");
        Objects.requireNonNull(options, "options");
        if (!(drone instanceof QuadcopterEntity quad)) {
            throw new IllegalArgumentException("Kein Propwash-Drohnen-Entity: " + drone);
        }
        if (!(quad.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Autonomer Flug nur am Server");
        }
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Autonomer Flug nur im Server-Thread");
        }
        return begin(quad, level, owner, assist, options, null, false);
    }

    public static Optional<AutonomyNotReady> notReadyReason(Entity drone, AutonomyOptions options) {
        Objects.requireNonNull(drone, "drone");
        Objects.requireNonNull(options, "options");
        if (!(drone instanceof QuadcopterEntity quad)) {
            throw new IllegalArgumentException("Kein Propwash-Drohnen-Entity: " + drone);
        }
        if (!(quad.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("Autonomer Flug nur am Server");
        }
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Autonomer Flug nur im Server-Thread");
        }
        if (quad.isRemoved()) {
            return Optional.of(AutonomyNotReady.REMOVED);
        }
        if (FLIGHTS.containsKey(quad)) {
            return Optional.empty();
        }
        return notReady(grounded(quad), options.takeoff().isPresent(), false, quad.hasBattery());
    }

    static Optional<AutonomyNotReady> notReady(boolean grounded, boolean takeoff, boolean handover, boolean battery) {
        if (grounded) {
            return takeoff ? Optional.empty() : Optional.of(AutonomyNotReady.NO_TAKEOFF);
        }
        if (!handover) {
            return Optional.of(AutonomyNotReady.NOT_ON_GROUND);
        }
        return battery ? Optional.empty() : Optional.of(AutonomyNotReady.NO_BATTERY);
    }

    public static boolean stop(Entity drone) {
        Objects.requireNonNull(drone, "drone");
        Flight flight = drone instanceof QuadcopterEntity quad ? FLIGHTS.get(quad) : null;
        if (flight == null || !flight.logic.addonActive()) {
            return false;
        }
        flight.logic.end(AutonomyEnd.STOPPED);
        dispatch((QuadcopterEntity) drone, flight);
        return true;
    }

    static boolean onPilotExit(QuadcopterEntity drone, UUID pilot, @Nullable ReturnPlan report) {
        List<Api17Registries.Registered<ExitHandler>> handlers = Api17Registries.AUTONOMY.exitHandlers();
        if (handlers.isEmpty() || drone.isRemoved() || !(drone.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pilot);
        if (player == null || player.hasDisconnected()) {
            return false;
        }
        for (Api17Registries.Registered<ExitHandler> handler : handlers) {
            Optional<AutonomyRequest> request;
            try {
                request = handler.value().onPilotExit(drone, player);
            } catch (RuntimeException e) {
                Constants.LOG.warn("Übergabe beim FPV-Ende {} fehlgeschlagen", handler.id(), e);
                continue;
            }
            if (request == null || request.isEmpty()) {
                continue;
            }
            AutonomyResult result;
            try {
                result = begin(drone, level, player, request.get().assist(), request.get().options(), report, true);
            } catch (RuntimeException e) {
                Constants.LOG.warn("Übergabe beim FPV-Ende {} abgelehnt", handler.id(), e);
                return false;
            }
            if (result != AutonomyResult.STARTED) {
                Constants.LOG.debug("Übergabe beim FPV-Ende {} für Drohne {}: {}", handler.id(), drone.getId(), result);
            }
            return result == AutonomyResult.STARTED;
        }
        return false;
    }

    private static AutonomyResult begin(QuadcopterEntity drone, ServerLevel level, ServerPlayer owner, FlightAssist assist, AutonomyOptions options,
                                        @Nullable ReturnPlan report, boolean handover) {
        NamespaceRules.requireForeign(options.id().toString(), "autonomous flight");
        if (drone.isRemoved()) {
            return AutonomyResult.NOT_READY;
        }
        if (drone.pilot().isPresent()) {
            return AutonomyResult.PILOTED;
        }
        if (owner.hasDisconnected() || owner.isRemoved() || owner.level() != level || drone.returnPlan() != null
                || !ServerRules.mayPilot(owner, drone) || !(owner.position().distanceTo(drone.position()) <= startRange(owner, drone))) {
            return AutonomyResult.DENIED;
        }
        Flight running = FLIGHTS.get(drone);
        if (running != null) {
            return replace(drone, running, owner, assist, options);
        }
        if (!admitted(drone, level.getServer(), owner)) {
            return AutonomyResult.NO_BUDGET;
        }
        boolean grounded = grounded(drone);
        boolean takeoff = grounded && options.takeoff().isPresent();
        if (notReady(grounded, options.takeoff().isPresent(), handover, drone.hasBattery()).isPresent()) {
            return AutonomyResult.NOT_READY;
        }
        if (!drone.hasBattery()) {
            owner.sendOverlayMessage(Component.translatable("propwash.drone.pilot.no_battery").withStyle(ChatFormatting.RED));
            return AutonomyResult.NOT_ARMABLE;
        }
        Flight flight = new Flight(new ServerRescueWorld(GATE));
        flight.rulesOf(owner.getUUID());
        if (takeoff) {
            flight.begin(drone, level, Vec3.ZERO, false);
            if (!flight.arm(drone, level)) {
                owner.sendOverlayMessage(notArmable(drone, flight.sim().telemetry().armBlockers));
                return AutonomyResult.NOT_ARMABLE;
            }
            flight.logic.startFromGround(flight.sim(), drone.getX(), drone.getY(), drone.getZ(), assist, options);
            flight.tookOff = true;
        } else {
            flight.begin(drone, level, drone.getDeltaMovement().scale(20.0), true);
            flight.logic.startInFlight(flight.sim(), drone.getX(), drone.getY(), drone.getZ(), assist, options);
        }
        BUDGET.admit(drone, owner.getUUID(), AutonomyBudget.Kind.ADDON);
        flight.owner = owner.getUUID();
        flight.id = options.id();
        flight.home = report;
        flight.generates = ServerRules.mayGenerateChunks(owner);
        FLIGHTS.put(drone, flight);
        flight.chunks.keep(drone, flight.generates);
        drone.setArmed(true);
        announce(drone, flight, owner);
        return AutonomyResult.STARTED;
    }

    private static AutonomyResult replace(QuadcopterEntity drone, Flight flight, ServerPlayer owner, FlightAssist assist, AutonomyOptions options) {
        UUID previousOwner = flight.owner;
        Identifier previousId = flight.id;
        boolean wasActive = flight.logic.addonActive();
        if (!previousOwner.equals(owner.getUUID())) {
            if (!admitted(drone, owner.level().getServer(), owner)) {
                return AutonomyResult.NO_BUDGET;
            }
            BUDGET.release(drone);
            BUDGET.admit(drone, owner.getUUID(), AutonomyBudget.Kind.ADDON);
        }
        flight.logic.replace(assist, options);
        flight.owner = owner.getUUID();
        flight.id = options.id();
        flight.sentLabel = "";
        flight.sentActive = false;
        if (wasActive) {
            fireEnded(drone, previousOwner, previousId, AutonomyEnd.REPLACED);
        }
        if (FLIGHTS.get(drone) == flight) {
            announce(drone, flight, owner);
        }
        return AutonomyResult.STARTED;
    }

    static AutonomyBudget budget(MinecraftServer server) {
        BUDGET.addonLimits(WorldRules.get(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_PER_PLAYER),
                WorldRules.get(server, WorldRuleSpecs.AUTONOMOUS_FLIGHTS_TOTAL));
        return BUDGET;
    }

    private static boolean admitted(QuadcopterEntity drone, MinecraftServer server, ServerPlayer owner) {
        AutonomyBudget budget = budget(server);
        AutonomyBudget.Admission admission = budget.check(drone, owner.getUUID(), AutonomyBudget.Kind.ADDON);
        if (admission == AutonomyBudget.Admission.ADMITTED) {
            return true;
        }
        owner.sendOverlayMessage(budget.refusal(DroneInteractions.droneName(drone), admission).withStyle(ChatFormatting.RED));
        return false;
    }

    static boolean grounded(QuadcopterEntity drone) {
        return GroundContact.grounded(drone);
    }

    private static double startRange(ServerPlayer owner, QuadcopterEntity drone) {
        return RadioLink.startRange(ServerRules.maxPilotDistance(owner), DronePiloting.freeRange(owner, drone));
    }

    private static double releaseRange(ServerPlayer owner, QuadcopterEntity drone) {
        return RadioLink.releaseRange(ServerRules.maxPilotDistance(owner), DronePiloting.freeRange(owner, drone));
    }

    static boolean tick(QuadcopterEntity drone) {
        Flight flight = FLIGHTS.get(drone);
        if (flight == null || !(drone.level() instanceof ServerLevel level)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (!drone.hasBattery()) {
            flight.logic.end(AutonomyEnd.BATTERY);
            finish(drone, flight);
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(flight.owner);
        boolean online = owner != null && !owner.hasDisconnected();
        if (flight.logic.addonActive()) {
            if (!online) {
                flight.logic.end(AutonomyEnd.OWNER_OFFLINE);
            } else if (owner.level() != level || !(owner.position().distanceTo(drone.position()) <= releaseRange(owner, drone))) {
                flight.logic.end(AutonomyEnd.OUT_OF_RANGE);
            }
        }
        flight.generates = online && flight.logic.addonActive() && ServerRules.mayGenerateChunks(owner);
        dispatch(drone, flight);
        if (FLIGHTS.get(drone) != flight) {
            return false;
        }
        scan(drone, flight);
        ServerFlight.Outcome outcome = flight.fly(drone, level);
        flight.chunks.keep(drone, flight.generates);
        if (outcome != ServerFlight.Outcome.FLYING) {
            flight.logic.disarmed(outcome == ServerFlight.Outcome.LANDED);
            finish(drone, flight);
            return true;
        }
        dispatch(drone, flight);
        if (FLIGHTS.get(drone) == flight) {
            status(drone, flight, false);
        }
        return true;
    }

    private static void scan(QuadcopterEntity drone, Flight flight) {
        double range = flight.logic.sensorRange();
        if (!(range > 0.0)) {
            return;
        }
        ObstacleScan scan = DroneQueries.INSTANCE.scan(drone, range);
        for (SensorDirection direction : SensorDirection.values()) {
            double distance = scan.distance(direction);
            flight.logic.obstacle(direction, distance >= range ? Double.POSITIVE_INFINITY : distance);
        }
    }

    private static void dispatch(QuadcopterEntity drone, Flight flight) {
        AutonomyEnd reason = flight.logic.consumeEnded();
        if (reason == null) {
            return;
        }
        RuntimeException failure = flight.logic.consumeFailure();
        if (failure != null) {
            Constants.LOG.warn("Flughilfe {} im autonomen Flug von Drohne {} fehlgeschlagen", flight.id, drone.getId(), failure);
        }
        UUID owner = flight.owner;
        Identifier id = flight.id;
        boolean homeRequested = flight.logic.homeRequested();
        boolean gpsFix = ServerFlightAssist.needsGps(reason, homeRequested) && drone.level() instanceof ServerLevel level && flight.gpsFix(drone, level);
        ServerFlightAssist.FollowUp next = ServerFlightAssist.followUp(reason, homeRequested, gpsFix);
        if (next == ServerFlightAssist.FollowUp.NONE && reason != AutonomyEnd.REPLACED
                || next == ServerFlightAssist.FollowUp.HOME && handOverHome(drone, flight)) {
            remove(drone, flight);
        }
        flight.sentActive = false;
        flight.sentLabel = "";
        tell(drone, owner, reason);
        sendStatus(drone, owner, false, "", reason);
        fireEnded(drone, owner, id, reason);
    }

    private static boolean handOverHome(QuadcopterEntity drone, Flight flight) {
        if (!(drone.level() instanceof ServerLevel level) || !WorldRules.on(level.getServer(), WorldRuleSpecs.AUTONOMOUS_RESCUE)
                || !drone.isArmed() || drone.pilot().isPresent()) {
            return false;
        }
        ReturnPlan plan = homePlan(drone, flight, level);
        if (plan == null) {
            return false;
        }
        if (plan.horizontalDistance(drone.getX(), drone.getZ()) > AutonomousReturns.MAX_HOME_DISTANCE) {
            plan = plan.withLandNow();
        }
        drone.setReturnPlan(plan);
        return true;
    }

    private static @Nullable ReturnPlan homePlan(QuadcopterEntity drone, Flight flight, ServerLevel level) {
        if (flight.home != null) {
            return flight.home.withTicks(0);
        }
        HomePoint point = drone.homePoint();
        if (point != null && point.dimension().equals(HomePointCodecs.dimension(level))) {
            return ReturnPlan.of(flight.owner, point.x(), point.y(), point.z(), false, new RescueSettings());
        }
        if (flight.tookOff) {
            return ReturnPlan.of(flight.owner, flight.logic.takeoffX(), groundAt(flight, drone), flight.logic.takeoffZ(), false, new RescueSettings());
        }
        return null;
    }

    private static double groundAt(Flight flight, QuadcopterEntity drone) {
        double ground = WorldProbe.groundBelow(flight.world, flight.logic.takeoffX(), drone.getY(), flight.logic.takeoffZ(), 0.3,
                ServerFlightAssist.LAND_SCAN_DEPTH);
        return Double.isFinite(ground) ? ground : drone.getY();
    }

    private static void finish(QuadcopterEntity drone, Flight flight) {
        AutonomyEnd reason = flight.logic.consumeEnded();
        boolean wasActive = flight.sentActive;
        remove(drone, flight);
        if (drone.returnPlan() == null) {
            drone.setArmed(false);
            drone.setMotorOutputsPacked(0);
        }
        flight.sentActive = false;
        if (reason != null) {
            tell(drone, flight.owner, reason);
            sendStatus(drone, flight.owner, false, "", reason);
            fireEnded(drone, flight.owner, flight.id, reason);
        } else if (wasActive) {
            sendStatus(drone, flight.owner, false, "", null);
        }
    }

    private static void remove(QuadcopterEntity drone, Flight flight) {
        if (FLIGHTS.get(drone) == flight) {
            FLIGHTS.remove(drone);
            BUDGET.release(drone);
        }
    }

    private static void announce(QuadcopterEntity drone, Flight flight, ServerPlayer owner) {
        String label = flight.logic.label();
        Component shown = Component.literal(label.isBlank() ? flight.id.getPath().toUpperCase(Locale.ROOT) : label);
        owner.sendOverlayMessage(Component.translatable("propwash.drone.autonomy.started", DroneInteractions.droneName(drone), shown)
                .withStyle(ChatFormatting.AQUA));
        status(drone, flight, true);
    }

    private static void status(QuadcopterEntity drone, Flight flight, boolean force) {
        boolean active = flight.logic.addonActive();
        String label = active ? flight.logic.label() : "";
        if (!force && active == flight.sentActive && label.equals(flight.sentLabel)) {
            return;
        }
        flight.sentActive = active;
        flight.sentLabel = label;
        sendStatus(drone, flight.owner, active, label, null);
    }

    private static void sendStatus(QuadcopterEntity drone, UUID owner, boolean active, String label, @Nullable AutonomyEnd reason) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        PropwashNet.sendToPlayer(player, new AutonomyStatusS2C(drone.getId(), active, label, reason == null ? -1 : reason.ordinal()));
    }

    private static void tell(QuadcopterEntity drone, UUID owner, AutonomyEnd reason) {
        if (reason == AutonomyEnd.REPLACED || reason == AutonomyEnd.OWNER_OFFLINE || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.hasDisconnected()) {
            return;
        }
        ChatFormatting style = reason == AutonomyEnd.LANDED || reason == AutonomyEnd.PILOT ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        player.sendOverlayMessage(Component.translatable("propwash.drone.autonomy.ended." + reason.name().toLowerCase(Locale.ROOT),
                DroneInteractions.droneName(drone)).withStyle(style));
    }

    private static Component notArmable(QuadcopterEntity drone, int blockers) {
        MutableComponent reasons = Component.empty();
        boolean first = true;
        for (ArmBlocker blocker : ArmBlocker.values()) {
            if (!blocker.isSet(blockers)) {
                continue;
            }
            if (!first) {
                reasons.append(", ");
            }
            first = false;
            reasons.append(Component.translatable(blockerKey(blocker)));
        }
        return Component.translatable("propwash.drone.autonomy.not_armable", DroneInteractions.droneName(drone), reasons).withStyle(ChatFormatting.RED);
    }

    static String blockerKey(ArmBlocker blocker) {
        String warning = switch (blocker) {
            case ARM_SWITCH_NOT_RESET -> "arm_switch";
            case THROTTLE_NOT_LOW -> "arm_throttle";
            case TILTED -> "arm_angle";
            case PACK_DEPLETED -> "pack_depleted";
            case BUILD_INVALID -> "arm_build";
            case CRASH_COOLDOWN -> "arm_crash";
            case FAILSAFE -> "failsafe";
            case REBOOTING -> "arm_reboot";
            case IN_FLUID -> "arm_fluid";
            case HARDWARE_FAULT -> "arm_hardware";
            case THROTTLE_NOT_CENTERED -> "arm_throttle_3d";
            case TURTLE_AIRBORNE -> "arm_turtle";
        };
        return "propwash.osd.warning." + warning;
    }

    private static void fireEnded(QuadcopterEntity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        try {
            PropwashEvents.AUTONOMY_ENDED.fire(listener -> listener.onAutonomyEnded(drone, owner, id, reason));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Listener für AUTONOMY_ENDED ({}) fehlgeschlagen", id, e);
        }
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (current.isEmpty()) {
            return;
        }
        Flight flight = FLIGHTS.get(drone);
        if (flight == null) {
            return;
        }
        if (flight.logic.addonActive()) {
            flight.logic.end(AutonomyEnd.PILOT);
            dispatch(drone, flight);
        }
        remove(drone, flight);
    }

    private static void onTickEnd(MinecraftServer server) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        List<QuadcopterEntity> gone = new ArrayList<>();
        for (Map.Entry<QuadcopterEntity, Flight> entry : FLIGHTS.entrySet()) {
            QuadcopterEntity drone = entry.getKey();
            boolean stale = drone.level().getGameTime() - entry.getValue().lastTick > STALE_TICKS;
            if (drone.isRemoved() || drone.pilot().isPresent() || stale) {
                gone.add(drone);
            }
        }
        for (QuadcopterEntity drone : gone) {
            Flight flight = FLIGHTS.get(drone);
            if (flight == null) {
                continue;
            }
            if (flight.logic.addonActive()) {
                flight.logic.end(drone.pilot().isPresent() ? AutonomyEnd.PILOT : AutonomyEnd.STOPPED);
            }
            finish(drone, flight);
        }
        for (Map.Entry<QuadcopterEntity, Flight> entry : FLIGHTS.entrySet()) {
            entry.getValue().chunks.keep(entry.getKey(), entry.getValue().generates);
        }
    }
}
