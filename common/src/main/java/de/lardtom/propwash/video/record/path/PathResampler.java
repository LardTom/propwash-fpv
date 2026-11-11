package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PathResampler {
    public static final double ANTI_ALIAS_TICKS = 3.0;
    public static final double QUADRATURE_TICKS = 0.25;

    private PathResampler() {
    }

    public static List<TickPose> perTick(CameraPathBuffer buffer) {
        List<TickPose> out = new ArrayList<>();
        int n = buffer.size();
        if (n == 0) {
            return out;
        }
        if (n == 1) {
            PathSample only = buffer.get(0);
            double t = only.tick();
            if (t == Math.rint(t)) {
                out.add(pose((int) t, only, only, 0.0));
            }
            return out;
        }
        int first = (int) Math.ceil(buffer.firstTick());
        int last = (int) Math.floor(buffer.lastTick());
        int segment = 0;
        for (int tick = first; tick <= last; tick++) {
            segment = segment(buffer, segment, tick);
            out.add(tickPose(buffer, tick, segment));
        }
        return out;
    }

    static int segment(CameraPathBuffer buffer, int from, int tick) {
        int segment = from;
        while (segment + 2 < buffer.size() && buffer.tick(segment + 1) < tick) {
            segment++;
        }
        return segment;
    }

    static TickPose tickPose(CameraPathBuffer buffer, int tick, int segment) {
        PathSample a = buffer.get(segment);
        PathSample b = buffer.get(segment + 1);
        TickPose centre = pose(tick, a, b, alpha(a.tick(), b.tick(), tick));
        double half = Math.min(ANTI_ALIAS_TICKS, Math.min(tick - buffer.firstTick(), buffer.lastTick() - tick));
        if (!(half > 1.0E-9)) {
            return centre;
        }
        Quaterniond rotation = antiAliased(buffer, tick, segment, half, centre.rotation());
        return new TickPose(tick, centre.x(), centre.y(), centre.z(), rotation, centre.fov());
    }

    static double kernel(double ticks) {
        double x = Math.abs(ticks);
        if (x >= ANTI_ALIAS_TICKS) {
            return 0.0;
        }
        return sinc(x) * sinc(x / ANTI_ALIAS_TICKS);
    }

    private static double sinc(double x) {
        if (x < 1.0E-9) {
            return 1.0;
        }
        double angle = Math.PI * x;
        return Math.sin(angle) / angle;
    }

    private static Quaterniond antiAliased(CameraPathBuffer buffer, int tick, int segment, double half, Quaterniond centre) {
        double from = tick - half;
        double to = tick + half;
        int lo = segment;
        while (lo > 0 && buffer.tick(lo) > from) {
            lo--;
        }
        int hi = segment + 1;
        while (hi + 1 < buffer.size() && buffer.tick(hi) < to) {
            hi++;
        }
        Vector3d[] logs = unfolded(buffer, lo, hi, segment, centre);
        Vector3d sum = new Vector3d();
        double weight = 0.0;
        for (int i = lo; i < hi; i++) {
            double startTime = buffer.tick(i);
            double endTime = buffer.tick(i + 1);
            Vector3d start = logs[i - lo];
            Vector3d end = logs[i + 1 - lo];
            double a = Math.max(from, startTime);
            double b = Math.min(to, endTime);
            if (b > a) {
                double span = endTime - startTime;
                int pieces = Math.max(1, (int) Math.ceil((b - a) / QUADRATURE_TICKS));
                double step = (b - a) / pieces;
                for (int k = 0; k < pieces; k++) {
                    double p = a + k * step;
                    double q = p + step;
                    double m = 0.5 * (p + q);
                    double wp = kernel(p - tick) * step / 6.0;
                    double wm = kernel(m - tick) * step * 4.0 / 6.0;
                    double wq = kernel(q - tick) * step / 6.0;
                    sum.fma(wp, start.lerp(end, (p - startTime) / span, new Vector3d()))
                            .fma(wm, start.lerp(end, (m - startTime) / span, new Vector3d()))
                            .fma(wq, start.lerp(end, (q - startTime) / span, new Vector3d()));
                    weight += wp + wm + wq;
                }
            }
        }
        if (!(weight > 0.0)) {
            return centre;
        }
        sum.mul(1.0 / weight);
        return new Quaterniond(centre).mul(exp(sum)).normalize();
    }

    private static Vector3d[] unfolded(CameraPathBuffer buffer, int lo, int hi, int anchor, Quaterniond centre) {
        Quaterniond inverse = new Quaterniond(centre).conjugate();
        Quaterniond scratch = new Quaterniond();
        Vector3d[] logs = new Vector3d[hi - lo + 1];
        logs[anchor - lo] = log(inverse.mul(buffer.rotation(anchor, scratch), new Quaterniond()));
        for (int i = anchor + 1; i <= hi; i++) {
            logs[i - lo] = unfold(logs[i - 1 - lo], log(inverse.mul(buffer.rotation(i, scratch), new Quaterniond())));
        }
        for (int i = anchor - 1; i >= lo; i--) {
            logs[i - lo] = unfold(logs[i + 1 - lo], log(inverse.mul(buffer.rotation(i, scratch), new Quaterniond())));
        }
        return logs;
    }

    static Vector3d unfold(Vector3d previous, Vector3d v) {
        double angle = v.length();
        double reference = previous.length();
        if (!(angle > 1.0E-7) && !(reference > 0.0)) {
            return v;
        }
        Vector3d axis = angle > 1.0E-7 ? new Vector3d(v).div(angle) : new Vector3d(previous).div(reference);
        double turns = Math.rint((previous.dot(axis) - v.dot(axis)) / (2.0 * Math.PI));
        return turns == 0.0 ? v : axis.mul(2.0 * Math.PI * turns).add(v);
    }

    static Vector3d log(Quaterniond q) {
        double sign = q.w < 0.0 ? -1.0 : 1.0;
        double x = sign * q.x;
        double y = sign * q.y;
        double z = sign * q.z;
        double w = sign * q.w;
        double sin = Math.sqrt(x * x + y * y + z * z);
        if (sin < 1.0E-12) {
            return new Vector3d(2.0 * x, 2.0 * y, 2.0 * z);
        }
        double scale = 2.0 * Math.atan2(sin, w) / sin;
        return new Vector3d(x * scale, y * scale, z * scale);
    }

    static Quaterniond exp(Vector3d v) {
        double angle = v.length();
        if (angle < 1.0E-12) {
            return new Quaterniond(0.5 * v.x, 0.5 * v.y, 0.5 * v.z, 1.0).normalize();
        }
        double scale = Math.sin(0.5 * angle) / angle;
        return new Quaterniond(v.x * scale, v.y * scale, v.z * scale, Math.cos(0.5 * angle));
    }

    private static double alpha(double from, double to, double at) {
        double span = to - from;
        return span > 0.0 ? Math.max(0.0, Math.min(1.0, (at - from) / span)) : 0.0;
    }

    public static Optional<PathSample> at(CameraPathBuffer buffer, double tick) {
        int n = buffer.size();
        if (n == 0 || tick < buffer.firstTick() || tick > buffer.lastTick()) {
            return Optional.empty();
        }
        int lo = 0;
        int hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (buffer.tick(mid) <= tick) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        PathSample a = buffer.get(lo);
        PathSample b = buffer.get(hi);
        double span = b.tick() - a.tick();
        double alpha = span > 0.0 ? Math.max(0.0, Math.min(1.0, (tick - a.tick()) / span)) : 0.0;
        TickPose pose = pose(0, a, b, alpha);
        Quaterniond q = pose.rotation();
        return Optional.of(new PathSample(tick, pose.x(), pose.y(), pose.z(), q.x, q.y, q.z, q.w, pose.fov()));
    }

    static TickPose pose(int tick, PathSample a, PathSample b, double alpha) {
        Quaterniond qa = new Quaterniond(a.qx(), a.qy(), a.qz(), a.qw());
        Quaterniond qb = new Quaterniond(b.qx(), b.qy(), b.qz(), b.qw());
        Quaterniond q = alpha <= 0.0 ? qa : alpha >= 1.0 ? qb : qa.slerp(qb, alpha, new Quaterniond());
        q.normalize();
        return new TickPose(tick, lerp(a.x(), b.x(), alpha), lerp(a.y(), b.y(), alpha), lerp(a.z(), b.z(), alpha), q,
                lerp(a.fov(), b.fov(), alpha));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
