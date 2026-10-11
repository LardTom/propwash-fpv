package de.lardtom.propwash.blackbox;

import java.util.concurrent.atomic.AtomicLong;

public final class BlackboxRing {
    public static final int KIND_MAIN = 1;
    public static final int KIND_RESUME = 2;
    public static final int KIND_START = 3;
    public static final int KIND_STOP = 4;
    static final int CONTROL_RESERVE = 8;

    private final int capacity;
    private final int mask;
    private final int[] data;
    private final int[] kinds;
    private final Object[] payloads;
    private final AtomicLong head = new AtomicLong();
    private final AtomicLong tail = new AtomicLong();

    public BlackboxRing(int capacityPowerOfTwo) {
        int cap = Integer.highestOneBit(Math.max(CONTROL_RESERVE * 2, capacityPowerOfTwo));
        this.capacity = cap;
        this.mask = cap - 1;
        this.data = new int[cap * BlackboxSample.WIDTH];
        this.kinds = new int[cap];
        this.payloads = new Object[cap];
    }

    public int capacity() {
        return this.capacity;
    }

    public int size() {
        return (int) (this.head.get() - this.tail.get());
    }

    public boolean offerSample(int kind, int[] sample) {
        long h = this.head.get();
        if (h - this.tail.get() >= this.capacity - CONTROL_RESERVE) {
            return false;
        }
        int slot = (int) (h & this.mask);
        System.arraycopy(sample, 0, this.data, slot * BlackboxSample.WIDTH, BlackboxSample.WIDTH);
        this.kinds[slot] = kind;
        this.payloads[slot] = null;
        this.head.lazySet(h + 1);
        return true;
    }

    public boolean offerControl(int kind, Object payload) {
        long h = this.head.get();
        if (h - this.tail.get() >= this.capacity) {
            return false;
        }
        int slot = (int) (h & this.mask);
        this.kinds[slot] = kind;
        this.payloads[slot] = payload;
        this.head.lazySet(h + 1);
        return true;
    }

    public boolean isEmpty() {
        return this.head.get() == this.tail.get();
    }

    public int peekKind() {
        return this.kinds[(int) (this.tail.get() & this.mask)];
    }

    public Object peekPayload() {
        return this.payloads[(int) (this.tail.get() & this.mask)];
    }

    public int peekOffset() {
        return (int) (this.tail.get() & this.mask) * BlackboxSample.WIDTH;
    }

    public int[] data() {
        return this.data;
    }

    public void release() {
        long t = this.tail.get();
        this.payloads[(int) (t & this.mask)] = null;
        this.tail.lazySet(t + 1);
    }
}
