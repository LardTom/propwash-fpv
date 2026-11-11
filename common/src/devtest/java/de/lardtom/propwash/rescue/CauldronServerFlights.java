package de.lardtom.propwash.rescue;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.sim.DisarmReason;
import de.lardtom.propwash.sim.SnagField;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

public final class CauldronServerFlights {
    private static final UUID OWNER = UUID.fromString("00000000-0000-4000-8000-0000000ca1d0");
    private static final double HOME_DY = 6.0;

    private CauldronServerFlights() {
    }

    public record Result(int disarmTick, DisarmReason reason, double drag) {
        public boolean fluid() {
            return this.disarmTick >= 0 && this.reason == DisarmReason.FLUID;
        }

        public boolean braked() {
            return this.drag > 0.0;
        }

        @Override
        public String toString() {
            String armed = this.disarmTick < 0 ? "scharf" : String.format(Locale.ROOT, "entschärft nach %d Ticks (%s)", this.disarmTick, this.reason);
            return armed + String.format(Locale.ROOT, ", Bremse %.1f/s", this.drag);
        }
    }

    public static Result fly(ServerLevel level, Vec3 feet, int ticks) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        try {
            drone.setBuild(DroneBuild.WHOOP);
            drone.snapTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("Drohne nicht gespawnt");
            }
            ReturnPlan plan = ReturnPlan.of(OWNER, feet.x, feet.y + HOME_DY, feet.z, false, new RescueSettings());
            ServerReturnFlight flight = ServerReturnFlight.start(drone, plan, level, new ServerRescueWorld((l, x, z) -> true));
            if (!flight.sim().telemetry().armed) {
                throw new IllegalStateException("Heimflug startet nicht scharf");
            }
            double drag = 0.0;
            for (int tick = 1; tick <= ticks; tick++) {
                flight.tick(drone, level, plan, null);
                drag = Math.max(drag, drag(flight.env.snag));
                Telemetry t = flight.sim().telemetry();
                if (!t.armed) {
                    return new Result(tick, t.lastDisarmReason, drag);
                }
            }
            return new Result(-1, DisarmReason.NONE, drag);
        } finally {
            drone.discard();
        }
    }

    private static double drag(SnagField field) {
        double sum = 0.0;
        for (int i = 0; i < SnagField.POINTS; i++) {
            sum += field.linear[i] + field.quadratic[i];
        }
        return sum;
    }
}
