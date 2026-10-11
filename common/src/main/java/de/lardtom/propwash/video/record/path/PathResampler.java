package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PathResampler {
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
        PathSample a = buffer.get(0);
        PathSample b = buffer.get(1);
        for (int tick = first; tick <= last; tick++) {
            while (segment + 2 < n && buffer.tick(segment + 1) < tick) {
                segment++;
                a = b;
                b = buffer.get(segment + 1);
            }
            double span = b.tick() - a.tick();
            double alpha = span > 0.0 ? (tick - a.tick()) / span : 0.0;
            out.add(pose(tick, a, b, Math.max(0.0, Math.min(1.0, alpha))));
        }
        return out;
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
