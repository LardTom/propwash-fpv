package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyRequest;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExampleFollow {
    public static final Identifier HOLD = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "hold");
    public static final Identifier FOLLOW = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "follow");
    public static final Identifier EXIT = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "mission");
    public static final String HOLD_TAG = "propwash_example_hold";
    public static final String FOLLOW_TAG = "propwash_example_follow";
    public static final double FOLLOW_DISTANCE = 2.5;
    public static final double FOLLOW_GAIN = 1.2;
    public static final double FOLLOW_SPEED = 12.0;
    public static final List<Ended> ENDED = new CopyOnWriteArrayList<>();

    private static final Map<UUID, Mission> MISSIONS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private ExampleFollow() {
    }

    public record Ended(UUID drone, UUID owner, Identifier id, AutonomyEnd reason) {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.drones().autonomy().registerExitHandler(EXIT, ExampleFollow::onPilotExit);
        PropwashEvents.AUTONOMY_ENDED.register((drone, owner, id, reason) -> {
            if (ExampleAddon.NAMESPACE.equals(id.getNamespace())) {
                ENDED.add(new Ended(drone.getUUID(), owner, id, reason));
                Mission mission = MISSIONS.get(drone.getUUID());
                if (mission != null && mission.id.equals(id) && reason != AutonomyEnd.REPLACED) {
                    MISSIONS.remove(drone.getUUID(), mission);
                }
            }
        });
    }

    static Optional<AutonomyRequest> onPilotExit(Entity drone, ServerPlayer pilot) {
        if (drone.entityTags().contains(FOLLOW_TAG)) {
            return Optional.of(request(drone, new Follow(pilot), FOLLOW, "FOLLOW"));
        }
        if (drone.entityTags().contains(HOLD_TAG)) {
            return Optional.of(request(drone, new Hold(), HOLD, "HOLD"));
        }
        return Optional.empty();
    }

    private static AutonomyRequest request(Entity drone, Mission mission, Identifier id, String label) {
        mission.id = id;
        MISSIONS.put(drone.getUUID(), mission);
        return AutonomyRequest.of(mission, AutonomyOptions.of(id).label(label));
    }

    public static AutonomyResult hold(Entity drone, ServerPlayer owner) {
        return start(drone, owner, new Hold(), AutonomyOptions.of(HOLD).label("HOLD"));
    }

    public static AutonomyResult follow(Entity drone, ServerPlayer owner) {
        return start(drone, owner, new Follow(owner), AutonomyOptions.of(FOLLOW).label("FOLLOW"));
    }

    public static AutonomyResult takeoff(Entity drone, ServerPlayer owner, double metres) {
        return start(drone, owner, new Hold(), AutonomyOptions.of(HOLD).label("HOLD").takeoff(metres));
    }

    public static Optional<AutonomyNotReady> notReady(Entity drone, double takeoffMetres) {
        AutonomyOptions options = AutonomyOptions.of(HOLD).label("HOLD");
        return PropwashApi.drones().autonomy().notReadyReason(drone, takeoffMetres > 0.0 ? options.takeoff(takeoffMetres) : options);
    }

    public static AutonomyResult start(Entity drone, ServerPlayer owner, Mission mission, AutonomyOptions options) {
        mission.id = options.id();
        Mission previous = MISSIONS.put(drone.getUUID(), mission);
        AutonomyResult result = PropwashApi.drones().autonomy().start(drone, owner, mission, options);
        if (!result.started()) {
            if (previous != null) {
                MISSIONS.put(drone.getUUID(), previous);
            } else {
                MISSIONS.remove(drone.getUUID(), mission);
            }
        }
        return result;
    }

    public static boolean land(Entity drone) {
        Mission mission = MISSIONS.get(drone.getUUID());
        if (mission == null) {
            return false;
        }
        mission.land = true;
        return true;
    }

    public static boolean stop(Entity drone) {
        return PropwashApi.drones().autonomy().stop(drone);
    }

    public static Optional<Ended> lastEnded(UUID drone) {
        for (int i = ENDED.size() - 1; i >= 0; i--) {
            Ended ended = ENDED.get(i);
            if (ended.drone().equals(drone)) {
                return Optional.of(ended);
            }
        }
        return Optional.empty();
    }

    public abstract static class Mission implements FlightAssist {
        Identifier id = HOLD;
        volatile boolean land;

        @Override
        public final void update(FlightState state, AssistOutput output) {
            this.fly(state, output);
            if (this.land) {
                output.land();
            }
        }

        abstract void fly(FlightState state, AssistOutput output);
    }

    public static final class Hold extends Mission {
        private double x = Double.NaN;
        private double y;
        private double z;

        @Override
        void fly(FlightState state, AssistOutput output) {
            if (Double.isNaN(this.x)) {
                this.x = state.x();
                this.y = state.y();
                this.z = state.z();
            }
            output.holdPosition(this.x, this.z);
            output.holdAltitude(this.y);
            output.label("HOLD");
        }

        @Override
        public void reset() {
            this.x = Double.NaN;
        }
    }

    public static final class Follow extends Mission {
        private final ServerPlayer owner;
        private double height = Double.NaN;
        private double lastTime = Double.NaN;
        private double lastX;
        private double lastZ;
        private double vx;
        private double vz;

        Follow(ServerPlayer owner) {
            this.owner = owner;
        }

        @Override
        void fly(FlightState state, AssistOutput output) {
            double ox = this.owner.getX();
            double oy = this.owner.getY();
            double oz = this.owner.getZ();
            double t = state.time();
            if (Double.isNaN(this.height)) {
                this.height = state.y() - oy;
            }
            if (Double.isNaN(this.lastTime)) {
                this.lastTime = t;
                this.lastX = ox;
                this.lastZ = oz;
            } else if (t - this.lastTime >= 0.05) {
                double dt = t - this.lastTime;
                this.vx += 0.5 * ((ox - this.lastX) / dt - this.vx);
                this.vz += 0.5 * ((oz - this.lastZ) / dt - this.vz);
                this.lastTime = t;
                this.lastX = ox;
                this.lastZ = oz;
            }
            double dx = state.x() - ox;
            double dz = state.z() - oz;
            double d = Math.hypot(dx, dz);
            double tx = ox + (d > 1.0E-3 ? dx / d : 0.0) * FOLLOW_DISTANCE;
            double tz = oz + (d > 1.0E-3 ? dz / d : 1.0) * FOLLOW_DISTANCE;
            output.velocity(this.vx + FOLLOW_GAIN * (tx - state.x()), this.vz + FOLLOW_GAIN * (tz - state.z()));
            output.maxSpeed(FOLLOW_SPEED);
            output.holdAltitude(oy + this.height);
            output.heading(Math.toDegrees(Math.atan2(-dx, dz)));
            output.label("FOLLOW");
        }

        @Override
        public void reset() {
            this.height = Double.NaN;
            this.lastTime = Double.NaN;
            this.vx = 0.0;
            this.vz = 0.0;
        }
    }
}
