package de.lardtom.propwash.video.record.path;

import java.util.ArrayList;
import java.util.List;

public record CameraTrack(int startTick, int endTick, List<PathKeyframe> camera, List<FovKeyframe> fov) {
    public static final int MAX_SPAN_TICKS = 10;
    public static final double FOV_STEP_DEGREES = 0.05;
    public static final CameraTrack EMPTY = new CameraTrack(0, 0, List.of(), List.of());
    public static final double MAX_COARSE_FACTOR = 1024.0;
    public static final int COARSE_SEARCH_STEPS = 6;

    public CameraTrack {
        camera = List.copyOf(camera);
        fov = List.copyOf(fov);
    }

    public static CameraTrack of(CameraPathBuffer buffer, KeyframeDensity density) {
        return of(PathResampler.perTick(buffer), density);
    }

    public static CameraTrack of(List<TickPose> poses, KeyframeDensity density) {
        return build(poses, density == KeyframeDensity.EVERY_TICK ? Simplification.OFF : Simplification.ofPosition(density.positionTolerance()),
                Integer.MAX_VALUE).track();
    }

    public static Built build(List<TickPose> poses, Simplification wanted, int maxKeyframes) {
        return build(PathFrames.of(poses), wanted, maxKeyframes);
    }

    public static Built build(PathFrames frames, Simplification wanted, int maxKeyframes) {
        if (frames.size() < 2) {
            return new Built(EMPTY, wanted, wanted, 0, maxKeyframes);
        }
        List<FovKeyframe> fov = fovKeys(frames.poses());
        int[] keys = thin(frames, wanted);
        int wantedCount = keys.length;
        if (wantedCount <= maxKeyframes) {
            return new Built(assemble(frames, keys, fov), wanted, wanted, wantedCount, maxKeyframes);
        }
        int limit = Math.max(2, maxKeyframes);
        double failing = wanted.off() ? 0.0 : 1.0;
        double fitting = Double.NaN;
        int[] fitKeys = null;
        for (double factor = wanted.off() ? 1.0 : 2.0; factor <= MAX_COARSE_FACTOR; factor *= 2.0) {
            int[] candidate = thin(frames, wanted.coarser(factor));
            if (candidate.length <= limit) {
                fitting = factor;
                fitKeys = candidate;
                break;
            }
            failing = factor;
        }
        if (fitKeys == null) {
            int[] uniform = uniform(frames.size(), limit);
            return new Built(assemble(frames, uniform, fov), wanted, wanted.coarser(MAX_COARSE_FACTOR), wantedCount, maxKeyframes);
        }
        if (failing > 0.0) {
            for (int i = 0; i < COARSE_SEARCH_STEPS; i++) {
                double middle = Math.sqrt(failing * fitting);
                int[] candidate = thin(frames, wanted.coarser(middle));
                if (candidate.length <= limit) {
                    fitting = middle;
                    fitKeys = candidate;
                } else {
                    failing = middle;
                }
            }
        }
        return new Built(assemble(frames, fitKeys, fov), wanted, wanted.coarser(fitting), wantedCount, maxKeyframes);
    }

    public static double smallestFittingSlider(PathFrames frames, int maxKeyframes) {
        if (frames.size() <= maxKeyframes) {
            return 0.0;
        }
        if (thin(frames, Simplification.ofSlider(1.0)).length > maxKeyframes) {
            return 1.0;
        }
        int failing = 0;
        int fitting = 100;
        while (fitting - failing > 1) {
            int middle = (failing + fitting) / 2;
            if (thin(frames, Simplification.ofSlider(middle / 100.0)).length <= maxKeyframes) {
                fitting = middle;
            } else {
                failing = middle;
            }
        }
        return fitting / 100.0;
    }

    static int[] thin(PathFrames frames, Simplification tolerance) {
        TrackThinner thinner = new TrackThinner(frames, tolerance);
        return thinner.finish(true);
    }

    static int[] uniform(int n, int count) {
        int[] keys = new int[Math.min(n, Math.max(2, count))];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = (int) Math.round(i * (n - 1) / (double) (keys.length - 1));
        }
        return keys;
    }

    static CameraTrack assemble(PathFrames frames, int[] keys, List<FovKeyframe> fov) {
        List<PathKeyframe> camera = new ArrayList<>(keys.length);
        for (int index : keys) {
            camera.add(frames.keyframe(index));
        }
        return new CameraTrack(frames.firstTick(), frames.lastTick(), camera, fov);
    }

    public static List<FovKeyframe> fovKeys(List<TickPose> poses) {
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

    public boolean usable() {
        return this.camera.size() >= 2 && this.endTick > this.startTick;
    }

    public int durationTicks() {
        return Math.max(0, this.endTick - this.startTick);
    }

    public double durationSeconds() {
        return this.durationTicks() / 20.0;
    }

    public record Built(CameraTrack track, Simplification wanted, Simplification used, int wantedKeyframes, int maxKeyframes) {
        public boolean capped() {
            return this.track.camera().size() < this.wantedKeyframes;
        }
    }
}
