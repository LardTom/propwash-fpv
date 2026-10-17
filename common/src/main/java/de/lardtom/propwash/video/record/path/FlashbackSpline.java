package de.lardtom.propwash.video.record.path;

import org.joml.Vector3d;

import java.util.List;

public final class FlashbackSpline {
    private static final float MIN_FACTOR = 0.4F;
    private static final double MAX_FACTOR_POSITION = 2.5;
    private static final float MAX_FACTOR = 2.5F;

    private FlashbackSpline() {
    }

    public record Pose(double x, double y, double z, float yaw, float pitch, float roll) {
        public CameraAngles angles() {
            return new CameraAngles(this.yaw, this.pitch, this.roll);
        }
    }

    public static Pose at(List<PathKeyframe> keys, float tick) {
        int n = keys.size();
        if (n == 0) {
            throw new IllegalArgumentException("no keyframes");
        }
        int lower = floorIndex(keys, (int) Math.floor(tick));
        if (lower < 0) {
            PathKeyframe first = keys.getFirst();
            return pose(first);
        }
        PathKeyframe low = keys.get(lower);
        if (tick == low.tick() || lower + 1 >= n) {
            return pose(low);
        }
        return segment(keys.get(Math.max(0, lower - 1)), low, keys.get(lower + 1), keys.get(Math.min(n - 1, lower + 2)), tick);
    }

    public static Pose segment(PathKeyframe before, PathKeyframe low, PathKeyframe high, PathKeyframe after, float tick) {
        float t0 = before.tick();
        float t1 = low.tick();
        float t2 = high.tick();
        float t3 = after.tick();
        float amount = (tick - t1) / (t2 - t1);
        float time1 = t1 - t0;
        float time2 = t2 - t0;
        float time3 = t3 - t0;
        Vector3d p = position(new Vector3d(before.x(), before.y(), before.z()), new Vector3d(low.x(), low.y(), low.z()),
                new Vector3d(high.x(), high.y(), high.z()), new Vector3d(after.x(), after.y(), after.z()), time1, time2, time3, amount);
        float yaw = degrees(before.yaw(), low.yaw(), high.yaw(), after.yaw(), time1, time2, time3, amount);
        float pitch = degrees(before.pitch(), low.pitch(), high.pitch(), after.pitch(), time1, time2, time3, amount);
        float roll = degrees(before.roll(), low.roll(), high.roll(), after.roll(), time1, time2, time3, amount);
        return new Pose(p.x, p.y, p.z, yaw, pitch, roll);
    }

    private static Pose pose(PathKeyframe key) {
        return new Pose(key.x(), key.y(), key.z(), key.yaw(), key.pitch(), key.roll());
    }

    static int floorIndex(List<PathKeyframe> keys, int tick) {
        int lo = 0;
        int hi = keys.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (keys.get(mid).tick() <= tick) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    private static double positionTj(Vector3d a, Vector3d b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double dz = b.z - a.z;
        return Math.pow(dx * dx + dy * dy + dz * dz, 0.25);
    }

    public static Vector3d position(Vector3d p0, Vector3d p1, Vector3d p2, Vector3d p3, float time1, float time2, float time3, float amount) {
        double tj1 = positionTj(p0, p1);
        double tj2 = positionTj(p1, p2);
        double tj3 = positionTj(p2, p3);
        double averageTj = (tj1 + tj2 + tj3) / 3.0;
        if (averageTj == 0.0) {
            return new Vector3d(p1);
        }
        double averageTime = time3 / 3.0F;
        double relation = averageTime / averageTj;
        double delta1 = 0.0;
        if (time1 > 0.0F) {
            double factor = tj1 * relation / time1;
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR_POSITION, factor));
            delta1 = tj1 * relation / factor;
        }
        double delta2 = 0.0;
        if (time2 - time1 > 0.0F) {
            double factor = tj2 * relation / (time2 - time1);
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR_POSITION, factor));
            delta2 = tj2 * relation / factor;
        }
        double delta3 = 0.0;
        if (time3 - time2 > 0.0F) {
            double factor = tj3 * relation / (time3 - time2);
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR_POSITION, factor));
            delta3 = tj3 * relation / factor;
        }
        double t0 = 0.0;
        double t1 = t0 + delta1;
        double t2 = t1 + delta2;
        double t3 = t2 + delta3;
        double t = t1 + (t2 - t1) * amount;
        Vector3d a1 = t0 == t1 ? p0.lerp(p1, 0.5, new Vector3d()) : p0.lerp(p1, (t - t0) / (t1 - t0), new Vector3d());
        Vector3d a2 = t1 == t2 ? p1.lerp(p2, 0.5, new Vector3d()) : p1.lerp(p2, (t - t1) / (t2 - t1), new Vector3d());
        Vector3d a3 = t2 == t3 ? p2.lerp(p3, 0.5, new Vector3d()) : p2.lerp(p3, (t - t2) / (t3 - t2), new Vector3d());
        Vector3d b1 = t0 == t2 ? a1.lerp(a2, 0.5, new Vector3d()) : a1.lerp(a2, (t - t0) / (t2 - t0), new Vector3d());
        Vector3d b2 = t1 == t3 ? a2.lerp(a3, 0.5, new Vector3d()) : a2.lerp(a3, (t - t1) / (t3 - t1), new Vector3d());
        return t1 == t2 ? b1.lerp(b2, 0.5, new Vector3d()) : b1.lerp(b2, (t - t1) / (t2 - t1), new Vector3d());
    }

    public static float degrees(float q0, float q1, float q2, float q3, float time1, float time2, float time3, float amount) {
        float delta1 = wrap(q1 - q0);
        float delta2 = wrap(q2 - q1);
        float delta3 = wrap(q3 - q2);
        float p0 = wrap(q0);
        float p1 = p0 + delta1;
        float p2 = p1 + delta2;
        float p3 = p2 + delta3;
        float tj1 = degreesTj(p0, p1);
        float tj2 = degreesTj(p1, p2);
        float tj3 = degreesTj(p2, p3);
        float averageTj = (tj1 + tj2 + tj3) / 3.0F;
        if (averageTj == 0.0) {
            return p1;
        }
        float averageTime = time3 / 3.0F;
        float relation = averageTime / averageTj;
        float d1 = 0.0F;
        if (time1 > 0.0F) {
            float factor = tj1 * relation / time1;
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
            d1 = tj1 * relation / factor;
        }
        float d2 = 0.0F;
        if (time2 - time1 > 0.0F) {
            float factor = tj2 * relation / (time2 - time1);
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
            d2 = tj2 * relation / factor;
        }
        float d3 = 0.0F;
        if (time3 - time2 > 0.0F) {
            float factor = tj3 * relation / (time3 - time2);
            factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
            d3 = tj3 * relation / factor;
        }
        float t0 = 0.0F;
        float t1 = t0 + d1;
        float t2 = t1 + d2;
        float t3 = t2 + d3;
        float t = t1 + (t2 - t1) * amount;
        float a1 = t0 == t1 ? linear(p0, p1, 0.5F) : linear(p0, p1, (t - t0) / (t1 - t0));
        float a2 = t1 == t2 ? linear(p1, p2, 0.5F) : linear(p1, p2, (t - t1) / (t2 - t1));
        float a3 = t2 == t3 ? linear(p2, p3, 0.5F) : linear(p2, p3, (t - t2) / (t3 - t2));
        float b1 = t0 == t2 ? linear(a1, a2, 0.5F) : linear(a1, a2, (t - t0) / (t2 - t0));
        float b2 = t1 == t3 ? linear(a2, a3, 0.5F) : linear(a2, a3, (t - t1) / (t3 - t1));
        return linear(b1, b2, amount);
    }

    private static float degreesTj(float a, float b) {
        return (float) Math.sqrt(Math.abs(wrap(a - b)));
    }

    private static float linear(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    static float wrap(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }
}
