package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

public record PathPreview(CameraTrack.Built built, int ticks, int substeps, double[] truth, double[] actual, double[] saved, double[] truthForward,
                          double[] savedForward, float[] positionError, float[] angleError, int[] keyIndices, double maxOffPath,
                          double maxSameTime, double maxAngle, int worstIndex, Gap topGap, Gap sideGap, int fovKeyframes, float uiScale,
                          int maxKeyframes) {
    public static final int SUBSTEPS = 4;
    public static final int PATH_WINDOW_TICKS = 20;

    public record Gap(double distance, double time, double ax, double ay, double az, double bx, double by, double bz) {
        public static final Gap NONE = new Gap(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        public int index() {
            return (int) Math.floor(this.time);
        }

        public boolean visible() {
            return this.distance > 0.0;
        }
    }

    public static PathPreview of(PathFrames frames, Simplification wanted, boolean limit, float uiScale) {
        int fov = CameraTrack.fovKeys(frames.poses()).size();
        int max = FlashbackBudget.maxCameraKeyframes(uiScale, fov);
        CameraTrack.Built built = CameraTrack.build(frames, wanted, limit ? max : Integer.MAX_VALUE);
        return of(frames, built, fov, uiScale, max);
    }

    public static PathPreview of(PathFrames frames, CameraTrack.Built built, int fovKeyframes, float uiScale, int maxKeyframes) {
        int n = frames.size();
        List<PathKeyframe> keys = built.track().camera();
        double[] truth = new double[n * 3];
        double[] truthForward = new double[n * 3];
        double[] savedForward = new double[n * 3];
        float[] positionError = new float[n];
        float[] angleError = new float[n];
        int samples = n <= 1 ? n : (n - 1) * SUBSTEPS + 1;
        double[] saved = new double[samples * 3];
        Vector3d forward = new Vector3d();
        double maxSameTime = 0.0;
        double maxAngle = 0.0;
        int worst = 0;
        int[] keyIndices = new int[keys.size()];
        for (int k = 0; k < keys.size(); k++) {
            keyIndices[k] = keys.get(k).tick() - (n == 0 ? 0 : frames.firstTick());
        }
        int segment = 0;
        for (int i = 0; i < n; i++) {
            TickPose pose = frames.pose(i);
            truth[i * 3] = pose.x();
            truth[i * 3 + 1] = pose.y();
            truth[i * 3 + 2] = pose.z();
            forward(frames.rotation(i), forward);
            truthForward[i * 3] = forward.x;
            truthForward[i * 3 + 1] = forward.y;
            truthForward[i * 3 + 2] = forward.z;
            if (keys.isEmpty()) {
                continue;
            }
            FlashbackSpline.Pose at = FlashbackSpline.at(keys, pose.tick());
            Quaterniond rotation = at.angles().rotation();
            forward(rotation, forward);
            savedForward[i * 3] = forward.x;
            savedForward[i * 3 + 1] = forward.y;
            savedForward[i * 3 + 2] = forward.z;
            double dx = at.x() - pose.x();
            double dy = at.y() - pose.y();
            double dz = at.z() - pose.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            while (segment + 1 < keyIndices.length && keyIndices[segment + 1] <= i) {
                segment++;
            }
            double slack = keyIndices[segment] == i || segment + 1 >= keyIndices.length ? frames.keyframeAngleError(i)
                    : Math.max(frames.keyframeAngleError(keyIndices[segment]), frames.keyframeAngleError(keyIndices[segment + 1]));
            double angle = Math.max(0.0, CameraAngles.degreesBetween(rotation, frames.rotation(i)) - slack);
            positionError[i] = (float) distance;
            angleError[i] = (float) angle;
            if (distance > maxSameTime) {
                maxSameTime = distance;
                worst = i;
            }
            maxAngle = Math.max(maxAngle, angle);
        }
        if (!keys.isEmpty()) {
            int first = frames.firstTick();
            for (int s = 0; s < samples; s++) {
                FlashbackSpline.Pose at = FlashbackSpline.at(keys, (float) (first + s / (double) SUBSTEPS));
                saved[s * 3] = at.x();
                saved[s * 3 + 1] = at.y();
                saved[s * 3 + 2] = at.z();
            }
        }
        double[] actual = actual(frames, samples);
        double[] gaps = keys.isEmpty() ? new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0} : gaps(actual, saved);
        Gap top = gaps[1] > 0.0 ? new Gap(gaps[1], gaps[2], gaps[3], gaps[4], gaps[5], gaps[6], gaps[7], gaps[8]) : Gap.NONE;
        Gap side = keys.isEmpty() ? Gap.NONE : heightGap(actual, saved);
        return new PathPreview(built, n, SUBSTEPS, truth, actual, saved, truthForward, savedForward, positionError, angleError, keyIndices, gaps[0],
                maxSameTime, maxAngle, worst, top, side, fovKeyframes, uiScale, maxKeyframes);
    }

    static double[] actual(PathFrames frames, int samples) {
        int n = frames.size();
        double[] actual = new double[samples * 3];
        for (int i = 0; i < n; i++) {
            TickPose pose = frames.pose(i);
            int s = i * SUBSTEPS;
            if (s >= samples) {
                break;
            }
            actual[s * 3] = pose.x();
            actual[s * 3 + 1] = pose.y();
            actual[s * 3 + 2] = pose.z();
        }
        for (int i = 0; i + 1 < n; i++) {
            PathKeyframe before = frames.keyframe(Math.max(0, i - 1));
            PathKeyframe low = frames.keyframe(i);
            PathKeyframe high = frames.keyframe(i + 1);
            PathKeyframe after = frames.keyframe(Math.min(n - 1, i + 2));
            for (int sub = 1; sub < SUBSTEPS; sub++) {
                int s = i * SUBSTEPS + sub;
                FlashbackSpline.Pose at = FlashbackSpline.segment(before, low, high, after, (float) (frames.firstTick() + s / (double) SUBSTEPS));
                actual[s * 3] = at.x();
                actual[s * 3 + 1] = at.y();
                actual[s * 3 + 2] = at.z();
            }
        }
        return actual;
    }

    static double[] gaps(double[] actual, double[] saved) {
        double[] result = new double[9];
        scan(actual, saved, result, false);
        scan(saved, actual, result, true);
        return result;
    }

    private static void scan(double[] from, double[] onto, double[] result, boolean fromSaved) {
        int samples = from.length / 3;
        int segments = onto.length / 3 - 1;
        int window = PATH_WINDOW_TICKS * SUBSTEPS;
        double[] closest = new double[3];
        double[] best = new double[3];
        for (int s = 0; s < samples; s++) {
            double px = from[s * 3];
            double py = from[s * 3 + 1];
            double pz = from[s * 3 + 2];
            int lo = Math.max(0, s - window);
            int hi = Math.min(segments, s + window);
            double nearest = Double.POSITIVE_INFINITY;
            double flat = Double.POSITIVE_INFINITY;
            for (int k = lo; k < hi; k++) {
                nearest = Math.min(nearest, segmentDistanceSquared(px, py, pz, onto, k));
                double d = flatDistanceSquared(px, pz, onto, k, closest);
                if (d < flat) {
                    flat = d;
                    best[0] = closest[0];
                    best[1] = closest[1];
                    best[2] = closest[2];
                }
            }
            if (lo >= hi) {
                int k = Math.min(lo, onto.length / 3 - 1);
                double dx = onto[k * 3] - px;
                double dy = onto[k * 3 + 1] - py;
                double dz = onto[k * 3 + 2] - pz;
                nearest = dx * dx + dy * dy + dz * dz;
                flat = dx * dx + dz * dz;
                best[0] = onto[k * 3];
                best[1] = onto[k * 3 + 1];
                best[2] = onto[k * 3 + 2];
            }
            if (Double.isFinite(nearest)) {
                result[0] = Math.max(result[0], Math.sqrt(nearest));
            }
            if (Double.isFinite(flat) && Math.sqrt(flat) > result[1]) {
                result[1] = Math.sqrt(flat);
                result[2] = s / (double) SUBSTEPS;
                int a = fromSaved ? 6 : 3;
                int b = fromSaved ? 3 : 6;
                result[a] = px;
                result[a + 1] = py;
                result[a + 2] = pz;
                result[b] = best[0];
                result[b + 1] = best[1];
                result[b + 2] = best[2];
            }
        }
    }

    static Gap heightGap(double[] actual, double[] saved) {
        int samples = Math.min(actual.length, saved.length) / 3;
        double worst = 0.0;
        int at = -1;
        for (int s = 0; s < samples; s++) {
            double d = Math.abs(saved[s * 3 + 1] - actual[s * 3 + 1]);
            if (d > worst) {
                worst = d;
                at = s;
            }
        }
        if (at < 0) {
            return Gap.NONE;
        }
        return new Gap(worst, at / (double) SUBSTEPS, actual[at * 3], actual[at * 3 + 1], actual[at * 3 + 2], saved[at * 3], saved[at * 3 + 1],
                saved[at * 3 + 2]);
    }

    static void forward(Quaterniond rotation, Vector3d dest) {
        rotation.transform(dest.set(0.0, 0.0, -1.0));
    }

    private static double segmentDistanceSquared(double px, double py, double pz, double[] path, int s) {
        double ax = path[s * 3];
        double ay = path[s * 3 + 1];
        double az = path[s * 3 + 2];
        double bx = path[s * 3 + 3];
        double by = path[s * 3 + 4];
        double bz = path[s * 3 + 5];
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double length = vx * vx + vy * vy + vz * vz;
        double t = length > 0.0 ? ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / length : 0.0;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = ax + vx * t - px;
        double dy = ay + vy * t - py;
        double dz = az + vz * t - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double flatDistanceSquared(double px, double pz, double[] path, int s, double[] closest) {
        double ax = path[s * 3];
        double ay = path[s * 3 + 1];
        double az = path[s * 3 + 2];
        double vx = path[s * 3 + 3] - ax;
        double vy = path[s * 3 + 4] - ay;
        double vz = path[s * 3 + 5] - az;
        double length = vx * vx + vz * vz;
        double t = length > 0.0 ? ((px - ax) * vx + (pz - az) * vz) / length : 0.0;
        t = Math.max(0.0, Math.min(1.0, t));
        closest[0] = ax + vx * t;
        closest[1] = ay + vy * t;
        closest[2] = az + vz * t;
        double dx = closest[0] - px;
        double dz = closest[2] - pz;
        return dx * dx + dz * dz;
    }

    public int keyframes() {
        return this.built.track().camera().size();
    }

    public double durationSeconds() {
        return this.built.track().durationSeconds();
    }

    public double fill() {
        return FlashbackBudget.fill(this.keyframes(), this.fovKeyframes, this.uiScale);
    }

    public double secondsThatFit() {
        return FlashbackBudget.secondsThatFit(this.built.wantedKeyframes(), this.durationSeconds(), this.uiScale, this.fovKeyframes);
    }

    public boolean fits() {
        return this.keyframes() <= this.maxKeyframes;
    }
}
