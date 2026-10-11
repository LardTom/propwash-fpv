package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.AirframeSpec;
import org.joml.Quaternionfc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DronePhysique {
    private static final Map<DroneBuild, DronePhysique> CACHE = new ConcurrentHashMap<>();

    private final float comHeightMm;
    private final float comAboveFoot;
    private final float[] hullX;
    private final float[] hullY;
    private final float[] hullZ;

    DronePhysique(float comHeightMm, float comAboveFoot, float[] hullX, float[] hullY, float[] hullZ) {
        this.comHeightMm = comHeightMm;
        this.comAboveFoot = comAboveFoot;
        this.hullX = hullX;
        this.hullY = hullY;
        this.hullZ = hullZ;
    }

    public static DronePhysique of(DroneBuild build) {
        return CACHE.computeIfAbsent(build, DronePhysique::derive);
    }

    static DronePhysique derive(DroneBuild build) {
        try {
            AirframeSpec params = AirframeSpec.of(build);
            int count = params.hullCount();
            float[] x = new float[count];
            float[] y = new float[count];
            float[] z = new float[count];
            for (int k = 0; k < count; k++) {
                x[k] = (float) -params.hullX(k);
                y[k] = (float) params.hullY(k);
                z[k] = (float) -params.hullZ(k);
            }
            float com = (float) (params.comHeight * 1000.0);
            float foot = (float) -params.hitboxFootOffset;
            if (!Float.isFinite(com) || !Float.isFinite(foot) || foot < 0.0F) {
                return fallback(build);
            }
            return new DronePhysique(com, foot, x, y, z);
        } catch (RuntimeException e) {
            Constants.LOG.warn("Drohnen-Renderer: Flugmodell-Maße für {} nicht verfügbar ({}), benutze Schätzwerte", build, e.toString());
            return fallback(build);
        }
    }

    static DronePhysique fallback(DroneBuild build) {
        float com = Math.max(4.0F, 0.06F * build.frame().wheelbaseMm);
        return new DronePhysique(com, (com + 4.0F) / 1000.0F, new float[0], new float[0], new float[0]);
    }

    public float comHeightMm() {
        return this.comHeightMm;
    }

    public float comAboveFoot() {
        return this.comAboveFoot;
    }

    public boolean hasHull() {
        return this.hullX.length > 0;
    }

    public float lowestPoint(Quaternionfc orientation) {
        float lowest = 0.0F;
        for (int k = 0; k < this.hullX.length; k++) {
            lowest = Math.min(lowest, rotatedY(orientation, this.hullX[k], this.hullY[k], this.hullZ[k]));
        }
        return lowest;
    }

    public static float rotatedY(Quaternionfc q, float x, float y, float z) {
        float qx = q.x();
        float qy = q.y();
        float qz = q.z();
        float qw = q.w();
        return 2.0F * (qx * qy + qw * qz) * x + (1.0F - 2.0F * (qx * qx + qz * qz)) * y + 2.0F * (qy * qz - qw * qx) * z;
    }
}
