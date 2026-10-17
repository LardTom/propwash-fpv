package de.lardtom.propwash.video.record.path;

import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PathFrames {
    private final List<TickPose> poses = new ArrayList<>();
    private final List<Quaterniond> rotations = new ArrayList<>();
    private final List<CameraAngles> angles = new ArrayList<>();
    private final List<PathKeyframe> keys = new ArrayList<>();
    private double[] keyErrors = new double[256];

    public static PathFrames of(List<TickPose> poses) {
        PathFrames frames = new PathFrames();
        for (TickPose pose : poses) {
            frames.add(pose);
        }
        return frames;
    }

    public boolean add(TickPose pose) {
        int n = this.poses.size();
        if (n > 0 && pose.tick() != this.poses.get(n - 1).tick() + 1) {
            return false;
        }
        Quaterniond rotation = pose.rotation();
        CameraAngles previous = n == 0 ? null : this.angles.get(n - 1);
        Quaterniond previousTrue = n == 0 ? null : this.rotations.get(n - 1);
        CameraAngles chosen = EulerPath.next(previous, previousTrue, rotation);
        this.poses.add(pose);
        this.rotations.add(rotation);
        this.angles.add(chosen);
        PathKeyframe key = new PathKeyframe(pose.tick(), pose.x(), pose.y(), pose.z(), (float) chosen.yaw(), (float) chosen.pitch(),
                (float) chosen.roll());
        this.keys.add(key);
        if (n == this.keyErrors.length) {
            this.keyErrors = Arrays.copyOf(this.keyErrors, n * 2);
        }
        this.keyErrors[n] = CameraAngles.degreesBetween(key.angles().rotation(), rotation);
        return true;
    }

    public int size() {
        return this.poses.size();
    }

    public boolean isEmpty() {
        return this.poses.isEmpty();
    }

    public TickPose pose(int index) {
        return this.poses.get(index);
    }

    public Quaterniond rotation(int index) {
        return this.rotations.get(index);
    }

    public CameraAngles angles(int index) {
        return this.angles.get(index);
    }

    public PathKeyframe keyframe(int index) {
        return this.keys.get(index);
    }

    public double keyframeAngleError(int index) {
        return this.keyErrors[index];
    }

    public int tick(int index) {
        return this.poses.get(index).tick();
    }

    public int firstTick() {
        return this.poses.getFirst().tick();
    }

    public int lastTick() {
        return this.poses.getLast().tick();
    }

    public List<TickPose> poses() {
        return this.poses;
    }
}
