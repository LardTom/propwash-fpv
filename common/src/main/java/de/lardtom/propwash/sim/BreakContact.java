package de.lardtom.propwash.sim;

public record BreakContact(double shardKgPerM2, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double strength,
                           long seed) {
    public static final BreakContact NONE = open(0.0, 0.0, 0L);

    public static BreakContact open(double shardKgPerM2, double strength, long seed) {
        return new BreakContact(shardKgPerM2, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, strength, seed);
    }

    public double min(int axis) {
        return axis == 0 ? this.minX : axis == 1 ? this.minY : this.minZ;
    }

    public double max(int axis) {
        return axis == 0 ? this.maxX : axis == 1 ? this.maxY : this.maxZ;
    }
}
