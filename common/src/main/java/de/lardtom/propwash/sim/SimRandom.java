package de.lardtom.propwash.sim;

final class SimRandom {
    private static final double SQRT3 = Math.sqrt(3.0);
    private static final double INV_2_53 = 1.0 / (1L << 53);

    private long state;

    SimRandom(long seed) {
        this.setSeed(seed);
    }

    void setSeed(long seed) {
        long s = seed ^ 0x9E3779B97F4A7C15L;
        s = (s ^ (s >>> 30)) * 0xBF58476D1CE4E5B9L;
        s = (s ^ (s >>> 27)) * 0x94D049BB133111EBL;
        s ^= s >>> 31;
        this.state = s == 0L ? 0x2545F4914F6CDD1DL : s;
    }

    long state() {
        return this.state;
    }

    void setState(long state) {
        this.state = state == 0L ? 0x2545F4914F6CDD1DL : state;
    }

    long nextLong() {
        long x = this.state;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        this.state = x;
        return x * 0x2545F4914F6CDD1DL;
    }

    double nextDouble() {
        return (this.nextLong() >>> 11) * INV_2_53;
    }

    double gauss() {
        return (this.nextDouble() + this.nextDouble() + this.nextDouble() + this.nextDouble() - 2.0) * SQRT3;
    }
}
