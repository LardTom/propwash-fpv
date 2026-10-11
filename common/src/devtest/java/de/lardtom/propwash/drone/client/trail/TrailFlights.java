package de.lardtom.propwash.drone.client.trail;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TrailFlights {
    private static final List<Flight> FLIGHTS = new CopyOnWriteArrayList<>();
    private static final float MOTOR = 0.55F;
    private static boolean installed;

    record Path(Vec3 center, double radiusX, double radiusZ, double speed, double bob, double phase, boolean figureEight) {
        Vec3 at(double seconds) {
            double circumference = 2.0 * Math.PI * Math.sqrt((this.radiusX * this.radiusX + this.radiusZ * this.radiusZ) * 0.5);
            double angle = this.phase + seconds * this.speed / Math.max(0.5, circumference) * 2.0 * Math.PI;
            double x = this.radiusX * Math.cos(angle);
            double z = this.figureEight ? this.radiusZ * Math.sin(2.0 * angle) * 0.5 : this.radiusZ * Math.sin(angle);
            double y = this.bob * Math.sin(angle * 2.0 + this.phase);
            return this.center.add(x, y, z);
        }
    }

    private record Flight(int id, ServerLevel level, Path path, boolean moving) {
    }

    private TrailFlights() {
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ServerBus.TICK_END.register(TrailFlights::onServerTick);
    }

    static QuadcopterEntity spawn(ServerLevel level, Path path, DroneBuild build, DronePaint paint) {
        install();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 start = path.at(level.getServer().getTickCount() / 20.0);
        drone.snapTo(start.x, start.y, start.z, 0.0F, 0.0F);
        drone.setBuild(build);
        drone.setPaint(paint);
        drone.setNoGravity(true);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        FLIGHTS.add(new Flight(drone.getId(), level, path, true));
        return drone;
    }

    static void park(MinecraftServer server, int id, Vec3 at) {
        for (Flight flight : FLIGHTS) {
            if (flight.id() == id) {
                FLIGHTS.remove(flight);
                FLIGHTS.add(new Flight(id, flight.level(), flight.path(), false));
                if (flight.level().getEntity(id) instanceof QuadcopterEntity drone) {
                    drone.snapTo(at.x, at.y, at.z, drone.getYRot(), 0.0F);
                    drone.setAttitude(new Quaternionf());
                    drone.setNoGravity(false);
                }
                return;
            }
        }
    }

    static void clear(MinecraftServer server) {
        for (Flight flight : FLIGHTS) {
            if (flight.level().getEntity(flight.id()) instanceof QuadcopterEntity drone) {
                drone.discard();
            }
        }
        FLIGHTS.clear();
    }

    static boolean active() {
        return !FLIGHTS.isEmpty();
    }

    static List<Integer> ids() {
        return FLIGHTS.stream().map(Flight::id).toList();
    }

    static void armOnClient(boolean armed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Flight flight : FLIGHTS) {
            if (minecraft.level.getEntity(flight.id()) instanceof QuadcopterEntity drone) {
                boolean on = armed && flight.moving();
                drone.setArmed(on);
                drone.setMotorOutputs(on ? MOTOR : 0.0F, on ? MOTOR : 0.0F, on ? MOTOR : 0.0F, on ? MOTOR : 0.0F);
            }
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (FLIGHTS.isEmpty()) {
            return;
        }
        double seconds = server.getTickCount() / 20.0;
        for (Flight flight : FLIGHTS) {
            if (!flight.moving() || !(flight.level().getEntity(flight.id()) instanceof QuadcopterEntity drone)) {
                continue;
            }
            Vec3 now = flight.path().at(seconds);
            Vec3 ahead = flight.path().at(seconds + 0.05);
            double dx = ahead.x - now.x;
            double dz = ahead.z - now.z;
            float heading = (float) Math.atan2(-dx, -dz);
            drone.setPos(now.x, now.y, now.z);
            drone.setDeltaMovement(Vec3.ZERO);
            Quaternionf attitude = new Quaternionf().rotationY(heading).rotateX(-0.35F);
            drone.setAttitude(attitude);
            drone.setYRot(QuadcopterEntity.yawDegrees(attitude));
        }
    }
}
