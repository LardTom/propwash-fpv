package de.lardtom.propwash.rescue;

import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

public final class Handover {
    private static final float PI = (float) Math.PI;

    private Handover() {
    }

    public static void place(DroneSim sim, SimSnapshot scratch, double x, double feetY, double z, Quaternionfc body,
                             double vx, double vy, double vz, boolean armed) {
        double com = -sim.params().hitboxFootOffset;
        Quaternionf simQ = new Quaternionf(body).normalize().rotateY(PI);
        sim.reset(x, feetY + com, z, 0.0);
        sim.copyStateTo(scratch);
        scratch.qw = simQ.w;
        scratch.qx = simQ.x;
        scratch.qy = simQ.y;
        scratch.qz = simQ.z;
        scratch.vx = finite(vx);
        scratch.vy = finite(vy);
        scratch.vz = finite(vz);
        scratch.armed = armed;
        scratch.sleeping = false;
        if (armed) {
            double hover = sim.params().hoverOmega * Math.sqrt(Math.max(0.1, sim.tuning().gravityScale));
            for (int i = 0; i < scratch.rotorOmega.length; i++) {
                scratch.rotorOmega[i] = hover;
            }
        }
        sim.copyStateFrom(scratch);
    }

    public static Quaternionf body(DroneSim sim) {
        Quaterniond q = new Quaterniond();
        sim.getAttitude(q);
        return new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w).normalize().rotateY(PI);
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0.0;
    }
}
