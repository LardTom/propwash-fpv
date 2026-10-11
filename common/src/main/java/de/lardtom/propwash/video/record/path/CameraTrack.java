package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record CameraTrack(int startTick, int endTick, List<PathKeyframe> camera, List<FovKeyframe> fov) {
    public static final int MAX_SPAN_TICKS = 10;
    public static final double FOV_STEP_DEGREES = 0.05;
    public static final CameraTrack EMPTY = new CameraTrack(0, 0, List.of(), List.of());

    public CameraTrack {
        camera = List.copyOf(camera);
        fov = List.copyOf(fov);
    }

    public static CameraTrack of(CameraPathBuffer buffer, KeyframeDensity density) {
        return of(PathResampler.perTick(buffer), density);
    }

    public static CameraTrack of(List<TickPose> poses, KeyframeDensity density) {
        if (poses.size() < 2) {
            return EMPTY;
        }
        List<Quaterniond> rotations = new ArrayList<>(poses.size());
        for (TickPose pose : poses) {
            rotations.add(pose.rotation());
        }
        List<CameraAngles> angles = EulerPath.continuous(rotations);
        boolean[] keep = density == KeyframeDensity.EVERY_TICK ? all(poses.size()) : thin(poses, rotations, angles, density);
        List<PathKeyframe> camera = new ArrayList<>();
        for (int i = 0; i < poses.size(); i++) {
            if (keep[i]) {
                TickPose pose = poses.get(i);
                CameraAngles a = angles.get(i);
                camera.add(new PathKeyframe(pose.tick(), pose.x(), pose.y(), pose.z(), (float) a.yaw(), (float) a.pitch(), (float) a.roll()));
            }
        }
        return new CameraTrack(poses.getFirst().tick(), poses.getLast().tick(), camera, fovKeys(poses));
    }

    static List<FovKeyframe> fovKeys(List<TickPose> poses) {
        List<FovKeyframe> keys = new ArrayList<>();
        float current = (float) poses.getFirst().fov();
        keys.add(new FovKeyframe(poses.getFirst().tick(), current));
        for (int i = 1; i < poses.size(); i++) {
            float value = (float) poses.get(i).fov();
            if (Math.abs(value - current) > FOV_STEP_DEGREES) {
                current = value;
                keys.add(new FovKeyframe(poses.get(i).tick(), current));
            }
        }
        int end = poses.getLast().tick();
        if (keys.getLast().tick() != end) {
            keys.add(new FovKeyframe(end, current));
        }
        return keys;
    }

    private static boolean[] all(int n) {
        boolean[] keep = new boolean[n];
        Arrays.fill(keep, true);
        return keep;
    }

    static boolean[] thin(List<TickPose> poses, List<Quaterniond> rotations, List<CameraAngles> angles, KeyframeDensity density) {
        int n = poses.size();
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        int anchor = 0;
        while (anchor < n - 1) {
            int reach = anchor + 1;
            for (int j = anchor + 2; j < n && j - anchor <= MAX_SPAN_TICKS; j++) {
                if (!fits(poses, rotations, angles, anchor, j, density)) {
                    break;
                }
                reach = j;
            }
            keep[reach] = true;
            anchor = reach;
        }
        return keep;
    }

    private static boolean fits(List<TickPose> poses, List<Quaterniond> rotations, List<CameraAngles> angles, int from, int to,
                                KeyframeDensity density) {
        TickPose a = poses.get(from);
        TickPose b = poses.get(to);
        double span = to - from;
        double positionLimit = density.positionTolerance() * density.positionTolerance();
        for (int k = from + 1; k < to; k++) {
            double t = (k - from) / span;
            TickPose truth = poses.get(k);
            double dx = a.x() + (b.x() - a.x()) * t - truth.x();
            double dy = a.y() + (b.y() - a.y()) * t - truth.y();
            double dz = a.z() + (b.z() - a.z()) * t - truth.z();
            if (dx * dx + dy * dy + dz * dz > positionLimit) {
                return false;
            }
            Quaterniond guess = angles.get(from).lerpShortest(angles.get(to), t).rotation();
            if (CameraAngles.degreesBetween(guess, rotations.get(k)) > density.angleTolerance()) {
                return false;
            }
        }
        return true;
    }

    public boolean usable() {
        return this.camera.size() >= 2 && this.endTick > this.startTick;
    }

    public int durationTicks() {
        return Math.max(0, this.endTick - this.startTick);
    }

    public double durationSeconds() {
        return this.durationTicks() / 20.0;
    }
}
