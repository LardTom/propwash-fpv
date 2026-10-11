package de.lardtom.propwash.drone.trail;

public final class TrailBuffer {
    private final int capacity;
    private final double[] times;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final float[] lives;
    private int start;
    private int size;

    public TrailBuffer(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("Kapazität zu klein: " + capacity);
        }
        this.capacity = capacity;
        this.times = new double[capacity];
        this.xs = new double[capacity];
        this.ys = new double[capacity];
        this.zs = new double[capacity];
        this.lives = new float[capacity];
    }

    public int capacity() {
        return this.capacity;
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public void clear() {
        this.start = 0;
        this.size = 0;
    }

    public void push(double time, double x, double y, double z, float life) {
        int slot;
        if (this.size == this.capacity) {
            slot = this.start;
            this.start = (this.start + 1) % this.capacity;
        } else {
            slot = (this.start + this.size) % this.capacity;
            this.size++;
        }
        this.times[slot] = time;
        this.xs[slot] = x;
        this.ys[slot] = y;
        this.zs[slot] = z;
        this.lives[slot] = life;
    }

    public int expire(double now) {
        int dropped = 0;
        while (this.size > 0) {
            int oldest = this.start;
            if (now - this.times[oldest] < this.lives[oldest]) {
                break;
            }
            this.start = (this.start + 1) % this.capacity;
            this.size--;
            dropped++;
        }
        if (this.size == 0) {
            this.start = 0;
        }
        return dropped;
    }

    private int index(int i) {
        if (i < 0 || i >= this.size) {
            throw new IndexOutOfBoundsException(i + " von " + this.size);
        }
        return (this.start + i) % this.capacity;
    }

    public double time(int i) {
        return this.times[this.index(i)];
    }

    public double x(int i) {
        return this.xs[this.index(i)];
    }

    public double y(int i) {
        return this.ys[this.index(i)];
    }

    public double z(int i) {
        return this.zs[this.index(i)];
    }

    public float life(int i) {
        return this.lives[this.index(i)];
    }

    public int newest() {
        return this.size - 1;
    }
}
