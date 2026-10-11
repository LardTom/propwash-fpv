package de.lardtom.propwash.sim;

public final class EventQueue {
    public static final int CAPACITY = 32;
    private static final SimEventType[] TYPES = SimEventType.values();

    private final int[] types = new int[CAPACITY];
    private final double[] times = new double[CAPACITY];
    private final double[] values = new double[CAPACITY];
    private final int[] motors = new int[CAPACITY];
    private int start;
    private int size;
    private int overflow;

    public EventQueue() {
    }

    public int size() {
        return this.size;
    }

    public SimEventType type(int index) {
        return TYPES[this.types[this.slot(index)]];
    }

    public double time(int index) {
        return this.times[this.slot(index)];
    }

    public double value(int index) {
        return this.values[this.slot(index)];
    }

    public int motor(int index) {
        return this.motors[this.slot(index)];
    }

    public int overflowCount() {
        return this.overflow;
    }

    public int count(SimEventType type) {
        int n = 0;
        for (int k = 0; k < this.size; k++) {
            if (this.type(k) == type) {
                n++;
            }
        }
        return n;
    }

    public boolean contains(SimEventType type) {
        return this.count(type) > 0;
    }

    private int slot(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException(index);
        }
        return (this.start + index) % CAPACITY;
    }

    void clear() {
        this.start = 0;
        this.size = 0;
        this.overflow = 0;
    }

    void add(SimEventType type, double time, double value, int motor) {
        int slot;
        if (this.size < CAPACITY) {
            slot = (this.start + this.size) % CAPACITY;
            this.size++;
        } else {
            slot = this.start;
            this.start = (this.start + 1) % CAPACITY;
            this.overflow++;
        }
        this.types[slot] = type.ordinal();
        this.times[slot] = time;
        this.values[slot] = value;
        this.motors[slot] = motor;
    }
}
