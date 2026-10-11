package de.lardtom.propwash.video.record;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Stabilizer {
    public static final float DEFAULT_STRENGTH = 0.5F;
    public static final float SMOOTH_ZOOM = zoom(Mode.SMOOTH, DEFAULT_STRENGTH);
    public static final float HORIZON_ZOOM = zoom(Mode.HORIZON, DEFAULT_STRENGTH);
    public static final double SMOOTH_SECONDS = smoothSeconds(DEFAULT_STRENGTH);
    private static final float SMOOTH_ZOOM_RANGE = 0.3F;
    private static final float HORIZON_ZOOM_BASE = 1.25F;
    private static final float HORIZON_ZOOM_RANGE = 0.7F;
    private static final double SECONDS_BASE = 0.04;
    private static final double SECONDS_RANGE = 0.2;
    private static final int SEARCH_STEPS = 12;
    private static final float[][] PROBES = {
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}
    };

    private final Quaternionf smoothed = new Quaternionf();
    private final Quaternionf target = new Quaternionf();
    private final Quaternionf output = new Quaternionf();
    private final Quaternionf relative = new Quaternionf();
    private final Matrix3f warp = new Matrix3f();
    private final Vector3f ray = new Vector3f();
    private boolean primed;
    private float zoom = 1.0F;
    private float applied;

    public void reset() {
        this.primed = false;
        this.warp.identity();
        this.zoom = 1.0F;
        this.applied = 0.0F;
    }

    public Matrix3f warp() {
        return this.warp;
    }

    public float zoom() {
        return this.zoom;
    }

    public float applied() {
        return this.applied;
    }

    public static float zoom(Mode mode, float strength) {
        float s = clamp(strength);
        return switch (mode) {
            case OFF -> 1.0F;
            case SMOOTH -> 1.0F + SMOOTH_ZOOM_RANGE * s;
            case HORIZON -> HORIZON_ZOOM_BASE + HORIZON_ZOOM_RANGE * s;
        };
    }

    public static double smoothSeconds(float strength) {
        return SECONDS_BASE + SECONDS_RANGE * clamp(strength);
    }

    public static float horizonHoldDegrees(float zoom, float aspect) {
        float lo = 0.0F;
        float hi = 45.0F;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5F;
            double r = Math.toRadians(mid);
            if (Math.cos(r) + aspect * Math.sin(r) <= zoom) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static float clamp(float strength) {
        return Float.isFinite(strength) ? Math.max(0.0F, Math.min(1.0F, strength)) : DEFAULT_STRENGTH;
    }

    public void update(Mode mode, Quaternionfc actual, double seconds, float tanSourceX, float tanSourceY, float tanOutX, float tanOutY) {
        this.update(mode, DEFAULT_STRENGTH, actual, seconds, tanSourceX, tanSourceY, tanOutX, tanOutY);
    }

    public void update(Mode mode, float strength, Quaternionfc actual, double seconds, float tanSourceX, float tanSourceY, float tanOutX,
                       float tanOutY) {
        if (mode == Mode.OFF) {
            this.reset();
            return;
        }
        this.zoom = zoom(mode, strength);
        if (!this.primed) {
            this.smoothed.set(actual);
            this.primed = true;
        } else {
            double alpha = 1.0 - Math.exp(-Math.max(0.0, seconds) / smoothSeconds(strength));
            this.smoothed.slerp(actual, (float) alpha).normalize();
        }
        this.target.set(this.smoothed);
        if (mode == Mode.HORIZON) {
            level(this.smoothed, this.target);
        }
        float t = 1.0F;
        if (!this.fits(actual, this.target, 1.0F, tanSourceX, tanSourceY, tanOutX, tanOutY)) {
            float lo = 0.0F;
            float hi = 1.0F;
            for (int i = 0; i < SEARCH_STEPS; i++) {
                float mid = (lo + hi) * 0.5F;
                if (this.fits(actual, this.target, mid, tanSourceX, tanSourceY, tanOutX, tanOutY)) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            t = lo;
            Quaternionf pulled = new Quaternionf(actual).slerp(this.smoothed, t);
            this.smoothed.set(pulled);
        }
        this.applied = t;
        new Quaternionf(actual).slerp(this.target, t, this.output);
        this.relative.set(actual).conjugate().mul(this.output);
        this.warp.set(this.relative);
    }

    private boolean fits(Quaternionfc actual, Quaternionfc wanted, float t, float tanSourceX, float tanSourceY, float tanOutX, float tanOutY) {
        new Quaternionf(actual).slerp(wanted, t, this.output);
        this.relative.set(actual).conjugate().mul(this.output);
        Matrix3f m = new Matrix3f().set(this.relative);
        for (float[] probe : PROBES) {
            this.ray.set(probe[0] * tanOutX, probe[1] * tanOutY, -1.0F);
            m.transform(this.ray);
            if (this.ray.z >= -1.0E-4F) {
                return false;
            }
            float x = this.ray.x / -this.ray.z;
            float y = this.ray.y / -this.ray.z;
            if (Math.abs(x) > tanSourceX * 1.0001F || Math.abs(y) > tanSourceY * 1.0001F) {
                return false;
            }
        }
        return true;
    }

    public static Quaternionf level(Quaternionfc orientation, Quaternionf dest) {
        Vector3f forward = orientation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (Math.abs(forward.y) > 0.98F) {
            return dest.set(orientation);
        }
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        Vector3f back = new Vector3f(forward).negate();
        Matrix3f basis = new Matrix3f(right, up, back);
        return dest.setFromNormalized(basis).normalize();
    }

    public static float rollDegrees(Quaternionfc orientation) {
        Vector3f forward = orientation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f levelRight = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F);
        if (levelRight.lengthSquared() < 1.0E-8F) {
            return 0.0F;
        }
        levelRight.normalize();
        Vector3f levelUp = new Vector3f(levelRight).cross(forward).normalize();
        float cos = up.dot(levelUp);
        float sin = up.dot(levelRight);
        return (float) Math.toDegrees(Math.atan2(sin, cos));
    }

    public enum Mode {
        OFF("off"),
        SMOOTH("smooth"),
        HORIZON("horizon");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public String translationKey() {
            return "propwash.video.record.stabilization." + this.key;
        }
    }
}
