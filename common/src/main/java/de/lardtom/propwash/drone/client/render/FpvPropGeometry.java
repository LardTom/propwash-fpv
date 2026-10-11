package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.sim.FpvRig;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class FpvPropGeometry {
    public static final int SLOTS = DroneLayout.SLOTS;
    public static final int RIM_SAMPLES = 24;
    public static final int INNER_SAMPLES = 12;
    public static final float DISK_LIFT_MM = 2.5F;
    public static final float NEAR_TARGET_MM = 55.0F;
    public static final float MAX_NEAR_SCALE = 12.0F;
    public static final float MIN_DEPTH_MM = 1.0F;
    public static final float CONE_TAN = (float) Math.tan(Math.toRadians(85.0));
    public static final float NEAR_FLOOR_MM = 52.0F;
    private static final float[] NO_POINTS = new float[0];

    private final Vector3fc[] hubs = new Vector3fc[SLOTS];
    private final float radiusMm;
    private final Vector3fc lensMm;
    private final float tiltDegrees;
    private final float[][] samples = new float[SLOTS][];
    private final float[][] inner = new float[SLOTS][];
    private final float[] guards;
    private final float nearScale;

    public FpvPropGeometry(Vector3fc[] hubsMm, float propRadiusMm, Vector3fc lensMm, float tiltDegrees) {
        this(hubsMm, propRadiusMm, lensMm, tiltDegrees, NO_POINTS);
    }

    public FpvPropGeometry(Vector3fc[] hubsMm, float propRadiusMm, Vector3fc lensMm, float tiltDegrees, float[] guardPointsMm) {
        if (hubsMm.length != SLOTS) {
            throw new IllegalArgumentException("Es gibt genau " + SLOTS + " Props");
        }
        this.radiusMm = Math.max(1.0F, propRadiusMm);
        this.lensMm = new Vector3f(lensMm);
        this.tiltDegrees = Float.isFinite(tiltDegrees) ? tiltDegrees : 0.0F;
        float nearest = Float.POSITIVE_INFINITY;
        for (int slot = 0; slot < SLOTS; slot++) {
            this.hubs[slot] = new Vector3f(hubsMm[slot]);
            float[] points = new float[(RIM_SAMPLES + 1) * 3];
            Vector3f camera = new Vector3f();
            for (int i = 0; i <= RIM_SAMPLES; i++) {
                float x = hubsMm[slot].x();
                float z = hubsMm[slot].z();
                if (i < RIM_SAMPLES) {
                    double angle = 2.0 * Math.PI * i / RIM_SAMPLES;
                    x += (float) Math.cos(angle) * this.radiusMm;
                    z += (float) Math.sin(angle) * this.radiusMm;
                }
                this.toCamera(x, hubsMm[slot].y() + DISK_LIFT_MM, z, camera);
                points[i * 3] = camera.x;
                points[i * 3 + 1] = camera.y;
                points[i * 3 + 2] = camera.z;
                float depth = -camera.z;
                if (depth > MIN_DEPTH_MM && Math.abs(camera.x) < depth * CONE_TAN && Math.abs(camera.y) < depth * CONE_TAN) {
                    nearest = Math.min(nearest, depth);
                }
            }
            this.samples[slot] = points;
            this.inner[slot] = this.innerSamples(hubsMm[slot]);
            for (int i = 0; i + 2 < this.inner[slot].length; i += 3) {
                float depth = -this.inner[slot][i + 2];
                if (depth > MIN_DEPTH_MM && Math.abs(this.inner[slot][i]) < depth * CONE_TAN && Math.abs(this.inner[slot][i + 1]) < depth * CONE_TAN) {
                    nearest = Math.min(nearest, depth);
                }
            }
        }
        this.guards = new float[guardPointsMm.length / 3 * 3];
        Vector3f camera = new Vector3f();
        for (int i = 0; i + 2 < guardPointsMm.length; i += 3) {
            this.toCamera(guardPointsMm[i], guardPointsMm[i + 1], guardPointsMm[i + 2], camera);
            this.guards[i] = camera.x;
            this.guards[i + 1] = camera.y;
            this.guards[i + 2] = camera.z;
            float depth = -camera.z;
            if (depth > MIN_DEPTH_MM && Math.abs(camera.x) < depth * CONE_TAN && Math.abs(camera.y) < depth * CONE_TAN) {
                nearest = Math.min(nearest, depth);
            }
        }
        this.nearScale = Float.isFinite(nearest) ? Math.clamp(NEAR_TARGET_MM / nearest, 1.0F, MAX_NEAR_SCALE) : 1.0F;
    }

    public static float pushScale(float depthMm, float nearScale) {
        float scale = Float.isFinite(nearScale) ? Math.max(1.0F, nearScale) : 1.0F;
        float scaled = depthMm * scale;
        if (scaled >= NEAR_TARGET_MM) {
            return scale;
        }
        float target = NEAR_FLOOR_MM + (NEAR_TARGET_MM - NEAR_FLOOR_MM) * Math.max(0.0F, scaled) / NEAR_TARGET_MM;
        return target / Math.max(depthMm, 1.0E-4F);
    }

    public boolean guardsVisible(float tanHalfX, float tanHalfY) {
        return inView(this.guards, tanHalfX, tanHalfY);
    }

    public int guardPoints() {
        return this.guards.length / 3;
    }

    public int visibleGuardPoints(float tanHalfX, float tanHalfY) {
        int count = 0;
        for (int i = 0; i + 2 < this.guards.length; i += 3) {
            float depth = -this.guards[i + 2];
            if (depth > MIN_DEPTH_MM && Math.abs(this.guards[i]) <= depth * tanHalfX && Math.abs(this.guards[i + 1]) <= depth * tanHalfY) {
                count++;
            }
        }
        return count;
    }

    private float[] innerSamples(Vector3fc hub) {
        float[] points = new float[(INNER_SAMPLES + 1) * 3];
        Vector3f camera = new Vector3f();
        float y = hub.y() + DISK_LIFT_MM;
        for (int i = 0; i < INNER_SAMPLES; i++) {
            double angle = 2.0 * Math.PI * (i + 0.5) / INNER_SAMPLES;
            this.toCamera(hub.x() + (float) Math.cos(angle) * this.radiusMm * 0.5F, y, hub.z() + (float) Math.sin(angle) * this.radiusMm * 0.5F, camera);
            points[i * 3] = camera.x;
            points[i * 3 + 1] = camera.y;
            points[i * 3 + 2] = camera.z;
        }
        float dx = this.lensMm.x() - hub.x();
        float dz = this.lensMm.z() - hub.z();
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float scale = distance > this.radiusMm ? this.radiusMm / distance : 1.0F;
        this.toCamera(hub.x() + dx * scale, y, hub.z() + dz * scale, camera);
        points[INNER_SAMPLES * 3] = camera.x;
        points[INNER_SAMPLES * 3 + 1] = camera.y;
        points[INNER_SAMPLES * 3 + 2] = camera.z;
        return points;
    }

    public Vector3f toCamera(float xMm, float yMm, float zMm, Vector3f dest) {
        float dx = xMm - this.lensMm.x();
        float dy = yMm - this.lensMm.y();
        float dz = zMm - this.lensMm.z();
        double tilt = Math.toRadians(-this.tiltDegrees);
        float cos = (float) Math.cos(tilt);
        float sin = (float) Math.sin(tilt);
        return dest.set(dx, dy * cos - dz * sin, dy * sin + dz * cos);
    }

    public boolean visible(int slot, float tanHalfX, float tanHalfY) {
        return inView(this.samples[slot], tanHalfX, tanHalfY) || inView(this.inner[slot], tanHalfX, tanHalfY);
    }

    private static boolean inView(float[] points, float tanHalfX, float tanHalfY) {
        for (int i = 0; i + 2 < points.length; i += 3) {
            float depth = -points[i + 2];
            if (depth > MIN_DEPTH_MM && Math.abs(points[i]) <= depth * tanHalfX && Math.abs(points[i + 1]) <= depth * tanHalfY) {
                return true;
            }
        }
        return false;
    }

    public boolean anyVisible(float tanHalfX, float tanHalfY) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.visible(slot, tanHalfX, tanHalfY)) {
                return true;
            }
        }
        return false;
    }

    public int visibleSamples(float tanHalfX, float tanHalfY) {
        int count = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            float[] points = this.samples[slot];
            for (int i = 0; i + 2 < points.length; i += 3) {
                float depth = -points[i + 2];
                if (depth > MIN_DEPTH_MM && Math.abs(points[i]) <= depth * tanHalfX && Math.abs(points[i + 1]) <= depth * tanHalfY) {
                    count++;
                }
            }
        }
        return count;
    }

    public static Vector3f lens(DroneBuild build) {
        double[] lens = FpvRig.lens(build);
        return new Vector3f((float) lens[0], (float) lens[1], (float) lens[2]);
    }

    public static float tanHalfFromDegrees(float fullDegrees) {
        float clamped = Math.clamp(fullDegrees, 1.0F, 179.0F);
        return (float) Math.tan(Math.toRadians(clamped * 0.5));
    }

    public float nearScale() {
        return this.nearScale;
    }

    public Vector3fc hub(int slot) {
        return this.hubs[slot];
    }

    public Vector3fc lensMm() {
        return this.lensMm;
    }

    public float tiltDegrees() {
        return this.tiltDegrees;
    }

    public float radiusMm() {
        return this.radiusMm;
    }
}
