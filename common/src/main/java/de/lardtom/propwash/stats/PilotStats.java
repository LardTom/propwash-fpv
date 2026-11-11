package de.lardtom.propwash.stats;

import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.DroneVibrations;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.stats.advancement.FpvAdvancements;
import de.lardtom.propwash.stats.net.CrashC2S;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PilotStats {
    private static final Map<QuadcopterEntity, FlightTally> TALLIES = new WeakHashMap<>();
    private static final Map<UUID, CrashBudget> CRASHES = new HashMap<>();

    private PilotStats() {
    }

    static void init() {
        QuadcopterEntity.PILOT_CHANGED.register(PilotStats::onPilotChanged);
        QuadcopterEntity.SERVER_MOVED.register(PilotStats::onMoved);
        ServerBus.PLAYER_LEAVE.register(player -> CRASHES.remove(player.getUUID()));
        ServerBus.STARTED.register(server -> PropwashStats.bind());
        ServerBus.STOPPED.register(server -> {
            TALLIES.clear();
            CRASHES.clear();
        });
        PropwashEvents.REGISTRY_FROZEN.register(registry -> PropwashStats.bind());
        PropwashNet.registerC2S(CrashC2S.TYPE, CrashC2S.CODEC, PilotStats::onCrash);
    }

    public static void lap(ServerPlayer pilot) {
        award(pilot, PropwashStats.LAPS, 1);
    }

    public static void raceFinished(ServerPlayer pilot) {
        award(pilot, PropwashStats.RACES_FINISHED, 1);
    }

    public static int value(ServerPlayer player, PropwashStats.Entry entry) {
        return player.getStats().getValue(entry.stat());
    }

    public static boolean tracking(QuadcopterEntity drone) {
        return TALLIES.containsKey(drone);
    }

    private static long tick(QuadcopterEntity drone) {
        return drone.level() instanceof ServerLevel level ? level.getServer().getTickCount() : 0L;
    }

    private static void onPilotChanged(QuadcopterEntity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (current.isPresent()) {
            TALLIES.put(drone, new FlightTally(current.get(), tick(drone), drone.isArmed(), drone.batteryCharge()));
        } else {
            TALLIES.remove(drone);
        }
    }

    private static void onMoved(QuadcopterEntity drone, ServerPlayer pilot, Vec3 from, Vec3 to) {
        long tick = pilot.level().getServer().getTickCount();
        FlightTally tally = TALLIES.get(drone);
        if (tally == null || !tally.pilot().equals(pilot.getUUID())) {
            TALLIES.put(drone, new FlightTally(pilot.getUUID(), tick, drone.isArmed(), drone.batteryCharge()));
            return;
        }
        FlightTally.Step step = tally.update(tick, drone.isArmed(), from.distanceTo(to), drone.getDeltaMovement().length() * 20.0,
                drone.batteryCharge());
        if (step.empty()) {
            return;
        }
        if (step.ticks() > 0) {
            award(pilot, PropwashStats.FLIGHT_TIME, step.ticks());
            award(pilot, PropwashStats.classTime(StatClass.of(drone.build())), step.ticks());
        }
        award(pilot, PropwashStats.DISTANCE, step.centimeters());
        if (step.armed()) {
            award(pilot, PropwashStats.ARMS, 1);
        }
        if (step.depleted()) {
            award(pilot, PropwashStats.BATTERIES_DEPLETED, 1);
        }
        raise(pilot, PropwashStats.TOP_SPEED, step.topSpeed());
    }

    private static void onCrash(CrashC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (payload.crashes() <= 0 || !(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()
                || !drone.isFlownBy(player.getUUID())) {
            return;
        }
        int wanted = Math.min(payload.crashes(), CrashC2S.MAX_CRASHES);
        int granted = CRASHES.computeIfAbsent(player.getUUID(), uuid -> new CrashBudget()).admit(context.server().getTickCount(), wanted);
        award(player, PropwashStats.CRASHES, granted);
        if (granted > 0) {
            FpvAdvancements.crashed(player);
            DroneVibrations.crashed(drone);
        }
    }

    private static void award(ServerPlayer player, PropwashStats.Entry entry, int amount) {
        if (amount > 0) {
            player.awardStat(entry.stat(), amount);
        }
    }

    private static void raise(ServerPlayer player, PropwashStats.Entry entry, int value) {
        if (value <= 0) {
            return;
        }
        Stat<Identifier> stat = entry.stat();
        int current = player.getStats().getValue(stat);
        if (value > current) {
            player.awardStat(stat, value - current);
        }
    }
}
