package de.lardtom.propwash.video.record.path;

import java.util.Arrays;

public final class CameraPathBuffer {
    public static final int MAX_SAMPLES = 1 << 20;
    private static final int INITIAL = 1024;

    private double[] ticks = new double[INITIAL];
    private double[] positions = new double[INITIAL * 3];
    private double[] rotations = new double[INITIAL * 4];
    private double[] fovs = new double[INITIAL];
    private int size;

    public boolean add(PathSample sample) {
        if (!sample.finite()) {
            return false;
        }
        if (this.size > 0 && sample.tick() <= this.ticks[this.size - 1]) {
            return false;
        }
        if (this.size >= MAX_SAMPLES) {
            return false;
        }
        this.grow();
        int i = this.size;
        this.ticks[i] = sample.tick();
        this.positions[i * 3] = sample.x();
        this.positions[i * 3 + 1] = sample.y();
        this.positions[i * 3 + 2] = sample.z();
        double norm = Math.sqrt(sample.qx() * sample.qx() + sample.qy() * sample.qy() + sample.qz() * sample.qz() + sample.qw() * sample.qw());
        double sign = 1.0;
        if (i > 0) {
            double dot = this.rotations[(i - 1) * 4] * sample.qx() + this.rotations[(i - 1) * 4 + 1] * sample.qy()
                    + this.rotations[(i - 1) * 4 + 2] * sample.qz() + this.rotations[(i - 1) * 4 + 3] * sample.qw();
            sign = dot < 0.0 ? -1.0 : 1.0;
        }
        this.rotations[i * 4] = sign * sample.qx() / norm;
        this.rotations[i * 4 + 1] = sign * sample.qy() / norm;
        this.rotations[i * 4 + 2] = sign * sample.qz() / norm;
        this.rotations[i * 4 + 3] = sign * sample.qw() / norm;
        this.fovs[i] = sample.fov();
        this.size++;
        return true;
    }

    private void grow() {
        if (this.size < this.ticks.length) {
            return;
        }
        int capacity = Math.min(MAX_SAMPLES, this.ticks.length * 2);
        this.ticks = Arrays.copyOf(this.ticks, capacity);
        this.positions = Arrays.copyOf(this.positions, capacity * 3);
        this.rotations = Arrays.copyOf(this.rotations, capacity * 4);
        this.fovs = Arrays.copyOf(this.fovs, capacity);
    }

    public void clear() {
        this.size = 0;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public double firstTick() {
        return this.size == 0 ? Double.NaN : this.ticks[0];
    }

    public double lastTick() {
        return this.size == 0 ? Double.NaN : this.ticks[this.size - 1];
    }

    public double spanTicks() {
        return this.size < 2 ? 0.0 : this.ticks[this.size - 1] - this.ticks[0];
    }

    public PathSample get(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException(index);
        }
        return new PathSample(this.ticks[index], this.positions[index * 3], this.positions[index * 3 + 1], this.positions[index * 3 + 2],
                this.rotations[index * 4], this.rotations[index * 4 + 1], this.rotations[index * 4 + 2], this.rotations[index * 4 + 3],
                this.fovs[index]);
    }

    double tick(int index) {
        return this.ticks[index];
    }
}
