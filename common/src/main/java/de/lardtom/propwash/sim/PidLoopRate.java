package de.lardtom.propwash.sim;

public enum PidLoopRate {
    HZ_2000(1),
    HZ_1000(2),
    HZ_500(4);

    private final int divider;

    PidLoopRate(int divider) {
        this.divider = divider;
    }

    public int divider() {
        return this.divider;
    }

    public double hz() {
        return DroneSim.PHYSICS_HZ / (double) this.divider;
    }
}
