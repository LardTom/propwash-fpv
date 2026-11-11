package de.lardtom.propwash.rescue;

import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.ThrottleCentering;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;

public final class PilotTakeover {
    public static final double TICKS_PER_SECOND = 20.0;
    private static final double MAX_DEADBAND_3D = 0.5;

    private PilotTakeover() {
    }

    public static boolean seamless(QuadcopterEntity drone) {
        return drone.isArmed() && seamless(true, GroundContact.gap(drone, GroundContact.PROBE));
    }

    public static boolean seamless(boolean armed, double supportGap) {
        return armed && supportGap >= GroundContact.TOUCH;
    }

    public static void place(DroneSim sim, SimSnapshot scratch, QuadcopterEntity drone, Quaternionfc body) {
        Vec3 v = drone.getDeltaMovement().scale(TICKS_PER_SECOND);
        place(sim, scratch, drone.getX(), drone.getY(), drone.getZ(), body, v.x, v.y, v.z);
    }

    public static void place(DroneSim sim, SimSnapshot scratch, double x, double feetY, double z, Quaternionfc body, double vx, double vy, double vz) {
        Handover.place(sim, scratch, x, feetY, z, body, vx, vy, vz, true);
        sim.settleForTakeover();
    }

    public static boolean resolved(DroneSim sim) {
        return sim.assist() == null || sim.telemetry().time > 0.0;
    }

    public static float throttle(boolean assistHolds, boolean mode3d, double hoverStick, double deadband3d) {
        if (assistHolds) {
            return ThrottleCentering.CENTER;
        }
        double hover = Double.isFinite(hoverStick) ? Math.max(0.0, Math.min(1.0, hoverStick)) : ThrottleCentering.CENTER;
        if (!mode3d) {
            return (float) hover;
        }
        double band = Double.isFinite(deadband3d) ? Math.max(0.0, Math.min(MAX_DEADBAND_3D, deadband3d)) : 0.0;
        double high = ThrottleCentering.CENTER + band;
        return (float) Math.min(1.0, high + hover * (1.0 - high));
    }
}
