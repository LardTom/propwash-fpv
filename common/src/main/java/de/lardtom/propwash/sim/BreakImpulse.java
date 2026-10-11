package de.lardtom.propwash.sim;

public final class BreakImpulse {
    public static final double SHARD_COUPLING = 0.15;
    public static final double TUMBLE_SHARE = 0.14;
    public static final double CONTACT_DEPTH = 0.5;
    public static final double LEAD_BAND = 0.02;
    public static final double EDGE_MARGIN = 0.03;
    public static final double IRREGULARITY = 0.12;
    public static final double MAX_RATE_CHANGE = 12.0;
    public static final double STRIKE_LOSS = 0.35;
    public static final double MAX_STRENGTH = 3.0;

    private BreakImpulse() {
    }

    public static double strength(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(MAX_STRENGTH, value);
    }

    public static double residual(double massKg, double approach, double joules) {
        double remaining = approach * approach - 2.0 * joules / massKg;
        return remaining > 0.0 ? Math.sqrt(remaining) : 0.0;
    }

    public static double hitArea(double areaX, double areaY, double areaZ, double nbx, double nby, double nbz, double share) {
        double projected = Math.abs(nbx) * positive(areaX) + Math.abs(nby) * positive(areaY) + Math.abs(nbz) * positive(areaZ);
        return projected * SimMath.clamp(Double.isFinite(share) ? share : 0.0, 0.0, 1.0);
    }

    public static double plugMass(double shardKgPerM2, double areaM2, double strength) {
        return SHARD_COUPLING * strength(strength) * positive(shardKgPerM2) * positive(areaM2);
    }

    public static double afterShards(double massKg, double speed, double plugKg) {
        if (!(massKg > 0.0) || !(speed > 0.0)) {
            return 0.0;
        }
        return speed * massKg / (massKg + positive(plugKg));
    }

    public static double tumbleShare(double strength) {
        return Math.min(1.0, TUMBLE_SHARE * strength(strength));
    }

    public static double strikeLoss(double strength) {
        return STRIKE_LOSS * Math.min(1.0, strength(strength));
    }

    public static double unit(long seed, int stream) {
        long z = seed + 0x9E37_79B9_7F4A_7C15L * (stream + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        z ^= z >>> 31;
        return ((z >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}
