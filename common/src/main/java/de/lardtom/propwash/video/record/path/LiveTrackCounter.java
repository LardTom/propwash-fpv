package de.lardtom.propwash.video.record.path;

public final class LiveTrackCounter {
    private final PathFrames frames = new PathFrames();
    private final TrackThinner thinner;
    private final Simplification tolerance;
    private int segment;
    private int nextTick = Integer.MIN_VALUE;
    private int fovKeys;
    private float fovCurrent;
    private int lastFovTick = Integer.MIN_VALUE;

    public LiveTrackCounter(Simplification tolerance) {
        this.tolerance = tolerance;
        this.thinner = new TrackThinner(this.frames, tolerance);
    }

    public Simplification tolerance() {
        return this.tolerance;
    }

    public void update(CameraPathBuffer buffer) {
        int n = buffer.size();
        if (n < 2) {
            return;
        }
        if (this.nextTick == Integer.MIN_VALUE) {
            this.nextTick = (int) Math.ceil(buffer.firstTick());
            this.segment = 0;
        }
        double last = buffer.lastTick();
        boolean added = false;
        while (this.nextTick <= last) {
            int tick = this.nextTick;
            while (this.segment + 2 < n && buffer.tick(this.segment + 1) < tick) {
                this.segment++;
            }
            PathSample a = buffer.get(this.segment);
            PathSample b = buffer.get(this.segment + 1);
            double span = b.tick() - a.tick();
            double alpha = span > 0.0 ? (tick - a.tick()) / span : 0.0;
            TickPose pose = PathResampler.pose(tick, a, b, Math.max(0.0, Math.min(1.0, alpha)));
            if (!this.frames.add(pose)) {
                break;
            }
            this.countFov(pose);
            this.nextTick++;
            added = true;
        }
        if (added) {
            this.thinner.advance();
        }
    }

    private void countFov(TickPose pose) {
        float value = (float) pose.fov();
        if (this.fovKeys == 0) {
            this.fovKeys = 1;
            this.fovCurrent = value;
            this.lastFovTick = pose.tick();
            return;
        }
        if (Math.abs(value - this.fovCurrent) > CameraTrack.FOV_STEP_DEGREES) {
            this.fovCurrent = value;
            this.fovKeys++;
            this.lastFovTick = pose.tick();
        }
    }

    public int ticks() {
        return this.frames.size();
    }

    public int cameraKeyframes() {
        return this.thinner.keyframes();
    }

    public int fovKeyframes() {
        if (this.fovKeys == 0) {
            return 0;
        }
        return this.fovKeys + (this.frames.size() > 0 && this.lastFovTick != this.frames.lastTick() ? 1 : 0);
    }

    public PathFrames frames() {
        return this.frames;
    }
}
