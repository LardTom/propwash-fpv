package de.lardtom.propwash.sim;

public final class ImpactLog {
    public static final int CAPACITY = 16;

    private final float[] speed = new float[CAPACITY];
    private final float[] tangential = new float[CAPACITY];
    private final float[] normalX = new float[CAPACITY];
    private final float[] normalY = new float[CAPACITY];
    private final float[] normalZ = new float[CAPACITY];
    private final int[] contacts = new int[CAPACITY];
    private final int[] struck = new int[CAPACITY];
    private final float[] strikeSpin = new float[CAPACITY];
    private int size;
    private int dropped;

    public ImpactLog() {
    }

    public int size() {
        return this.size;
    }

    public int dropped() {
        return this.dropped;
    }

    public ImpactSample get(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException(index);
        }
        return new ImpactSample(this.speed[index], this.tangential[index], this.normalX[index], this.normalY[index], this.normalZ[index],
                this.contacts[index], this.struck[index], this.strikeSpin[index]);
    }

    public float strongest() {
        float max = 0.0F;
        for (int i = 0; i < this.size; i++) {
            max = Math.max(max, this.speed[i]);
        }
        return max;
    }

    void clear() {
        this.size = 0;
        this.dropped = 0;
    }

    void add(double speed, double tangential, double nx, double ny, double nz, int contacts, int struck, double strikeSpin) {
        int slot = this.size;
        if (slot >= CAPACITY) {
            int weakest = 0;
            for (int i = 1; i < CAPACITY; i++) {
                if (this.speed[i] < this.speed[weakest]) {
                    weakest = i;
                }
            }
            this.dropped++;
            if (this.speed[weakest] >= speed && struck == 0) {
                return;
            }
            slot = weakest;
        } else {
            this.size++;
        }
        this.speed[slot] = (float) speed;
        this.tangential[slot] = (float) tangential;
        this.normalX[slot] = (float) nx;
        this.normalY[slot] = (float) ny;
        this.normalZ[slot] = (float) nz;
        this.contacts[slot] = contacts;
        this.struck[slot] = struck;
        this.strikeSpin[slot] = (float) strikeSpin;
    }
}
