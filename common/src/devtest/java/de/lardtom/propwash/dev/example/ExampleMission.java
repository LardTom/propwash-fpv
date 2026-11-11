package de.lardtom.propwash.dev.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.dev.example.client.ExampleMissionClient;
import de.lardtom.propwash.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleMission {
    public static final String REQUIRED_API = "1.7";
    public static final Identifier MISSION_ID = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "mission");
    public static final Identifier LOG_ID = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "service_log");
    public static final int MAX_WAYPOINTS = 16;
    public static final AtomicInteger PILOT_TAKEN = new AtomicInteger();
    public static final AtomicInteger PILOT_LEFT = new AtomicInteger();
    public static final AtomicReference<String> LAST_CHANGE = new AtomicReference<>("");

    private static volatile boolean testing;
    private static DroneDataKey<Mission> mission;
    private static DroneDataKey<ServiceLog> log;
    private static boolean initialized;

    public record Mission(String name, List<BlockPos> waypoints, int flights) {
        public static final Mission EMPTY = new Mission("", List.of(), 0);
        public static final Codec<Mission> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "").forGetter(Mission::name),
                BlockPos.CODEC.listOf(0, MAX_WAYPOINTS).optionalFieldOf("waypoints", List.of()).forGetter(Mission::waypoints),
                Codec.INT.optionalFieldOf("flights", 0).forGetter(Mission::flights)).apply(i, Mission::new));

        public Mission {
            waypoints = List.copyOf(waypoints);
        }

        public Mission flown() {
            return new Mission(this.name, this.waypoints, this.flights + 1);
        }
    }

    public record ServiceLog(int pilotChanges, Optional<UUID> lastPilot) {
        public static final ServiceLog EMPTY = new ServiceLog(0, Optional.empty());
        public static final Codec<ServiceLog> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("changes", 0).forGetter(ServiceLog::pilotChanges),
                UUIDUtil.CODEC.optionalFieldOf("last_pilot").forGetter(ServiceLog::lastPilot)).apply(i, ServiceLog::new));

        public ServiceLog next(Optional<UUID> pilot) {
            return new ServiceLog(this.pilotChanges + 1, pilot.isPresent() ? pilot : this.lastPilot);
        }
    }

    private ExampleMission() {
    }

    static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.requireVersion(ExampleAddon.NAMESPACE, REQUIRED_API);
        mission = PropwashApi.droneData().register(MISSION_ID, Mission.CODEC, Mission.EMPTY, DroneDataKey.Sync.TRACKING);
        log = PropwashApi.droneData().register(LOG_ID, ServiceLog.CODEC, ServiceLog.EMPTY, DroneDataKey.Sync.NONE);
        PropwashEvents.PILOT_CHANGED.register(ExampleMission::onPilotChanged);
        if (Services.PLATFORM.isClientDist()) {
            ExampleMissionClient.init();
        }
    }

    public static DroneDataKey<Mission> mission() {
        return mission;
    }

    public static DroneDataKey<ServiceLog> log() {
        return log;
    }

    public static boolean active() {
        return testing || ExampleAddon.showcase();
    }

    public static void testing(boolean on) {
        testing = on;
    }

    public static Mission around(BlockPos center, String name) {
        List<BlockPos> points = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2.0 * i;
            points.add(center.offset((int) Math.round(Math.cos(angle) * 8.0), 4, (int) Math.round(Math.sin(angle) * 8.0)));
        }
        return new Mission(name, points, 0);
    }

    private static void onPilotChanged(Entity drone, Optional<UUID> previous, Optional<UUID> current) {
        (current.isPresent() ? PILOT_TAKEN : PILOT_LEFT).incrementAndGet();
        LAST_CHANGE.set(previous.map(UUID::toString).orElse("-") + " -> " + current.map(UUID::toString).orElse("-"));
        if (!active() || drone.level().isClientSide()) {
            return;
        }
        PropwashApi.droneData().set(drone, log, PropwashApi.droneData().get(drone, log).next(current));
        if (current.isPresent()) {
            Mission now = PropwashApi.droneData().get(drone, mission);
            if (now.waypoints().isEmpty()) {
                now = around(drone.blockPosition(), drone.getName().getString());
            }
            PropwashApi.droneData().set(drone, mission, now.flown());
        }
    }
}
