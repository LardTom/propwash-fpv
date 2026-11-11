package de.lardtom.propwash.osd;

import de.lardtom.propwash.drone.net.StateValidation;
import org.joml.Vector2f;

public final class FlightPathMarker {
    public static final double HIDDEN_BELOW = 1.0;
    public static final double FULL_FROM = 2.0;
    public static final long SPAN_NANOS = 50_000_000L;
    public static final double SMOOTHING_SECONDS = 0.04;
    public static final double RESTART_SECONDS = 0.5;
    public static final double MAX_SPEED = StateValidation.PHYSICS_MAX_SPEED;

    private final Vector2f point = new Vector2f();

    public static float alpha(double speed) {
        if (!(speed >= HIDDEN_BELOW)) {
            return 0.0F;
        }
        return (float) Math.min(1.0, (speed - HIDDEN_BELOW) / (FULL_FROM - HIDDEN_BELOW));
    }

    public State compute(double vx, double vy, double vz, OsdProjection projection, float left, float top, float right, float bottom,
                         State out) {
        out.clear();
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (!projection.valid() || !Double.isFinite(speed) || !(right > left) || !(bottom > top)) {
            return out;
        }
        float alpha = alpha(speed);
        if (alpha <= 0.0F) {
            return out;
        }
        out.visible = true;
        out.alpha = alpha;
        boolean front = projection.project(vx, vy, vz, this.point);
        if (front && this.point.x >= left && this.point.x <= right && this.point.y >= top && this.point.y <= bottom) {
            out.inView = true;
            out.x = this.point.x;
            out.y = this.point.y;
            out.edgeAngle = (float) Math.atan2(this.point.y - (top + bottom) * 0.5F, this.point.x - (left + right) * 0.5F);
            return out;
        }
        float angle = projection.screenAngle(vx, vy, vz);
        out.inView = false;
        out.edgeAngle = angle;
        out.x = (left + right) * 0.5F + (float) Math.cos(angle) * (right - left) * 0.5F;
        out.y = (top + bottom) * 0.5F + (float) Math.sin(angle) * (bottom - top) * 0.5F;
        return out;
    }

    public static final class State {
        public boolean visible;
        public float alpha;
        public float x;
        public float y;
        public boolean inView;
        public float edgeAngle;

        public void clear() {
            this.visible = false;
            this.alpha = 0.0F;
            this.x = Float.NaN;
            this.y = Float.NaN;
            this.inView = false;
            this.edgeAngle = 0.0F;
        }
    }

    public static final class Velocity {
        private static final long NONE = Long.MIN_VALUE;

        private double x = Double.NaN;
        private double y = Double.NaN;
        private double z = Double.NaN;
        private long lastNanos = NONE;

        public void reset() {
            this.x = Double.NaN;
            this.y = Double.NaN;
            this.z = Double.NaN;
            this.lastNanos = NONE;
        }

        public boolean known() {
            return this.lastNanos != NONE;
        }

        public double x() {
            return this.x;
        }

        public double y() {
            return this.y;
        }

        public double z() {
            return this.z;
        }

        public boolean update(long nanos, double dx, double dy, double dz, long spanNanos) {
            if (spanNanos <= 0L) {
                this.reset();
                return false;
            }
            double seconds = spanNanos * 1.0E-9;
            double rawX = dx / seconds;
            double rawY = dy / seconds;
            double rawZ = dz / seconds;
            double speed = Math.sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ);
            if (!Double.isFinite(speed) || speed > MAX_SPEED) {
                this.reset();
                return false;
            }
            if (this.lastNanos == NONE || nanos < this.lastNanos || (nanos - this.lastNanos) * 1.0E-9 > RESTART_SECONDS) {
                this.x = rawX;
                this.y = rawY;
                this.z = rawZ;
                this.lastNanos = nanos;
                return true;
            }
            double dt = (nanos - this.lastNanos) * 1.0E-9;
            if (dt > 0.0) {
                double blend = 1.0 - Math.exp(-dt / SMOOTHING_SECONDS);
                this.x += (rawX - this.x) * blend;
                this.y += (rawY - this.y) * blend;
                this.z += (rawZ - this.z) * blend;
                this.lastNanos = nanos;
            }
            return true;
        }
    }
}
