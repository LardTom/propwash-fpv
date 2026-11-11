package de.lardtom.propwash.stats.advancement;

import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.CargoReleaseReason;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.interact.DroneStrikes;
import de.lardtom.propwash.interact.StrikeMath;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.stats.CrashBudget;
import de.lardtom.propwash.stats.PilotStats;
import de.lardtom.propwash.stats.PropwashStats;
import de.lardtom.propwash.stats.net.GhostBeatenC2S;
import de.lardtom.propwash.stats.net.TrainingC2S;
import de.lardtom.propwash.stats.net.TrickC2S;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class FpvAdvancements {
    private static final Map<QuadcopterEntity, FlightFeats> FLIGHTS = new WeakHashMap<>();
    private static final Map<QuadcopterEntity, Vec3> CARGO = new WeakHashMap<>();
    private static final Map<UUID, CrashBudget> REPORTS = new HashMap<>();
    private static boolean initialized;

    private FpvAdvancements() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashTriggers.bootstrap();
        PropwashEvents.DRONE_ASSEMBLED.register((player, stack, data) -> milestone(player, Milestone.DRONE_BUILT));
        PropwashEvents.BUILD_IMPORTED.register((player, request, result) -> {
            if (result.status() == ImportStatus.APPLIED) {
                milestone(player, Milestone.SHARECODE_LOADED);
            }
        });
        QuadcopterEntity.PILOT_CHANGED.register((drone, previous, current) -> FLIGHTS.remove(drone));
        QuadcopterEntity.SERVER_MOVED.register(FpvAdvancements::onMoved);
        DroneCargo.CHANGED.register(FpvAdvancements::onCargo);
        DroneStrikes.STRUCK.register(FpvAdvancements::onStruck);
        ServerBus.PLAYER_LEAVE.register(player -> REPORTS.remove(player.getUUID()));
        ServerBus.STOPPED.register(server -> {
            FLIGHTS.clear();
            CARGO.clear();
            REPORTS.clear();
        });
        PropwashNet.registerC2S(TrickC2S.TYPE, TrickC2S.CODEC, FpvAdvancements::onTrick);
        PropwashNet.registerC2S(TrainingC2S.TYPE, TrainingC2S.CODEC, FpvAdvancements::onTraining);
        PropwashNet.registerC2S(GhostBeatenC2S.TYPE, GhostBeatenC2S.CODEC, FpvAdvancements::onGhostBeaten);
    }

    public static void milestone(ServerPlayer player, Milestone milestone) {
        if (PropwashTriggers.MILESTONE.isBound()) {
            PropwashTriggers.MILESTONE.get().trigger(player, milestone);
        }
    }

    public static void lap(ServerPlayer player, boolean ownCourse) {
        milestone(player, Milestone.LAP);
        if (ownCourse) {
            milestone(player, Milestone.OWN_COURSE);
        }
    }

    public static void crashed(ServerPlayer player) {
        flight(player, Double.NaN, Double.NaN);
    }

    public static void flight(ServerPlayer player, double altitude, double dive) {
        if (PropwashTriggers.FLIGHT.isBound()) {
            double meters = PilotStats.value(player, PropwashStats.DISTANCE) / 100.0;
            int crashes = PilotStats.value(player, PropwashStats.CRASHES);
            PropwashTriggers.FLIGHT.get().trigger(player, new FlightTrigger.Report(meters, altitude, dive, crashes));
        }
    }

    public static void struck(ServerPlayer player, Entity target) {
        if (PropwashTriggers.DRONE_STRIKE.isBound()) {
            PropwashTriggers.DRONE_STRIKE.get().trigger(player, target);
        }
    }

    public static void cargoDelivered(ServerPlayer player, double distance) {
        if (PropwashTriggers.CARGO_DELIVERED.isBound()) {
            PropwashTriggers.CARGO_DELIVERED.get().trigger(player, distance);
        }
    }

    public static void trick(ServerPlayer player, String trick, int combo) {
        if (PropwashTriggers.TRICK.isBound()) {
            PropwashTriggers.TRICK.get().trigger(player, trick, combo);
        }
    }

    public static void flightSchool(ServerPlayer player, TrainingTally tally) {
        if (PropwashTriggers.FLIGHT_SCHOOL.isBound()) {
            PropwashTriggers.FLIGHT_SCHOOL.get().trigger(player, tally);
        }
    }

    private static void onMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to) {
        boolean armed = drone.isArmed();
        FlightFeats.Observation seen = FLIGHTS.computeIfAbsent(drone, d -> new FlightFeats()).update(armed, to.y);
        if (seen.takeoff()) {
            milestone(pilot, Milestone.TAKEOFF);
            if (drone.pilotMode() == PilotMode.FPV) {
                milestone(pilot, Milestone.FPV_FLIGHT);
            }
        }
        flight(pilot, armed ? to.y : Double.NaN, seen.dive());
    }

    private static void onCargo(QuadcopterEntity drone, double grams, @Nullable CargoReleaseReason released) {
        if (released == null) {
            if (grams > 0.0) {
                CARGO.putIfAbsent(drone, drone.position());
            }
            return;
        }
        Vec3 from = CARGO.remove(drone);
        if (from == null || released == CargoReleaseReason.CRASH || released == CargoReleaseReason.REMOVED) {
            return;
        }
        double dx = drone.getX() - from.x;
        double dz = drone.getZ() - from.z;
        pilotOf(drone).ifPresent(player -> cargoDelivered(player, Math.sqrt(dx * dx + dz * dz)));
    }

    private static void onStruck(QuadcopterEntity drone, LivingEntity target, StrikeMath.Strike strike, boolean hurt) {
        pilotOf(drone).ifPresent(player -> struck(player, target));
    }

    private static Optional<ServerPlayer> pilotOf(QuadcopterEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return drone.pilot().map(uuid -> level.getServer().getPlayerList().getPlayer(uuid));
    }

    private static boolean admit(ServerPayloadContext context) {
        return REPORTS.computeIfAbsent(context.player().getUUID(), uuid -> new CrashBudget()).admit(context.server().getTickCount(), 1) > 0;
    }

    private static void onTrick(TrickC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.level().getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()
                || !drone.isFlownBy(player.getUUID())) {
            return;
        }
        if (!payload.trick().isEmpty() && FeatCodecs.trick(payload.trick()).isEmpty() || !admit(context)) {
            return;
        }
        trick(player, payload.trick(), Math.clamp(payload.combo(), 0, TrickC2S.MAX_COMBO));
    }

    private static void onTraining(TrainingC2S payload, ServerPayloadContext context) {
        if (admit(context)) {
            flightSchool(context.player(), new TrainingTally(payload.chapters(), payload.perfect()));
        }
    }

    private static void onGhostBeaten(GhostBeatenC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (payload.courseId() > 0 && DronePiloting.pilotedBy(player.getUUID()).isPresent() && admit(context)) {
            milestone(player, Milestone.GHOST_BEATEN);
        }
    }
}
