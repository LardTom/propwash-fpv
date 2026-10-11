package de.lardtom.propwash.interact;

public final class BreakMath {
    public static final double JOULES_PER_HARDNESS = 400.0;
    public static final double THIN_SHARE = 0.2;
    public static final double MIN_DEPTH = 0.0625;
    public static final double MIN_ENERGY = 5.0;
    public static final double MAX_HARDNESS = 1.0;
    public static final double MIN_SPEED = 3.0;
    public static final double SERVER_SLACK = 0.9;
    public static final double SHARD_KG_PER_M2_PER_JOULE = 10.0 / 36.0;

    private BreakMath() {
    }

    public static boolean fragile(double hardness) {
        return Double.isFinite(hardness) && hardness >= 0.0 && hardness <= MAX_HARDNESS;
    }

    public static double threshold(double hardness, double depth) {
        if (!fragile(hardness)) {
            return Double.POSITIVE_INFINITY;
        }
        double d = Double.isFinite(depth) ? Math.max(MIN_DEPTH, Math.min(1.0, depth)) : 1.0;
        return Math.max(MIN_ENERGY, JOULES_PER_HARDNESS * hardness * (THIN_SHARE + (1.0 - THIN_SHARE) * d));
    }

    public static double energy(double massKg, double closingSpeed) {
        if (!Double.isFinite(massKg) || massKg <= 0.0 || !Double.isFinite(closingSpeed) || closingSpeed <= 0.0) {
            return 0.0;
        }
        return 0.5 * massKg * closingSpeed * closingSpeed;
    }

    public static Impact of(double massKg, double closingSpeed, double threshold) {
        return of(massKg, closingSpeed, threshold, 1.0);
    }

    public static Impact of(double massKg, double closingSpeed, double threshold, double slack) {
        double energy = energy(massKg, closingSpeed);
        boolean breaks = energy > 0.0 && closingSpeed >= MIN_SPEED && Double.isFinite(threshold) && threshold > 0.0
                && energy >= threshold * slack;
        if (!breaks) {
            return new Impact(false, energy, 0.0, closingSpeed > 0.0 && Double.isFinite(closingSpeed) ? closingSpeed : 0.0, 0.0);
        }
        double absorbed = Math.min(energy, threshold);
        double after = Math.sqrt(Math.max(0.0, closingSpeed * closingSpeed - 2.0 * absorbed / massKg));
        return new Impact(true, energy, absorbed, after, closingSpeed - after);
    }

    public static double shardMass(double threshold) {
        if (!Double.isFinite(threshold) || threshold <= 0.0) {
            return 0.0;
        }
        return SHARD_KG_PER_M2_PER_JOULE * threshold;
    }

    public static double breakSpeed(double massKg, double threshold) {
        if (!Double.isFinite(massKg) || massKg <= 0.0 || !Double.isFinite(threshold) || threshold <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(MIN_SPEED, Math.sqrt(2.0 * threshold / massKg));
    }

    public record Impact(boolean breaks, double energy, double absorbed, double speedAfter, double speedLost) {
    }
}
